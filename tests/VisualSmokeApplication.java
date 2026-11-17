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
            // A project-only control loads a nested FXML using FXMLLoader's default loader.
            var project = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot()
                    .getProject("custom-control-" + System.currentTimeMillis());
            project.create(null);
            project.open(null);
            var description = project.getDescription();
            description.setNatureIds(new String[] { org.eclipse.jdt.core.JavaCore.NATURE_ID });
            project.setDescription(description, null);
            var javaProject = org.eclipse.jdt.core.JavaCore.create(project);
            javaProject.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[0],
                    project.getFullPath().append("bin"), null);
            Path outputDir = project.getLocation().toFile().toPath().resolve("bin");
            Files.createDirectories(outputDir.resolve("custom"));
            Path control = project.getLocation().toFile().toPath().resolve("CustomPane.java");
            Files.writeString(control, """
                    package custom;
                    public class CustomPane extends javafx.scene.layout.VBox {
                        public CustomPane() {
                            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("inner.fxml"));
                            loader.setRoot(this);
                            loader.setController(this);
                            try { loader.load(); } catch (java.io.IOException e) { throw new RuntimeException(e); }
                        }
                    }
                    """);
            var compiler = new ProcessBuilder("C:/Program Files/Java/jdk-25/bin/javac.exe", "-classpath",
                    "D:/eclipse/repos/JavaFXCompanion/lib/*", "-d", outputDir.toString(), control.toString())
                    .inheritIO().start();
            if (compiler.waitFor() != 0) throw new AssertionError("Custom control compilation failed");
            Files.writeString(outputDir.resolve("custom/inner.fxml"), """
                    <?import javafx.scene.layout.VBox?>
                    <?import javafx.scene.control.Label?>
                    <fx:root type="VBox" xmlns:fx="http://javafx.com/fxml/1"><children><Label text="Nested custom content"/></children></fx:root>
                    """);
            project.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_INFINITE, null);
            paneClass.getMethod("setProject", org.eclipse.core.resources.IProject.class).invoke(pane, project);
            String customFxml = """
                    <?import javafx.scene.control.TabPane?>
                    <?import javafx.scene.control.Tab?>
                    <?import custom.CustomPane?>
                    <TabPane xmlns:fx="http://javafx.com/fxml/1"><tabs><Tab text="Scripts"><content><CustomPane fx:id="scriptEditor"/></content></Tab></tabs></TabPane>
                    """;
            paneClass.getMethod("load", String.class, java.net.URL.class).invoke(pane, customFxml, sample.toUri().toURL());
            String customOutput = (String) paneClass.getMethod("getFxml").invoke(pane);
            if (!customOutput.contains("CustomPane") || !customOutput.contains("scriptEditor"))
                throw new AssertionError("Custom control round trip failed");
            System.out.println("PASS: Project custom control in Tab with nested FXML and round trip");
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
