package org.javafxcompanion;

import java.net.URL;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

/** One Eclipse editor/document/save lifecycle with source and visual pages. */
public final class FxmlSceneEditor extends MultiPageEditorPart {
    private ITextEditor source;
    private Composite designPage;
    private Composite host;
    private Label error;
    private StackLayout stack;
    private SceneBuilderPane design;
    private boolean designValid;
    private boolean designChanged;
    private String designBaseline;
    private boolean closed;

    @Override public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        if (!(input instanceof FileEditorInput)) throw new PartInitException("The embedded editor requires a workspace file.");
        super.init(site, input);
        setPartName(input.getName());
    }

    private ITextEditor createSourceEditor() {
        // Reuse the installed FXML editor when present; remain independent otherwise.
        for (String id : new String[] { "org.eclipse.fx.ide.fxml.editors.FXMLEditor", "org.eclipse.wst.xml.ui.internal.tabletree.XMLMultiPageEditorPart" }) {
            for (var element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.editors")) {
                if (id.equals(element.getAttribute("id"))) {
                    try {
                        Object editor = element.createExecutableExtension("class");
                        if (editor instanceof ITextEditor text) return text;
                    } catch (CoreException | LinkageError e) { /* Plain text remains available. */ }
                }
            }
        }
        return new TextEditor();
    }

    @Override protected void createPages() {
        try {
            source = createSourceEditor();
            int code = addPage(source, getEditorInput());
            setPageText(code, "FXML Source");
            designPage = new Composite(getContainer(), SWT.NONE);
            stack = new StackLayout();
            designPage.setLayout(stack);
            host = new Composite(designPage, SWT.NONE);
            host.setLayout(new FillLayout());
            error = new Label(designPage, SWT.WRAP);
            error.setText("Select Design to load Scene Builder.");
            stack.topControl = error;
            int visual = addPage(designPage);
            setPageText(visual, "Design");
            setActivePage(code);
        } catch (PartInitException e) {
            MessageDialog.openError(getSite().getShell(), "FXML + Scene Builder", e.getMessage());
        }
    }

    private IDocument document() { return source.getDocumentProvider().getDocument(source.getEditorInput()); }

    @Override protected void pageChange(int page) {
        if (page == 0) syncDesign();
        super.pageChange(page);
        if (page == 1) {
            designValid = false;
            designChanged = false;
            try {
                if (design == null) design = new SceneBuilderPane(host, text -> {
                    if (closed || !designValid || getActivePage() != 1) return;
                    if (java.util.Objects.equals(text, document().get())) return;
                    designChanged = true;
                    updateDocument(text);
                }, () -> {
                    if (!closed && designValid && getActivePage() == 1) {
                        designChanged = true;
                        firePropertyChange(PROP_DIRTY);
                    }
                });
                URL location = ((FileEditorInput) getEditorInput()).getFile().getLocationURI().toURL();
                design.load(document().get(), location);
                designBaseline = design.getFxml();
                designValid = true;
                stack.topControl = host;
            } catch (Exception | LinkageError e) {
                error.setText("Unable to load the design. Your source code has been preserved.\n\n" + e.toString());
                stack.topControl = error;
                Platform.getLog(getClass()).log(new Status(IStatus.ERROR, "org.javafxcompanion", "Scene Builder initialization failed", e));
            }
            designPage.layout(true, true);
        }
    }

    private void updateDocument(String text) {
        if (text != null && !text.equals(document().get())) document().set(text);
    }

    private void syncDesign() {
        if (design != null && designValid) {
            String current = design.getFxml();
            if (designChanged || !java.util.Objects.equals(current, designBaseline)) updateDocument(current);
            designBaseline = current;
            designChanged = false;
        }
    }

    @Override public void doSave(IProgressMonitor monitor) {
        syncDesign();
        source.doSave(monitor);
    }
    @Override public void doSaveAs() {
        // FileEditorInput identity must follow Save As before resolving relative resources.
        syncDesign();
        source.doSaveAs();
        setInput(source.getEditorInput());
        setPartName(getEditorInput().getName());
    }
    @Override public boolean isSaveAsAllowed() { return source != null && source.isSaveAsAllowed(); }
    @Override public boolean isDirty() { return designChanged || super.isDirty(); }
    @Override public void setFocus() {
        if (getActivePage() == 1 && design != null && designValid) design.focus();
        else if (source != null) source.setFocus();
    }
    @Override public void dispose() { closed = true; super.dispose(); }
}
