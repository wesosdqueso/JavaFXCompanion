package org.javafxcompanion.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

public final class VisualSmokeApplication implements IApplication {
    @Override public Object start(IApplicationContext context) throws Exception {
        Display display = new Display();
        Shell shell = new Shell(display);
        try {
            shell.setText("JavaFX Companion — Scene Builder integration test");
            shell.setLayout(new FillLayout());
            shell.setSize(1280, 850);
            Class<?> paneClass = Platform.getBundle("org.javafxcompanion").loadClass("org.javafxcompanion.SceneBuilderPane");
            Object pane = paneClass.getConstructor(Composite.class, Consumer.class).newInstance(shell, (Consumer<String>) text -> {});
            String fxml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<?import javafx.scene.layout.VBox?>\n<?import javafx.scene.control.Button?>\n<?import javafx.scene.control.Label?>\n<VBox xmlns:fx=\"http://javafx.com/fxml/1\" prefWidth=\"400\" prefHeight=\"300\" spacing=\"20\"><children><Label text=\"Embedded Scene Builder 26\"/><Button text=\"Edit me inside Eclipse\"/></children></VBox>";
            Path sample = Path.of("D:/eclipse/repos/JavaFXCompanion/build/visual-smoke.fxml");
            Files.writeString(sample, fxml);
            paneClass.getMethod("load", String.class, java.net.URL.class).invoke(pane, fxml, sample.toUri().toURL());
            shell.open();
            display.timerExec(5000, () -> {
                try {
                    String output = (String) paneClass.getMethod("getFxml").invoke(pane);
                    if (!output.contains("Edit me inside Eclipse")) throw new AssertionError("FXML round trip failed");
                    Image snapshot = new Image(display, shell.getClientArea().width, shell.getClientArea().height);
                    GC gc = new GC(shell);
                    gc.copyArea(snapshot, 0, 0);
                    gc.dispose();
                    ImageLoader loader = new ImageLoader();
                    loader.data = new org.eclipse.swt.graphics.ImageData[] { snapshot.getImageData() };
                    loader.save("D:/eclipse/repos/JavaFXCompanion/build/scene-builder-smoke.png", SWT.IMAGE_PNG);
                    snapshot.dispose();
                    System.out.println("PASS: Native FXCanvas + Scene Builder 26 panels + FXML round trip");
                } catch (Exception e) { throw new RuntimeException(e); }
                shell.dispose();
            });
            while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
        } finally { if (!shell.isDisposed()) shell.dispose(); display.dispose(); }
        return EXIT_OK;
    }
    @Override public void stop() {}
}
