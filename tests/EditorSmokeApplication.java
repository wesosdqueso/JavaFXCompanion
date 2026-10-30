package org.javafxcompanion.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.*;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

public final class EditorSmokeApplication implements IApplication {
    private Throwable failure;
    @Override public Object start(IApplicationContext context) {
        Display display = PlatformUI.createDisplay();
        try {
            PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
                @Override public String getInitialWindowPerspectiveId() { return "org.eclipse.ui.resourcePerspective"; }
                @Override public void postStartup() {
                    display.asyncExec(() -> {
                        try { exercise(); }
                        catch (Throwable e) { failure = e; e.printStackTrace(); }
                        finally {
                            var window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                            if (window != null && window.getActivePage() != null) window.getActivePage().closeAllEditors(false);
                            PlatformUI.getWorkbench().close();
                        }
                    });
                }
            });
        } finally { display.dispose(); }
        if (failure != null) throw new AssertionError("Embedded editor smoke test", failure);
        return EXIT_OK;
    }
    private void exercise() throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("editor-smoke-" + System.currentTimeMillis());
        if (!project.exists()) project.create(null);
        project.open(null);
        IFile file = project.getFile("Screen.fxml");
        String original = "<?import javafx.scene.layout.VBox?>\n<?import javafx.scene.control.Button?>\n<VBox xmlns:fx=\"http://javafx.com/fxml/1\"><children><Button text=\"Original\"/></children></VBox>";
        var bytes = new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8));
        if (file.exists()) file.setContents(bytes, true, false, null); else file.create(bytes, true, null);
        IEditorPart editor = IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), file, "org.javafxcompanion.fxmlEditor", true);
        var sourceField = editor.getClass().getDeclaredField("source"); sourceField.setAccessible(true);
        ITextEditor source = (ITextEditor) sourceField.get(editor);
        var doc = source.getDocumentProvider().getDocument(source.getEditorInput());
        doc.set(original.replace("Original", "Changed in source"));
        var switchPage = MultiPageEditorPart.class.getDeclaredMethod("setActivePage", int.class); switchPage.setAccessible(true);
        switchPage.invoke(editor, 1);
        var valid = editor.getClass().getDeclaredField("designValid"); valid.setAccessible(true);
        if (!(boolean) valid.get(editor)) throw new AssertionError("Design page failed to load");
        var designField = editor.getClass().getDeclaredField("design"); designField.setAccessible(true);
        Object design = designField.get(editor);
        String designXml = (String) design.getClass().getMethod("getFxml").invoke(design);
        if (!designXml.contains("Changed in source")) throw new AssertionError("Source to design synchronization");
        var controllerField = design.getClass().getDeclaredField("controller"); controllerField.setAccessible(true);
        Object controller = controllerField.get(design);
        Object fxDocument = controller.getClass().getMethod("getFxomDocument").invoke(controller);
        Object root = fxDocument.getClass().getMethod("getFxomRoot").invoke(fxDocument);
        Object button = ((java.util.List<?>) root.getClass().getMethod("getChildObjects").invoke(root)).get(0);
        var bundle = Platform.getBundle("org.javafxcompanion");
        Class<?> jobType = bundle.loadClass("com.oracle.javafx.scenebuilder.kit.editor.job.atomic.ModifyFxIdJob");
        Object job = jobType.getConstructor(bundle.loadClass("com.oracle.javafx.scenebuilder.kit.fxom.FXOMObject"), String.class, controller.getClass()).newInstance(button, "editedInDesign", controller);
        Object manager = controller.getClass().getMethod("getJobManager").invoke(controller);
        manager.getClass().getMethod("push", bundle.loadClass("com.oracle.javafx.scenebuilder.kit.editor.job.Job")).invoke(manager, job);
        var visualDirty = editor.getClass().getDeclaredField("designChanged"); visualDirty.setAccessible(true);
        if (!(boolean) visualDirty.get(editor)) throw new AssertionError("Visual change callback was not received");
        if (!editor.isDirty()) throw new AssertionError("Visual edit did not mark editor dirty");
        editor.doSave(null);
        try (var input = file.getContents()) {
            if (!new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("editedInDesign")) throw new AssertionError("Visual edit save failed");
        }
        if (!doc.get().contains("editedInDesign")) throw new AssertionError("Design to source synchronization");
        Display display = Display.getCurrent();
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) if (!display.readAndDispatch()) Thread.sleep(10);
        if (editor.isDirty()) throw new AssertionError("Saved editor became dirty after queued visual callback");
        var shell = editor.getSite().getShell();
        shell.setSize(1400, 950);
        shell.layout(true, true);
        display.update();
        var snapshot = new org.eclipse.swt.graphics.Image(display, shell.getClientArea().width, shell.getClientArea().height);
        var gc = new org.eclipse.swt.graphics.GC(shell); gc.copyArea(snapshot, 0, 0); gc.dispose();
        var imageLoader = new org.eclipse.swt.graphics.ImageLoader();
        imageLoader.data = new org.eclipse.swt.graphics.ImageData[] { snapshot.getImageData() };
        imageLoader.save("D:/eclipse/repos/JavaFXCompanion/build/eclipse-editor-smoke.png", org.eclipse.swt.SWT.IMAGE_PNG);
        snapshot.dispose();
        switchPage.invoke(editor, 0);
        doc.set("<Invalid FXML");
        switchPage.invoke(editor, 1);
        if ((boolean) valid.get(editor)) throw new AssertionError("Invalid XML must not expose stale designer");
        if (!doc.get().equals("<Invalid FXML")) throw new AssertionError("Invalid source was overwritten");
        switchPage.invoke(editor, 0);
        doc.set(original);
        editor.doSave(null);
        ResourcesPlugin.getWorkspace().save(true, null);
        System.out.println("PASS: Eclipse editor opens, source/design synchronize, saves, and preserves invalid source");
    }
    @Override public void stop() {}
}
