package org.javafxcompanion;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.*;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.*;

/** Reshapes JDT's existing Project Explorer content using public CNF APIs. */
public final class FxmlNavigatorContentProvider implements IPipelinedTreeContentProvider2 {
    private final Map<IProject, Map<IFile, IPackageFragment>> cache = new HashMap<>();
    private Viewer viewer;
    private Display display;
    private boolean disposed;
    private final Runnable refreshViewer = () -> {
        if (!disposed && viewer != null && !viewer.getControl().isDisposed()) viewer.refresh();
    };
    private final IResourceChangeListener listener = event -> {
        synchronized (cache) { cache.clear(); }
        Display ui = display;
        if (ui != null && !ui.isDisposed()) ui.asyncExec(() -> {
            if (!disposed) {
                ui.timerExec(-1, refreshViewer);
                ui.timerExec(250, refreshViewer);
            }
        });
    };

    public FxmlNavigatorContentProvider() {
        ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
    }

    private Map<IFile, IPackageFragment> associations(IProject project) {
        synchronized (cache) {
            return cache.computeIfAbsent(project, this::scan);
        }
    }

    private Map<IFile, IPackageFragment> scan(IProject project) {
        Map<IFile, IPackageFragment> result = new HashMap<>();
        IJavaProject javaProject = JavaCore.create(project);
        if (!project.isOpen() || !javaProject.exists()) return result;
        try {
            project.accept(resource -> {
                if (resource.isDerived() || resource.getName().startsWith(".")) return false;
                if (resource instanceof IFile file && "fxml".equalsIgnoreCase(file.getFileExtension())) {
                    String controller = readController(file);
                    IType type = controller == null ? null : javaProject.findType(controller);
                    IPackageFragment pkg = null;
                    if (type != null && type.getCompilationUnit() != null
                            && project.equals(type.getJavaProject().getProject())) {
                        pkg = type.getPackageFragment();
                    }
                    if (pkg == null) pkg = matchingPackage(javaProject, file);
                    // Keep physical resources and add a virtual entry in the Java package.
                    if (pkg != null && !file.getParent().equals(pkg.getResource())) result.put(file, pkg);
                    return false;
                }
                return true;
            });
        } catch (CoreException e) {
            org.eclipse.core.runtime.Platform.getLog(getClass()).log(e.getStatus());
        }
        return result;
    }

    /** Match source-relative resource paths, independent of the FXML controller. */
    static IPackageFragment matchingPackage(IJavaProject project, IFile file) throws JavaModelException {
        IPackageFragmentRoot resourceRoot = null;
        for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
            if (root.getKind() == IPackageFragmentRoot.K_SOURCE
                    && project.equals(root.getJavaProject())
                    && root.getPath().isPrefixOf(file.getFullPath())
                    && (resourceRoot == null || root.getPath().segmentCount() > resourceRoot.getPath().segmentCount())) {
                resourceRoot = root;
            }
        }
        if (resourceRoot == null) return null;
        String packageName = file.getParent().getFullPath().makeRelativeTo(resourceRoot.getPath())
                .toString().replace('/', '.');
        for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
            if (root.getKind() != IPackageFragmentRoot.K_SOURCE || root.equals(resourceRoot)
                    || !project.equals(root.getJavaProject())) continue;
            IPackageFragment pkg = root.getPackageFragment(packageName);
            if (pkg.exists() && pkg.containsJavaResources()) return pkg;
        }
        return null;
    }

    private static String readController(IFile file) {
        String declaration = readControllerDeclaration(file);
        return declaration == null || declaration.isBlank() ? null : declaration;
    }

    /** Null means unreadable XML; empty means valid XML without a controller. */
    static String readControllerDeclaration(IFile file) {
        try (var input = file.getContents()) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var root = builder.parse(input).getDocumentElement();
            String value = root.getAttributeNS("http://javafx.com/fxml/1", "controller");
            if (value.isBlank()) value = root.getAttributeNS("http://javafx.com/fxml", "controller");
            return value.trim();
        } catch (Exception e) {
            // Incomplete FXML has no controller association while editing.
            return null;
        }
    }

    private static IProject project(Object element) {
        if (element instanceof IJavaElement java) return java.getJavaProject() == null ? null : java.getJavaProject().getProject();
        if (element instanceof IResource resource) return resource.getProject();
        return null;
    }

    @Override @SuppressWarnings({"rawtypes", "unchecked"})
    public void getPipelinedChildren(Object parent, Set children) {
        IProject project = project(parent);
        if (project == null) return;
        Map<IFile, IPackageFragment> links = associations(project);
        if (parent instanceof IPackageFragment pkg) links.forEach((file, target) -> {
            if (pkg.equals(target)) children.add(file);
        });
    }
    @Override @SuppressWarnings("rawtypes")
    public void getPipelinedElements(Object input, Set elements) { getPipelinedChildren(input, elements); }
    @Override public Object getPipelinedParent(Object element, Object suggested) {
        if (element instanceof IFile file) {
            IPackageFragment target = associations(file.getProject()).get(file);
            return target == null ? suggested : target;
        }
        return suggested;
    }
    @Override public Object getParent(Object element) { return getPipelinedParent(element, null); }
    @Override public Object[] getChildren(Object parent) { return new Object[0]; }
    @Override public Object[] getElements(Object input) { return new Object[0]; }
    @Override public boolean hasChildren(Object parent) { return false; }
    @Override public boolean hasPipelinedChildren(Object parent, boolean current) {
        if (current) return true;
        return parent instanceof IPackageFragment pkg && associations(pkg.getJavaProject().getProject()).containsValue(pkg);
    }
    @Override public PipelinedShapeModification interceptAdd(PipelinedShapeModification modification) {
        // Preserve additions under resources; virtual copies are enumerated separately.
        return modification;
    }
    @Override public PipelinedShapeModification interceptRemove(PipelinedShapeModification modification) { return modification; }
    @Override public boolean interceptRefresh(PipelinedViewerUpdate update) { return false; }
    @Override public boolean interceptUpdate(PipelinedViewerUpdate update) { return false; }
    @Override public void init(ICommonContentExtensionSite site) {}
    @Override public void restoreState(IMemento memento) {}
    @Override public void saveState(IMemento memento) {}
    @Override public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        this.viewer = viewer;
        this.display = viewer.getControl().getDisplay();
        synchronized (cache) { cache.clear(); }
    }
    @Override public void dispose() {
        disposed = true;
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
        if (display != null && !display.isDisposed()) display.asyncExec(() -> display.timerExec(-1, refreshViewer));
        synchronized (cache) { cache.clear(); }
    }
}
