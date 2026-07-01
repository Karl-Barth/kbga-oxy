package ro.sync.exml.workspace.api.editor.page.text;
import ro.sync.exml.workspace.api.editor.page.WSEditorPage;
public interface WSTextEditorPage extends WSEditorPage {
    javax.swing.text.Document getDocument();
    int getCaretOffset();
    int getSelectionStart();
    int getSelectionEnd();
    boolean hasSelection();
    String getSelectedText();
}
