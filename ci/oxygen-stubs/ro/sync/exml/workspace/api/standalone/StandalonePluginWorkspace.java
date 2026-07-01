package ro.sync.exml.workspace.api.standalone;
import ro.sync.exml.workspace.api.PluginWorkspace;
public interface StandalonePluginWorkspace extends PluginWorkspace {
    void addToolbarComponentsCustomizer(ToolbarComponentsCustomizer customizer);
    void addMenuBarCustomizer(MenuBarCustomizer customizer);
}
