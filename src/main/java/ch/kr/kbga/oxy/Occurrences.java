package ch.kr.kbga.oxy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds further occurrences of a referenced entity in the document's serialized XML so the
 * editor can tag them all at once. Matching is textual and conservative:
 *
 * <ul>
 *   <li>search terms are derived from the marked-up text and the DB record's {@code name},
 *       {@code alternative_names} and {@code abbreviations} — including a "Vorname Nachname"
 *       form and the bare surname from a "Nachname, Vorname" entry;</li>
 *   <li>matches respect word boundaries and accept a German genitive ending
 *       ({@code Barths}, {@code Marx'}) — only the base name is wrapped, the ending stays
 *       outside the element (TEI convention);</li>
 *   <li>text already inside a reference-bearing element (persName, placeName, …) is skipped,
 *       so nothing gets double-tagged.</li>
 * </ul>
 */
final class Occurrences {

    /** Fallback context width (chars per side) when no explicit width is given. */
    static final int DEFAULT_CONTEXT = 60;

    /** A single occurrence to (optionally) tag: {@code [start, end)} is the base name span. */
    static final class Match {
        final int start;
        final int end;
        final String term;
        final String snippet; // context for the preview, base name marked with «…»
        boolean selected = true;

        Match(int start, int end, String term, String snippet) {
            this.start = start;
            this.end = end;
            this.term = term;
            this.snippet = snippet;
        }
    }

    private Occurrences() { }

    // --- term derivation ---------------------------------------------------

    /** Distinct search terms for {@code entity}, longest first, derived from its names. */
    static List<String> terms(KbgaEntity entity, String markedText) {
        Set<String> raw = new LinkedHashSet<String>();
        collect(raw, markedText);
        if (entity != null) {
            for (String n : entity.names) {
                collect(raw, n);
            }
        }
        // longest first so a full "Vorname Nachname" claims a span before the bare surname.
        List<String> terms = new ArrayList<String>(raw);
        terms.sort((a, b) -> b.length() - a.length());
        return terms;
    }

    /** Break one raw name into usable tokens and add them (deduped, min length 3). */
    private static void collect(Set<String> out, String raw) {
        if (raw == null) {
            return;
        }
        String s = collapse(raw);
        // drop a trailing parenthetical qualifier, e.g. "Barth (Pfarrer aus …)".
        s = s.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        if (s.isEmpty()) {
            return;
        }
        int comma = s.indexOf(',');
        if (comma > 0) {
            String surname = s.substring(0, comma).trim();
            String given = s.substring(comma + 1).trim();
            given = given.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
            add(out, surname);
            if (!given.isEmpty()) {
                add(out, given + " " + surname); // "Eduard Thurneysen"
            }
        } else {
            add(out, s);
            int sp = s.lastIndexOf(' ');
            if (sp > 0) {
                add(out, s.substring(sp + 1).trim()); // bare surname from "Karl Barth"
            }
        }
    }

    private static void add(Set<String> out, String term) {
        if (term != null) {
            term = term.trim();
            if (term.length() >= 3) {
                out.add(term);
            }
        }
    }

    // --- scanning ----------------------------------------------------------

    /**
     * All occurrences of {@code terms} in {@code doc} that lie in text content and are not
     * enclosed by any element in {@code skipElements}. Overlapping matches are dropped
     * (longest term wins, since {@code terms} is sorted longest-first).
     */
    static List<Match> find(String doc, Set<String> skipElements, List<String> terms) {
        return find(doc, skipElements, terms, DEFAULT_CONTEXT);
    }

    /**
     * As {@link #find(String, Set, List)}, but with an explicit preview context width
     * ({@code contextChars} characters shown on each side of the base name).
     */
    static List<Match> find(String doc, Set<String> skipElements, List<String> terms, int contextChars) {
        List<Match> matches = new ArrayList<Match>();
        if (doc == null || doc.isEmpty() || terms.isEmpty()) {
            return matches;
        }
        List<String> stack = new ArrayList<String>();
        int i = 0;
        int n = doc.length();
        while (i < n) {
            char c = doc.charAt(i);
            if (c == '<') {
                i = consumeTag(doc, i, stack);
            } else {
                int lt = doc.indexOf('<', i);
                if (lt < 0) {
                    lt = n;
                }
                if (!enclosedByTarget(stack, skipElements)) {
                    scanRun(doc, i, lt, terms, matches, contextChars);
                }
                i = lt;
            }
        }
        return matches;
    }

    /** Advance past the markup construct starting at {@code lt} ('<'), updating the stack. */
    private static int consumeTag(String doc, int lt, List<String> stack) {
        int n = doc.length();
        if (doc.startsWith("<!--", lt)) {
            int e = doc.indexOf("-->", lt);
            return e < 0 ? n : e + 3;
        }
        if (doc.startsWith("<![CDATA[", lt)) {
            int e = doc.indexOf("]]>", lt);
            return e < 0 ? n : e + 3;
        }
        if (doc.startsWith("<?", lt) || doc.startsWith("<!", lt)) {
            int e = doc.indexOf('>', lt);
            return e < 0 ? n : e + 1;
        }
        int gt = doc.indexOf('>', lt);
        if (gt < 0) {
            return n;
        }
        String tag = doc.substring(lt + 1, gt).trim();
        boolean closing = tag.startsWith("/");
        boolean selfClosing = tag.endsWith("/");
        String name = elementName(tag);
        if (closing) {
            popTo(stack, name);
        } else if (!selfClosing) {
            stack.add(name);
        }
        return gt + 1;
    }

    /** Local element name from a tag body like "/tei:persName" or "placeName n=\"1\"". */
    private static String elementName(String tag) {
        int start = 0;
        int len = tag.length();
        while (start < len && (tag.charAt(start) == '/' || Character.isWhitespace(tag.charAt(start)))) {
            start++;
        }
        int end = start;
        while (end < len) {
            char c = tag.charAt(end);
            if (Character.isWhitespace(c) || c == '/' || c == '>') {
                break;
            }
            end++;
        }
        String q = tag.substring(start, end);
        int colon = q.indexOf(':');
        return colon >= 0 ? q.substring(colon + 1) : q;
    }

    private static void popTo(List<String> stack, String name) {
        for (int k = stack.size() - 1; k >= 0; k--) {
            if (stack.get(k).equals(name)) {
                while (stack.size() > k) {
                    stack.remove(stack.size() - 1);
                }
                return;
            }
        }
    }

    private static boolean enclosedByTarget(List<String> stack, Set<String> skipElements) {
        for (String name : stack) {
            if (skipElements.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** Find term matches inside the text run {@code doc[from, to)} and add non-overlapping ones. */
    private static void scanRun(String doc, int from, int to, List<String> terms,
                                List<Match> matches, int contextChars) {
        String run = doc.substring(from, to);
        for (String term : terms) {
            int idx = 0;
            while (true) {
                int p = run.indexOf(term, idx);
                if (p < 0) {
                    break;
                }
                int start = from + p;
                int end = start + term.length();
                if (boundaryBefore(doc, start) && genitiveOrBoundaryAfter(doc, end)
                        && !overlaps(matches, start, end)) {
                    matches.add(new Match(start, end, term, snippet(doc, start, end, contextChars)));
                }
                idx = p + 1;
            }
        }
    }

    private static boolean boundaryBefore(String doc, int start) {
        if (start == 0) {
            return true;
        }
        return !isNameChar(doc.charAt(start - 1));
    }

    /** True if the char after the base name is a boundary, or a German genitive ending. */
    private static boolean genitiveOrBoundaryAfter(String doc, int end) {
        if (end >= doc.length()) {
            return true;
        }
        char c = doc.charAt(end);
        if (!isNameChar(c)) {
            return true; // any non-name char is a boundary — incl. apostrophe genitive (Marx')
        }
        // a trailing letter is only allowed if it is a genitive 's' followed by a boundary.
        if (c == 's' && (end + 1 >= doc.length() || !isNameChar(doc.charAt(end + 1)))) {
            return true;
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '‐';
    }

    private static boolean overlaps(List<Match> matches, int start, int end) {
        for (Match m : matches) {
            if (start < m.end && m.start < end) {
                return true;
            }
        }
        return false;
    }

    private static String snippet(String doc, int start, int end, int ctx) {
        int a = Math.max(0, start - ctx);
        int b = Math.min(doc.length(), end + ctx);
        // don't slice a word in half at the edges: extend outward to the word boundary,
        // bounded so a very long token can't blow up the preview.
        int lo = Math.max(0, a - 20);
        while (a > lo && isNameChar(doc.charAt(a - 1))) {
            a--;
        }
        int hi = Math.min(doc.length(), b + 20);
        while (b < hi && isNameChar(doc.charAt(b))) {
            b++;
        }
        String before = collapse(doc.substring(a, start).replaceAll("<[^>]*>", " "));
        String base = doc.substring(start, end);
        String after = collapse(doc.substring(end, b).replaceAll("<[^>]*>", " "));
        return (a > 0 ? "…" : "") + before + " «" + base + "» " + after + (b < doc.length() ? "…" : "");
    }

    private static String collapse(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
