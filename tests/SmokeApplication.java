package org.javafxcompanion.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.app.*;
import org.eclipse.jdt.core.*;
import org.eclipse.ui.navigator.IPipelinedTreeContentProvider2;

public final class SmokeApplication implements IApplication {
    @Override public Object start(IApplicationContext context) throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("javafx-companion-smoke");
        if (!project.exists()) project.create(null);
        project.open(null);
        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);
        IFolder src = folder(project, "src");
        IFolder pkgFolder = folder(src, "demo");
        IFile controller = file(pkgFolder, "Controller.java", "package demo; public class Controller {}");
        IFolder resources = folder(project, "resources");
        IFile fxml = file(resources, "Screen.fxml", "<Pane xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"demo.Controller\"/>");
        IFile unmatched = file(resources, "Unmatched.fxml", "<Pane/>");
        IJavaProject javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(src.getFullPath()) }, project.getFullPath().append("bin"), null);
        IPackageFragment pkg = javaProject.getPackageFragmentRoot(src).getPackageFragment("demo");
        check(javaProject.findType("demo.Controller") != null, "JDT controller lookup");
        IConfigurationElement element = null;
        for (var candidate : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.navigator.navigatorContent")) {
            if ("org.javafxcompanion.fxmlContent".equals(candidate.getAttribute("id"))) element = candidate;
        }
        check(element != null, "Navigator contribution registered");
        check("org.eclipse.jdt.java.ui.javaContent".equals(element.getChildren("override")[0].getAttribute("suppressedExtensionId")), "JDT pipeline binding");
        var sorter = (org.eclipse.jface.viewers.ViewerComparator) element.getChildren("commonSorter")[0].createExecutableExtension("class");
        IFile sameName = file(resources, "Controller.fxml", "<Pane/>");
        IFile launcher = file(pkgFolder, "Launcher.java", "package demo; public class Launcher {}");
        IFile app = file(pkgFolder, "App.java", "package demo; public class App {}");
        Object controllerUnit = javaProject.findType("demo.Controller").getCompilationUnit();
        Object launcherUnit = javaProject.findType("demo.Launcher").getCompilationUnit();
        Object appUnit = javaProject.findType("demo.App").getCompilationUnit();
        var sorted = new java.util.ArrayList<Object>(java.util.List.of(launcherUnit, sameName, controllerUnit, appUnit));
        sorted.sort((a, b) -> sorter.compare(null, a, b));
        check(sorted.equals(java.util.List.of(appUnit, controllerUnit, sameName, launcherUnit)), "Mixed Java/FXML filename order");
        var provider = (IPipelinedTreeContentProvider2) element.createExecutableExtension("contentProvider");
        try {
            Set<Object> children = new HashSet<>();
            children.add(javaProject.findType("demo.Controller").getCompilationUnit());
            provider.getPipelinedChildren(pkg, children);
            check(children.contains(fxml) && children.size() == 2, "FXML added beside Java controller");
            Set<Object> physical = new HashSet<>();
            physical.add(fxml); physical.add(unmatched);
            provider.getPipelinedChildren(resources, physical);
            check(physical.contains(fxml) && physical.contains(unmatched), "FXML remains visible under physical parent");
            check(pkg.equals(provider.getPipelinedParent(fxml, resources)), "Virtual parent for editor linking");
            check(resources.equals(provider.getPipelinedParent(unmatched, resources)), "Unmatched FXML retains parent");
            check(fxml.exists() && controller.exists(), "Physical files retained");
            file(resources, "Screen.fxml", "<Pane/>");
            Set<Object> refreshed = new HashSet<>();
            provider.getPipelinedChildren(pkg, refreshed);
            check(refreshed.isEmpty(), "Association invalidated on FXML change");
            System.out.println("PASS: Project Explorer association smoke checks");
        } finally { provider.dispose(); }
        return EXIT_OK;
    }
    private static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); }
    private static IFolder folder(IContainer parent, String name) throws CoreException {
        IFolder folder = parent.getFolder(new Path(name));
        if (!folder.exists()) folder.create(true, true, null);
        return folder;
    }
    private static IFile file(IContainer parent, String name, String text) throws CoreException {
        IFile file = parent.getFile(new Path(name));
        var input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        if (file.exists()) file.setContents(input, true, false, null); else file.create(input, true, null);
        return file;
    }
    @Override public void stop() {}
}
