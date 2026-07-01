package ch.kr.kbga.oxy;

/**
 * One search hit returned by the KBGA meta database (an actor, place, bibl or song).
 *
 * <p>The {@link #fullId} is the value written into the TEI attribute, e.g.
 * {@code kbga-actors-123}, {@code kbga-places-45}, {@code kbga-bibls-1} or
 * {@code kbga-songs-247}. The API already returns it as {@code full-id}, so the
 * plugin never assembles it itself.</p>
 */
final class KbgaEntity {

    final long id;
    final String fullId;
    final String label;    // primary display string (persName_full / placeName_full / asString / title)
    final String type;     // e.g. "Person" / "Organisation" / place_type / bibl type; may be empty
    final String detail;   // optional extra info (dates, city/country, authors); may be empty
    final String register; // "actors" | "places" | "bibls" | "songs"

    KbgaEntity(long id, String fullId, String label, String type, String detail, String register) {
        this.id = id;
        this.fullId = fullId;
        this.label = label == null ? "" : label;
        this.type = type == null ? "" : type;
        this.detail = detail == null ? "" : detail;
        this.register = register;
    }

    /** Rendered in the results list. */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(cut(label.isEmpty() ? fullId : label, 140));
        if (!detail.isEmpty()) {
            b.append(", ").append(cut(detail, 60));
        }
        if (!type.isEmpty()) {
            b.append("  [").append(type).append("]");
        }
        b.append("   → ").append(fullId);
        return b.toString();
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max).trim() + "…" : s;
    }
}
