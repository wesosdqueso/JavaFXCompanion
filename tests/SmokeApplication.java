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
        javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(src.getFullPath()), JavaCore.newSourceEntry(resources.getFullPath()) }, project.getFullPath().append("bin"), null);
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
        IFolder resourcePackage = folder(resources, "demo");
        IFile custom = file(resourcePackage, "Custom.fxml", "<fx:root type=\"Pane\" xmlns:fx=\"http://javafx.com/fxml/1\"/>");
        IFile plain = file(resourcePackage, "Plain.fxml", "<Pane/>");
        IFolder otherPackage = folder(src, "other");
        file(otherPackage, "Other.java", "package other; public class Other {}");
        IFile explicit = file(resourcePackage, "Explicit.fxml", "<Pane xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"other.Other\"/>");
        IFile unresolved = file(resourcePackage, "Unresolved.fxml", "<Pane xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"missing.Controller\"/>");
        var provider = (IPipelinedTreeContentProvider2) element.createExecutableExtension("contentProvider");
        var labels = (org.eclipse.ui.navigator.ICommonLabelProvider) element.createExecutableExtension("labelProvider");
        try {
            check(labels.getDescription(fxml).contains("resuelto"), "Resolved controller description");
            check(labels.getDescription(custom).contains("coincidencia de paquete"), "Custom control package description");
            check(labels.getDescription(plain).contains("sin controlador declarado"), "Controller-free FXML description");
            check(labels.getDescription(unresolved).contains("no se encuentra"), "Unresolved controller warning description");
            check(labels.getText(custom).equals("Custom.fxml"), "Decoration preserves filename");
            var display = new org.eclipse.swt.widgets.Display();
            try {
                var resolvedIcon = labels.getImage(fxml);
                var folderIcon = labels.getImage(custom);
                var warningIcon = labels.getImage(unresolved);
                check(resolvedIcon != null && folderIcon != resolvedIcon && warningIcon != resolvedIcon
                        && warningIcon != folderIcon, "Distinct controller, package and warning icons");
                var preview = new org.eclipse.swt.graphics.Image(display, 240, 80);
                var gc = new org.eclipse.swt.graphics.GC(preview);
                try {
                    gc.setBackground(display.getSystemColor(org.eclipse.swt.SWT.COLOR_WHITE));
                    gc.fillRectangle(preview.getBounds());
                    var icons = new org.eclipse.swt.graphics.Image[] {resolvedIcon, folderIcon, warningIcon};
                    for (int i = 0; i < icons.length; i++) {
                        var bounds = icons[i].getBounds();
                        gc.drawImage(icons[i], 0, 0, bounds.width, bounds.height, i * 80 + 8, 8, 64, 64);
                    }
                    var loader = new org.eclipse.swt.graphics.ImageLoader();
                    loader.data = new org.eclipse.swt.graphics.ImageData[] {preview.getImageData()};
                    loader.save("D:/eclipse/repos/JavaFXCompanion/build/association-icons.png", org.eclipse.swt.SWT.IMAGE_PNG);
                } finally { gc.dispose(); preview.dispose(); }
            } finally {
                labels.dispose();
                display.dispose();
            }
            IFile incomplete = file(resourcePackage, "Incomplete.fxml", "<Pane");
            check(!labels.getDescription(incomplete).contains("sin controlador declarado"), "Malformed XML is not a controller-free declaration");
            file(resourcePackage, "Incomplete.fxml", "<Pane xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"demo.Controller\"/>");
            check(labels.getDescription(incomplete).contains("resuelto"), "Description updates after fixing XML");
            incomplete.delete(true, null);
            Set<Object> children = new HashSet<>();
            children.add(javaProject.findType("demo.Controller").getCompilationUnit());
            provider.getPipelinedChildren(pkg, children);
            check(children.contains(fxml) && children.size() == 5, "FXML added beside Java controller");
            check(children.contains(custom) && children.contains(plain) && children.contains(unresolved), "Resource paths match Java packages without a resolvable controller");
            check(pkg.equals(provider.getPipelinedParent(custom, resourcePackage)), "Custom control virtual parent");
            IPackageFragment other = javaProject.getPackageFragmentRoot(src).getPackageFragment("other");
            check(other.equals(provider.getPipelinedParent(explicit, resourcePackage)), "Explicit controller takes precedence");
            check(!children.contains(explicit), "No duplicate association for explicit controller");
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
            check(!refreshed.contains(fxml) && refreshed.contains(custom) && refreshed.contains(plain), "Association invalidated while structural matches remain");
            System.out.println("PASS: Project Explorer association smoke checks");
        } finally { labels.dispose(); provider.dispose(); }
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
