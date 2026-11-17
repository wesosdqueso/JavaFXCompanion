package org.javafxcompanion;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonLabelProvider;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/** Keep FXML's recognizable icon in both physical and virtual tree entries. */
public final class FxmlNavigatorLabelProvider extends ColumnLabelProvider implements ICommonLabelProvider {
    private WorkbenchLabelProvider delegate;
    private Image fxmlIcon;
    private Image packageIcon;
    private Image warningIcon;

    private enum Association { CONTROLLER, PACKAGE, UNRESOLVED, NONE }

    private static Association association(IFile file) {
        String controller = FxmlNavigatorContentProvider.readControllerDeclaration(file);
        if (controller == null) return Association.NONE;
        var project = JavaCore.create(file.getProject());
        try {
            if (!controller.isBlank()) return project.exists() && project.findType(controller) != null
                    ? Association.CONTROLLER : Association.UNRESOLVED;
            return project.exists() && FxmlNavigatorContentProvider.matchingPackage(project, file) != null
                    ? Association.PACKAGE : Association.NONE;
        } catch (JavaModelException e) { return Association.NONE; }
    }

    private WorkbenchLabelProvider delegate() {
        if (delegate == null) delegate = new WorkbenchLabelProvider();
        return delegate;
    }

    @Override public String getText(Object element) {
        return element instanceof IFile file ? file.getName() : delegate().getText(element);
    }

    @Override public Image getImage(Object element) {
        if (element instanceof IFile file && "fxml".equalsIgnoreCase(file.getFileExtension())) {
            if (fxmlIcon == null) {
                var url = FileLocator.find(Platform.getBundle("org.javafxcompanion"),
                    new Path("icons/page_white_code_red.png"), null);
                if (url != null) fxmlIcon = ImageDescriptor.createFromURL(url).createImage();
            }
            if (fxmlIcon != null) return switch (association(file)) {
                case PACKAGE -> {
                    if (packageIcon == null) packageIcon = decorated(false);
                    yield packageIcon;
                }
                case UNRESOLVED -> {
                    if (warningIcon == null) warningIcon = decorated(true);
                    yield warningIcon;
                }
                default -> fxmlIcon;
            };
        }
        return delegate().getImage(element);
    }

    private Image decorated(boolean warning) {
        Image image = new Image(fxmlIcon.getDevice(), 9, 8);
        GC gc = new GC(image);
        try {
            int x = 0, y = 0;
            gc.setAntialias(SWT.ON);
            gc.setForeground(image.getDevice().getSystemColor(SWT.COLOR_DARK_GRAY));
            if (warning) {
                int[] triangle = { x + 4, y, x + 8, y + 7, x, y + 7 };
                gc.setBackground(image.getDevice().getSystemColor(SWT.COLOR_YELLOW));
                gc.fillPolygon(triangle);
                gc.drawPolygon(triangle);
                gc.setForeground(image.getDevice().getSystemColor(SWT.COLOR_BLACK));
                gc.drawLine(x + 4, y + 2, x + 4, y + 4);
                gc.drawPoint(x + 4, y + 6);
            } else {
                gc.setBackground(image.getDevice().getSystemColor(SWT.COLOR_GRAY));
                gc.fillRectangle(x, y + 1, 4, 2);
                gc.fillRectangle(x, y + 3, 8, 4);
                gc.drawPolyline(new int[] {x, y + 7, x, y + 1, x + 3, y + 1,
                        x + 4, y + 3, x + 8, y + 3, x + 8, y + 7, x, y + 7});
            }
        } finally { gc.dispose(); }
        try {
            var data = image.getImageData();
            data.alphaData = new byte[data.width * data.height];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    boolean opaque = warning ? Math.abs(x - 4) <= y * 4.0 / 7.0 + 0.5
                            : y >= 3 || y >= 1 && x <= 3;
                    data.setAlpha(x, y, opaque ? 255 : 0);
                }
            }
            return new DecorationOverlayIcon(fxmlIcon, ImageDescriptor.createFromImageData(data),
                    IDecoration.BOTTOM_RIGHT).createImage();
        } finally { image.dispose(); }
    }

    @Override public String getToolTipText(Object element) {
        if (!(element instanceof IFile file) || !"fxml".equalsIgnoreCase(file.getFileExtension())) return null;
        return switch (association(file)) {
            case CONTROLLER -> "FXML con fx:controller resuelto";
            case PACKAGE -> "Mostrado por coincidencia de paquete; sin controlador declarado";
            case UNRESOLVED -> "fx:controller declarado, pero no se encuentra la clase";
            case NONE -> "FXML sin asociación de controlador o paquete disponible";
        };
    }
    @Override public String getDescription(Object element) {
        String tooltip = getToolTipText(element);
        return tooltip != null ? tooltip : getText(element);
    }
    @Override public void init(ICommonContentExtensionSite site) {}
    @Override public void restoreState(IMemento memento) {}
    @Override public void saveState(IMemento memento) {}

    @Override public void dispose() {
        if (packageIcon != null) { packageIcon.dispose(); packageIcon = null; }
        if (warningIcon != null) { warningIcon.dispose(); warningIcon = null; }
        if (fxmlIcon != null) { fxmlIcon.dispose(); fxmlIcon = null; }
        if (delegate != null) delegate.dispose();
        super.dispose();
    }
}
