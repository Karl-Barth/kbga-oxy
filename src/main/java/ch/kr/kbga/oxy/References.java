package ch.kr.kbga.oxy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts KBGA references ({@code kbga-<register>-<id>}) from a document's serialized
 * text so they can be validated against the meta database. Purely textual — it finds ids
 * wherever they appear (in {@code @ref}, {@code @corresp} or elsewhere) and de-duplicates
 * them, keeping a per-id occurrence count.
 */
final class References {

    /** One distinct reference found in the document. */
    static final class Ref {
        final String register; // "actors" | "places" | "bibls" | "songs"
        final long id;
        final String fullId;   // "kbga-actors-123"
        final int count;       // how often it occurs in the document

        Ref(String register, long id, String fullId, int count) {
            this.register = register;
            this.id = id;
            this.fullId = fullId;
            this.count = count;
        }
    }

    private static final Pattern KBGA_ID =
            Pattern.compile("kbga-(actors|places|bibls|songs)-(\\d+)");

    private References() { }

    /** All distinct references in {@code documentText}, in first-seen order. */
    static List<Ref> scan(String documentText) {
        Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
        Map<String, String[]> parts = new LinkedHashMap<String, String[]>();
        if (documentText != null) {
            Matcher m = KBGA_ID.matcher(documentText);
            while (m.find()) {
                String fullId = m.group();
                int[] c = counts.get(fullId);
                if (c == null) {
                    counts.put(fullId, new int[] { 1 });
                    parts.put(fullId, new String[] { m.group(1), m.group(2) });
                } else {
                    c[0]++;
                }
            }
        }
        List<Ref> out = new ArrayList<Ref>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            String fullId = e.getKey();
            String[] p = parts.get(fullId);
            long id = 0L;
            try {
                id = Long.parseLong(p[1]);
            } catch (NumberFormatException ignore) {
                // keep 0
            }
            out.add(new Ref(p[0], id, fullId, e.getValue()[0]));
        }
        return out;
    }

    /** Total number of reference occurrences (sum of counts). */
    static int totalOccurrences(List<Ref> refs) {
        int n = 0;
        for (Ref r : refs) {
            n += r.count;
        }
        return n;
    }
}
