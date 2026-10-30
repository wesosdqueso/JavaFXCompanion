package org.javafxcompanion;

import java.net.URL;
import java.util.function.Consumer;
import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.HierarchyPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.preferences.MavenPreferences;
import javafx.application.Platform;
import javafx.embed.swt.FXCanvas;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.geometry.Orientation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

/** JavaFX and SWT share the UI thread through the official FXCanvas bridge. */
public final class SceneBuilderPane {
    private final FXCanvas canvas;
    private final EditorController controller;
    private boolean loading;
    private boolean disposed;
    private int generation;
    private final Consumer<String> changed;

    public SceneBuilderPane(Composite parent, Consumer<String> changed) {
        this(parent, changed, () -> {});
    }

    public SceneBuilderPane(Composite parent, Consumer<String> changed, Runnable modified) {
        this.changed = changed;
        canvas = new FXCanvas(parent, SWT.NONE);
        Platform.setImplicitExit(false);
        controller = new EditorController();
        var library = new LibraryPanelController(controller, new MavenPreferences());
        var hierarchy = new HierarchyPanelController(controller);
        var content = new ContentPanelController(controller);
        var inspector = new InspectorPanelController(controller);
        var left = new SplitPane(library.getPanelRoot(), hierarchy.getPanelRoot());
        left.setOrientation(Orientation.VERTICAL);
        left.setDividerPositions(0.55);
        var root = new SplitPane(left, content.getPanelRoot(), inspector.getPanelRoot());
        root.setDividerPositions(0.22, 0.75);
        var scene = new Scene(root);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), () -> controller.undo());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), () -> controller.redo());
        canvas.setScene(scene);
        controller.getJobManager().revisionProperty().addListener((value, oldRevision, newRevision) -> {
            if (loading || disposed) return;
            modified.run();
            int expected = generation;
            Platform.runLater(() -> {
                if (!disposed && !loading && expected == generation && controller.canGetFxmlText()) changed.accept(controller.getFxmlText(false));
            });
        });
        canvas.addDisposeListener(event -> {
            disposed = true;
            generation++;
            controller.stopFileWatching();
            canvas.setScene(null);
        });
    }

    public void load(String fxml, URL location) throws java.io.IOException {
        loading = true;
        generation++;
        try {
            controller.setFxmlTextAndLocation(fxml, location);
            controller.clearUndoRedo();
        } finally { loading = false; }
    }

    public String getFxml() { return controller.canGetFxmlText() ? controller.getFxmlText(false) : null; }
    public void focus() { canvas.setFocus(); }
}
