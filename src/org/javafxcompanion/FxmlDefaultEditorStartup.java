package org.javafxcompanion;

import java.util.ArrayList;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

/** Migrate old per-file editor choices once, without touching any p2 profile. */
public final class FxmlDefaultEditorStartup implements IStartup {
    @Override public void earlyStartup() {
        var prefs = InstanceScope.INSTANCE.getNode("org.javafxcompanion");
        if (prefs.getBoolean("dualEditorDefaultMigration033", false)) return;
        var files = new ArrayList<IFile>();
        try {
            for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                if (!project.isOpen()) continue;
                project.accept(resource -> {
                    if (resource.isDerived() || resource.getName().startsWith(".")) return false;
                    if (resource instanceof IFile file && "fxml".equalsIgnoreCase(file.getFileExtension())) files.add(file);
                    return true;
                });
            }
        } catch (CoreException e) { Platform.getLog(getClass()).log(e.getStatus()); return; }
        var workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().asyncExec(() -> {
            if (workbench.isClosing()) return;
            try {
                String editorId = "org.javafxcompanion.fxmlEditor";
                workbench.getEditorRegistry().setDefaultEditor("*.fxml", editorId);
                for (IFile file : files) if (file.exists()) IDE.setDefaultEditor(file, editorId);
                prefs.putBoolean("dualEditorDefaultMigration033", true);
                prefs.flush();
            } catch (Exception e) {
                Platform.getLog(getClass()).log(new Status(IStatus.ERROR, "org.javafxcompanion", "Cannot set FXML default editor", e));
            }
        });
    }
}
