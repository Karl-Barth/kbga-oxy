package ch.kr.kbga.oxy;

import java.util.LinkedHashMap;
import java.util.Map;

import ro.sync.exml.workspace.api.options.WSOptionsStorage;

/**
 * Plugin configuration, persisted through Oxygen's options storage so the editor
 * only has to set it once. The base URL is the only thing that must be correct;
 * the {@code kbga-} slug and register are delivered by the API inside {@code full-id}.
 */
final class Config {

    static final String OPT_URL      = "kbga.oxy.baseUrl";
    static final String OPT_PERPAGE  = "kbga.oxy.perPage";
    static final String OPT_INSECURE = "kbga.oxy.insecureTls";
    static final String OPT_TEMPLATE = "kbga.oxy.template";
    static final String OPT_MAPPING  = "kbga.oxy.mapping";

    /** Default KBGA meta database. Change to a staging/DDEV URL in the settings if needed. */
    static final String DEFAULT_URL = "https://meta.karl-barth.ch";
    static final String DEFAULT_PERPAGE = "30";
    /**
     * Strict TLS by default (the default URL has a valid certificate). Enable lenient
     * TLS in the settings for local DDEV/mkcert hosts whose cert Java does not trust.
     */
    static final String DEFAULT_INSECURE = "false";
    /** Template for the written value. Placeholders: {fullId} {slug} {register} {id}. */
    static final String DEFAULT_TEMPLATE = "{fullId}";
    /**
     * Element → register auto-detection (one {@code element=register} per line, "#" comments
     * allowed). Determines which register is pre-selected when the caret sits in that element.
     * The written attribute itself comes from {@link Registers} (ref for actors/places,
     * corresp for bibls/songs), not from this mapping. The user can always switch register
     * in the search dialog — {@code bibl} defaults to Literatur and can be switched to Lieder.
     */
    static final String DEFAULT_MAPPING =
            "persName=actors\norgName=actors\nplaceName=places\nbibl=bibls";

    private final WSOptionsStorage store;

    Config(WSOptionsStorage store) {
        this.store = store;
    }

    String getBaseUrl() {
        String v = val(OPT_URL, DEFAULT_URL);
        // strip trailing slashes so we can append "/api/..."
        return v.replaceAll("/+$", "");
    }

    void setBaseUrl(String v) {
        store.setOption(OPT_URL, v == null ? "" : v.trim());
    }

    int getPerPage() {
        try {
            return Integer.parseInt(val(OPT_PERPAGE, DEFAULT_PERPAGE));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    void setPerPage(int n) {
        store.setOption(OPT_PERPAGE, String.valueOf(n));
    }

    boolean isInsecureTls() {
        return "true".equalsIgnoreCase(val(OPT_INSECURE, DEFAULT_INSECURE));
    }

    void setInsecureTls(boolean b) {
        store.setOption(OPT_INSECURE, b ? "true" : "false");
    }

    String getTemplate() {
        return val(OPT_TEMPLATE, DEFAULT_TEMPLATE);
    }

    void setTemplate(String v) {
        store.setOption(OPT_TEMPLATE, (v == null || v.trim().isEmpty()) ? DEFAULT_TEMPLATE : v.trim());
    }

    String getMappingRaw() {
        return val(OPT_MAPPING, DEFAULT_MAPPING);
    }

    void setMappingRaw(String v) {
        store.setOption(OPT_MAPPING, (v == null || v.trim().isEmpty()) ? DEFAULT_MAPPING : v.trim());
    }

    /**
     * Parsed element local-name → register key (insertion order preserved). Only registers
     * known to {@link Registers} are kept; unknown lines are ignored.
     */
    Map<String, String> getMapping() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String line : getMappingRaw().split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String tag = line.substring(0, eq).trim();
            String reg = line.substring(eq + 1).trim();
            if (!tag.isEmpty() && Registers.isKnown(reg)) {
                m.put(tag, reg);
            }
        }
        if (m.isEmpty()) {
            m.put("persName", "actors");
            m.put("orgName", "actors");
            m.put("placeName", "places");
            m.put("bibl", "bibls");
        }
        return m;
    }

    /** Build the attribute value for an entity using the configured template. */
    String formatRef(KbgaEntity e) {
        String fullId = e.fullId;
        String register = e.register;
        String slug = "";
        String id = String.valueOf(e.id);
        String marker = "-" + register + "-";
        int p = fullId.indexOf(marker);
        if (p >= 0) {
            slug = fullId.substring(0, p);
            id = fullId.substring(p + marker.length());
        }
        return getTemplate()
                .replace("{fullId}", fullId)
                .replace("{slug}", slug)
                .replace("{register}", register)
                .replace("{id}", id);
    }

    private String val(String key, String def) {
        String v = store.getOption(key, def);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
