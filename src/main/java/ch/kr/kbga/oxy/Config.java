package ch.kr.kbga.oxy;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    static final String OPT_SCAN     = "kbga.oxy.scanOccurrences";
    /** Recent picks are stored per register under {@code kbga.oxy.recent.<register>}. */
    static final String OPT_RECENT_PREFIX = "kbga.oxy.recent.";

    /** How many recent picks to remember per register. */
    static final int RECENT_MAX = 10;

    /** Default KBGA meta database. Change to a staging/DDEV URL in the settings if needed. */
    static final String DEFAULT_URL = "https://meta.karl-barth.ch";
    static final String DEFAULT_PERPAGE = "30";
    /**
     * Strict TLS by default (the default URL has a valid certificate). Enable lenient
     * TLS in the settings for local DDEV/mkcert hosts whose cert Java does not trust.
     */
    static final String DEFAULT_INSECURE = "false";
    /** Offer to tag further occurrences of a just-referenced actor/place (Text mode). */
    static final String DEFAULT_SCAN = "true";
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

    boolean isScanOccurrences() {
        return "true".equalsIgnoreCase(val(OPT_SCAN, DEFAULT_SCAN));
    }

    void setScanOccurrences(boolean b) {
        store.setOption(OPT_SCAN, b ? "true" : "false");
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

    // --- recent picks (MRU) per register -----------------------------------

    /** Most-recently-used picks for {@code register}, newest first (may be empty). */
    List<KbgaEntity> getRecent(String register) {
        List<KbgaEntity> out = new ArrayList<KbgaEntity>();
        String raw = store.getOption(OPT_RECENT_PREFIX + register, "");
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split("\\t", -1);
            if (f.length < 5 || f[0].isEmpty()) {
                continue;
            }
            long id = 0L;
            try {
                id = Long.parseLong(f[4]);
            } catch (NumberFormatException ignore) {
                // keep 0
            }
            out.add(new KbgaEntity(id, f[0], f[1], f[2], f[3], register));
        }
        return out;
    }

    /** Record {@code e} as the newest pick for its register (deduped, capped at {@link #RECENT_MAX}). */
    void addRecent(KbgaEntity e) {
        if (e == null || e.register == null) {
            return;
        }
        List<KbgaEntity> list = getRecent(e.register);
        StringBuilder b = new StringBuilder();
        b.append(encRecent(e));
        int kept = 1;
        for (KbgaEntity old : list) {
            if (kept >= RECENT_MAX) {
                break;
            }
            if (old.fullId.equals(e.fullId)) {
                continue; // move existing entry to the front
            }
            b.append('\n').append(encRecent(old));
            kept++;
        }
        store.setOption(OPT_RECENT_PREFIX + e.register, b.toString());
    }

    private static String encRecent(KbgaEntity e) {
        return clean(e.fullId) + "\t" + clean(e.label) + "\t" + clean(e.type)
                + "\t" + clean(e.detail) + "\t" + e.id;
    }

    /** Strip tab/newline so a pick round-trips through the line-based store. */
    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // --- browser deep-links ------------------------------------------------

    /** Portal search page for a register, e.g. to check or create a missing entry. */
    String portalSearchUrl(String register, String query) {
        return getBaseUrl() + "/" + register + "?search=" + enc(query);
    }

    /** Portal detail page for a specific entity. */
    String entityUrl(String register, long id) {
        return getBaseUrl() + "/" + register + "/" + id;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
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
