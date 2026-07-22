package ro.sync.exml.workspace.api.editor.page.author;
import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.exml.workspace.api.editor.page.WSEditorPage;
public interface WSAuthorEditorPage extends WSEditorPage {
    AuthorDocumentController getDocumentController();
    int getSelectionStart();
    int getSelectionEnd();
    boolean hasSelection();
    String getSelectedText();
    int[] getBalancedSelection(int startOffset, int endOffset);
}
