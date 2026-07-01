package ro.sync.exml.workspace.api.options;
public interface WSOptionsStorage {
    String getOption(String key, String defaultValue);
    void setOption(String key, String value);
}
