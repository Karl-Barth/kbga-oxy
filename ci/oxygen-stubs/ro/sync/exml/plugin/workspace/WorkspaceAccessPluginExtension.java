package ro.sync.exml.plugin.workspace;
import ro.sync.exml.plugin.PluginExtension;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
public interface WorkspaceAccessPluginExtension extends PluginExtension {
    void applicationStarted(StandalonePluginWorkspace pluginWorkspaceAccess);
    boolean applicationClosing();
}
