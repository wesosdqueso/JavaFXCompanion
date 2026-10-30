package org.javafxcompanion;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

/** Sort Java source and resources as files, rather than separate element kinds. */
public final class JavaFxNameSorter extends ViewerComparator {
    private static String fileName(Object element) {
        if (element instanceof ICompilationUnit unit) return unit.getElementName();
        if (element instanceof IFile file) return file.getName();
        return null;
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @Override public int category(Object element) {
        if (element instanceof IPackageFragment || element instanceof IContainer) return 0;
        return fileName(element) != null ? 1 : 2;
    }

    @Override public int compare(Viewer viewer, Object left, Object right) {
        int group = Integer.compare(category(left), category(right));
        if (group != 0) return group;
        String a = fileName(left), b = fileName(right);
        if (a == null || b == null) return super.compare(viewer, left, right);
        int name = stem(a).compareToIgnoreCase(stem(b));
        if (name != 0) return name;
        // Keep the Java class first when the base names match.
        int javaFirst = Boolean.compare(!a.endsWith(".java"), !b.endsWith(".java"));
        if (javaFirst != 0) return javaFirst;
        int insensitive = a.compareToIgnoreCase(b);
        return insensitive != 0 ? insensitive : a.compareTo(b);
    }
}
