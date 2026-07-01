package ro.sync.ecss.extensions.api.node;
public interface AuthorNode {
    AuthorNode getParent();
    String getName();
    int getStartOffset();
    int getEndOffset();
}
