package ro.sync.ecss.extensions.api;
import ro.sync.ecss.extensions.api.node.AttrValue;
import ro.sync.ecss.extensions.api.node.AuthorElement;
import ro.sync.ecss.extensions.api.node.AuthorNode;
public interface AuthorDocumentController {
    AuthorNode getNodeAtOffset(int offset) throws javax.swing.text.BadLocationException;
    void setAttribute(String attributeName, AttrValue value, AuthorElement element);
    String getText(int offset, int length) throws javax.swing.text.BadLocationException;
    void surroundInFragment(String xmlFragment, int startOffset, int endOffset) throws Exception;
    void renameElement(AuthorElement element, String name);
    void removeAttribute(String attributeName, AuthorElement element);
}
