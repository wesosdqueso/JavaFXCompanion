package org.javafxcompanion;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import com.oracle.javafx.scenebuilder.kit.library.BuiltinLibrary;
import com.oracle.javafx.scenebuilder.kit.library.Library;

/** Project classes share the designer's JavaFX runtime, rather than a new module layer. */
final class ProjectDesignLibrary extends Library implements AutoCloseable {
    private final URLClassLoader loader;

    ProjectDesignLibrary(IProject project) throws CoreException, IOException {
        Set<URL> urls = new LinkedHashSet<>();
        collect(project, urls, new java.util.HashSet<>());
        loader = new URLClassLoader(urls.toArray(URL[]::new), SceneBuilderPane.class.getClassLoader());
        getItems().setAll(BuiltinLibrary.getLibrary().getItems());
        setClassLoader(loader);
    }

    private static void collect(IProject project, Set<URL> urls, Set<IProject> visited)
            throws CoreException, IOException {
        if (!project.isOpen() || !visited.add(project) || !project.hasNature(JavaCore.NATURE_ID)) return;
        var java = JavaCore.create(project);
        add(java.getOutputLocation(), urls);
        for (var entry : java.getResolvedClasspath(true)) {
            switch (entry.getEntryKind()) {
                case IClasspathEntry.CPE_SOURCE -> {
                    if (entry.getOutputLocation() != null) add(entry.getOutputLocation(), urls);
                    // Resources may not have been copied to the output directory yet.
                    add(entry.getPath(), urls);
                }
                case IClasspathEntry.CPE_LIBRARY -> add(entry.getPath(), urls);
                case IClasspathEntry.CPE_PROJECT -> collect(ResourcesPlugin.getWorkspace().getRoot()
                        .getProject(entry.getPath().segment(0)), urls, visited);
                default -> { }
            }
        }
    }

    private static void add(IPath path, Set<URL> urls) throws IOException {
        var resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
        var local = resource == null ? path : resource.getLocation();
        if (local != null && local.toFile().exists()) urls.add(local.toFile().toURI().toURL());
    }

    @Override public Comparator<String> getSectionComparator() {
        return BuiltinLibrary.getLibrary().getSectionComparator();
    }

    @Override public void close() throws IOException { loader.close(); }
}
