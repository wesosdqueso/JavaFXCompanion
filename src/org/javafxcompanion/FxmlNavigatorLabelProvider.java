package org.javafxcompanion;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/** Keep FXML's recognizable icon in both physical and virtual tree entries. */
public final class FxmlNavigatorLabelProvider extends LabelProvider {
    private final WorkbenchLabelProvider delegate = new WorkbenchLabelProvider();
    private Image fxmlIcon;

    @Override public String getText(Object element) { return delegate.getText(element); }

    @Override public Image getImage(Object element) {
        if (element instanceof IFile file && "fxml".equalsIgnoreCase(file.getFileExtension())) {
            if (fxmlIcon == null) {
                var url = FileLocator.find(Platform.getBundle("org.javafxcompanion"),
                    new Path("icons/page_white_code_red.png"), null);
                if (url != null) fxmlIcon = ImageDescriptor.createFromURL(url).createImage();
            }
            if (fxmlIcon != null) return fxmlIcon;
        }
        return delegate.getImage(element);
    }

    @Override public void dispose() {
        if (fxmlIcon != null) { fxmlIcon.dispose(); fxmlIcon = null; }
        delegate.dispose();
        super.dispose();
    }
}
