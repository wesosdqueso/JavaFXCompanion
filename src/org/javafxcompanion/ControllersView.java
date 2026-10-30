package org.javafxcompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.*;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

/** A virtual view: resources keep their physical paths and Maven layout. */
public final class ControllersView extends ViewPart {
    private TreeViewer viewer;
    private Job refreshJob;
    private final IResourceChangeListener listener = event -> refresh();

    private record Node(String label, IFile file, List<Node> children) {}

    @Override public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        viewer.setContentProvider(new ITreeContentProvider() {
            public Object[] getElements(Object input) { return ((List<?>) input).toArray(); }
            public Object[] getChildren(Object element) { return ((Node) element).children().toArray(); }
            public Object getParent(Object element) { return null; }
            public boolean hasChildren(Object element) { return !((Node) element).children().isEmpty(); }
        });
        viewer.setLabelProvider(new LabelProvider() {
            @Override public String getText(Object element) { return ((Node) element).label(); }
        });
        viewer.addDoubleClickListener(event -> {
            Node node = (Node) viewer.getStructuredSelection().getFirstElement();
            if (node == null) return;
            if (node.file() == null) {
                viewer.setExpandedState(node, !viewer.getExpandedState(node));
                return;
            }
            try {
                IDE.openEditor(getSite().getPage(), node.file(), true);
            } catch (org.eclipse.ui.PartInitException e) {
                Platform.getLog(ControllersView.class).log(e.getStatus());
            }
        });
        getSite().setSelectionProvider(viewer);
        getViewSite().getActionBars().getToolBarManager().add(new Action("Refresh") {
            @Override public void run() { refresh(); }
        });
        setContentDescription("FXML and Java controllers together · double-click to open");
        ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
        refresh();
    }

    private synchronized void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) return;
        if (refreshJob != null) refreshJob.cancel();
        var display = viewer.getControl().getDisplay();
        refreshJob = new Job("Find JavaFX controllers") {
            @Override protected IStatus run(IProgressMonitor monitor) {
                List<Node> result = scan(monitor);
                if (!monitor.isCanceled() && !display.isDisposed()) display.asyncExec(() -> {
                    if (!monitor.isCanceled() && !viewer.getControl().isDisposed()) {
                        viewer.setInput(result);
                        viewer.expandToLevel(2);
                    }
                });
                return Status.OK_STATUS;
            }
        };
        refreshJob.setSystem(true);
        refreshJob.schedule(300);
    }

    private List<Node> scan(IProgressMonitor monitor) {
        List<Node> projects = new ArrayList<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (monitor.isCanceled()) break;
            if (!project.isOpen()) continue;
            Map<String, Map<IFile, List<Node>>> packages = new java.util.TreeMap<>();
            List<Node> unmatched = new ArrayList<>();
            IJavaProject javaProject = JavaCore.create(project);
            try {
                project.accept(resource -> {
                    if (monitor.isCanceled()) return false;
                    if (resource.isDerived() || resource.getName().startsWith(".")) return false;
                    if (!(resource instanceof IFile file) || !"fxml".equalsIgnoreCase(file.getFileExtension())) return true;
                    String controller = readController(file);
                    IType type = controller == null || !javaProject.exists() ? null : javaProject.findType(controller);
                    ICompilationUnit unit = type == null ? null : type.getCompilationUnit();
                    if (unit != null && unit.getResource() instanceof IFile javaFile) {
                        String pkg = type.getPackageFragment().getElementName();
                        packages.computeIfAbsent(pkg, key -> new LinkedHashMap<>())
                            .computeIfAbsent(javaFile, key -> new ArrayList<>())
                            .add(new Node(file.getName() + " — " + file.getProjectRelativePath(), file, List.of()));
                    } else {
                        unmatched.add(new Node(file.getProjectRelativePath().toString(), file, List.of()));
                    }
                    return false;
                });
            } catch (CoreException e) {
                Platform.getLog(ControllersView.class).log(e.getStatus());
            }
            List<Node> children = new ArrayList<>();
            packages.forEach((pkg, controllers) -> {
                List<Node> files = new ArrayList<>();
                controllers.forEach((javaFile, fxml) -> {
                    files.add(new Node(javaFile.getName(), javaFile, List.of()));
                    files.addAll(fxml);
                });
                children.add(new Node(pkg.isEmpty() ? "(default package)" : pkg, null, files));
            });
            if (!unmatched.isEmpty()) children.add(new Node("FXML without resolved controller", null, unmatched));
            if (!children.isEmpty()) projects.add(new Node(project.getName(), null, children));
        }
        return projects;
    }

    static String readController(IFile file) {
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
            return value.isBlank() ? null : value.trim();
        } catch (Exception e) {
            // Incomplete FXML remains accessible in the unresolved group while editing.
            return null;
        }
    }

    @Override public void setFocus() { viewer.getControl().setFocus(); }
    @Override public synchronized void dispose() {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
        if (refreshJob != null) refreshJob.cancel();
        super.dispose();
    }
}
