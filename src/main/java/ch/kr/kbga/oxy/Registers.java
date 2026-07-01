package ch.kr.kbga.oxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four KBGA registers the plugin can reference, together with the TEI attribute
 * each one is written into and any extra attributes to stamp on the element.
 *
 * <p>Convention verified against the KBGA edition data:</p>
 * <ul>
 *   <li><b>actors</b> (Akteure) — {@code persName} / {@code orgName}, written into {@code @ref}
 *       as {@code kbga-actors-<id>}.</li>
 *   <li><b>places</b> (Orte) — {@code placeName}, written into {@code @ref}
 *       as {@code kbga-places-<id>}.</li>
 *   <li><b>bibls</b> (Literatur) — {@code bibl}, written into {@code @corresp}
 *       as {@code kbga-bibls-<id>}.</li>
 *   <li><b>songs</b> (Lieder) — {@code bibl} with {@code @type="song"}, written into
 *       {@code @corresp} as {@code kbga-songs-<id>}.</li>
 * </ul>
 */
final class Registers {

    /** A referenceable KBGA register. */
    static final class Register {
        /** API path segment and {@code full-id} infix, e.g. "actors". */
        final String key;
        /** German label shown in the register combo, e.g. "Akteure (persName / orgName)". */
        final String label;
        /** TEI attribute that receives the id, e.g. "ref" or "corresp". */
        final String attribute;
        /** Extra attributes to set on the element (insertion order), e.g. {@code type=song}. */
        final Map<String, String> extraAttributes;

        Register(String key, String label, String attribute, Map<String, String> extraAttributes) {
            this.key = key;
            this.label = label;
            this.attribute = attribute;
            this.extraAttributes = extraAttributes == null
                    ? Collections.<String, String>emptyMap() : extraAttributes;
        }

        public String toString() {
            return label;
        }
    }

    private static final Map<String, Register> BY_KEY = new LinkedHashMap<String, Register>();

    static {
        add(new Register("actors", "Akteure (persName / orgName)", "ref", null));
        add(new Register("places", "Orte (placeName)", "ref", null));
        add(new Register("bibls", "Literatur (bibl)", "corresp", null));
        add(new Register("songs", "Lieder (bibl @type=song)", "corresp", songType()));
    }

    private Registers() { }

    private static Map<String, String> songType() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("type", "song");
        return m;
    }

    private static void add(Register r) {
        BY_KEY.put(r.key, r);
    }

    /** All registers in display order: actors, places, bibls, songs. */
    static List<Register> all() {
        return new ArrayList<Register>(BY_KEY.values());
    }

    /** The register for {@code key}, or the actors register as a safe fallback. */
    static Register get(String key) {
        Register r = BY_KEY.get(key);
        return r != null ? r : BY_KEY.get("actors");
    }

    /** Whether {@code key} is one of the known registers. */
    static boolean isKnown(String key) {
        return BY_KEY.containsKey(key);
    }
}
