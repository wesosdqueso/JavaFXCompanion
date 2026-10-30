package org.javafxcompanion;

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
                    String controller = ControllersView.readController(file);
                    IType type = controller == null ? null : javaProject.findType(controller);
                    if (type != null && type.getCompilationUnit() != null
                            && project.equals(type.getJavaProject().getProject())) {
                        IPackageFragment pkg = type.getPackageFragment();
                        // A file already in its controller's package needs no reshaping.
                        if (!file.getParent().equals(pkg.getResource())) result.put(file, pkg);
                    }
                    return false;
                }
                return true;
            });
        } catch (CoreException e) {
            org.eclipse.core.runtime.Platform.getLog(getClass()).log(e.getStatus());
        }
        return result;
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
