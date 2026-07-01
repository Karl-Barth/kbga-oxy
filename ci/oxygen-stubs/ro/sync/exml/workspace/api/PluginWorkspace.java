package ro.sync.exml.workspace.api;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.options.WSOptionsStorage;
public interface PluginWorkspace {
    int MAIN_EDITING_AREA = 0;
    WSEditor getCurrentEditorAccess(int editingArea);
    WSOptionsStorage getOptionsStorage();
}
