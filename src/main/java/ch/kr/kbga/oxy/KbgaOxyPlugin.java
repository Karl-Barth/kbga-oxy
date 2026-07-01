package ch.kr.kbga.oxy;

import ro.sync.exml.plugin.Plugin;
import ro.sync.exml.plugin.PluginDescriptor;

/**
 * Plugin entry point (boilerplate singleton required by Oxygen).
 */
public class KbgaOxyPlugin extends Plugin {

    private static KbgaOxyPlugin instance = null;

    public KbgaOxyPlugin(PluginDescriptor descriptor) {
        super(descriptor);
        if (instance != null) {
            throw new IllegalStateException("Already instantiated!");
        }
        instance = this;
    }

    public static KbgaOxyPlugin getInstance() {
        return instance;
    }
}
