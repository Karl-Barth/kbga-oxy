package ch.kr.kbga.oxy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;

import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.node.AttrValue;
import ro.sync.ecss.extensions.api.node.AuthorElement;
import ro.sync.ecss.extensions.api.node.AuthorNode;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.editor.page.WSEditorPage;
import ro.sync.exml.workspace.api.editor.page.author.WSAuthorEditorPage;
import ro.sync.exml.workspace.api.editor.page.text.WSTextEditorPage;

/**
 * Locates the TEI element under the caret that should receive a KBGA reference,
 * and writes attributes onto it — in both <b>Author</b> and <b>Text</b> mode.
 *
 * <p>Which elements are targets and which register each pre-selects is configurable
 * (see {@link Config#getMapping()}). The default mapping is:</p>
 * <ul>
 *   <li>{@code persName} / {@code orgName} → {@code actors}</li>
 *   <li>{@code placeName} → {@code places}</li>
 *   <li>{@code bibl} → {@code bibls} (switch to {@code songs} in the dialog for Lieder)</li>
 * </ul>
 *
 * <p>The actual attribute name (e.g. {@code ref} vs {@code corresp}) and any extra
 * attributes (e.g. {@code type="song"}) come from {@link Registers}, driven by the
 * register the editor finally picks — not by the located element.</p>
 */
final class RefTargets {

    private RefTargets() { }

    /** A located element that can report context and accept attribute writes. */
    interface RefTarget {
        /** Register key pre-selected from the located element, e.g. "actors". */
        String register();
        /** Local element name, e.g. "persName". */
        String elementName();
        /** Inner text of the element, for pre-filling the search field (may be empty). */
        String currentText();
        /** Existing value of attribute {@code attr}, or null. */
        String currentRef(String attr);
        /** Set/replace attribute {@code name} with {@code value}. */
        void setAttribute(String name, String value) throws Exception;
        /** Rename the element to {@code elementName} (no-op if already named so). */
        void renameElementTo(String elementName) throws Exception;
        /** Remove attribute {@code name} if present (no-op otherwise). */
        void removeAttribute(String name) throws Exception;
    }

    /**
     * A non-empty text selection that is <em>not</em> yet inside a mapped element: it can
     * report the selected text and wrap it in a freshly created TEI element carrying the
     * KBGA reference — turning "mark up" and "reference" into a single step.
     */
    interface WrapTarget {
        /** The selected text, for pre-filling the search field. */
        String selectedText();
        /**
         * Wrap the current selection in {@code <element attr="value" extra…>…</element>}.
         * All attributes are written into the freshly created start tag at once.
         */
        void wrap(String element, String attr, String value, Map<String, String> extra) throws Exception;
    }

    /** Convenience overload (default mapping) — used by tests. */
    static RefTarget locate(WSEditor editor) {
        return locate(editor, defaultMapping());
    }

    static Map<String, String> defaultMapping() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("persName", "actors");
        m.put("orgName", "actors");
        m.put("placeName", "places");
        m.put("bibl", "bibls");
        return m;
    }

    /**
     * @param mapping element local-name → register key
     * @return a target, or null if the caret is not inside a mapped element.
     */
    static RefTarget locate(WSEditor editor, Map<String, String> mapping) {
        if (editor == null) {
            return null;
        }
        WSEditorPage page = editor.getCurrentPage();
        if (page instanceof WSAuthorEditorPage) {
            return locateAuthor((WSAuthorEditorPage) page, mapping);
        }
        if (page instanceof WSTextEditorPage) {
            return locateText((WSTextEditorPage) page, mapping);
        }
        return null;
    }

    /**
     * A wrap target for the current selection, or null if nothing usable is selected.
     * Used only as a fallback when {@link #locate} finds no mapped element under the caret.
     */
    static WrapTarget locateSelection(WSEditor editor) {
        if (editor == null) {
            return null;
        }
        WSEditorPage page = editor.getCurrentPage();
        if (page instanceof WSTextEditorPage) {
            return selectionText((WSTextEditorPage) page);
        }
        if (page instanceof WSAuthorEditorPage) {
            return selectionAuthor((WSAuthorEditorPage) page);
        }
        return null;
    }

    /** Assemble a start tag {@code <element attr="value" extra…>} with escaped attribute values. */
    private static String startTag(String element, String attr, String value, Map<String, String> extra) {
        StringBuilder b = new StringBuilder("<").append(element);
        b.append(' ').append(attr).append("=\"").append(escapeAttr(value)).append('"');
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                b.append(' ').append(e.getKey()).append("=\"").append(escapeAttr(e.getValue())).append('"');
            }
        }
        return b.append('>').toString();
    }

    private static String escapeAttr(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * The full serialized XML of the document, for reference validation, or {@code null}
     * if the current page is not a Text page (attribute values are only reliably reachable
     * from the text serialization — the caller then asks the user to switch to Text mode).
     */
    static String documentText(WSEditor editor) {
        if (editor == null) {
            return null;
        }
        WSEditorPage page = editor.getCurrentPage();
        if (page instanceof WSTextEditorPage) {
            try {
                Document doc = ((WSTextEditorPage) page).getDocument();
                return doc.getText(0, doc.getLength());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /** The underlying text-mode {@link Document}, or null if the page is not a Text page. */
    static Document textDocument(WSEditor editor) {
        if (editor == null) {
            return null;
        }
        WSEditorPage page = editor.getCurrentPage();
        if (page instanceof WSTextEditorPage) {
            return ((WSTextEditorPage) page).getDocument();
        }
        return null;
    }

    /** Wrap {@code doc[start, end)} in {@code <element attr="value" extra…>…</element>}. */
    static void wrapRange(Document doc, int start, int end, String element,
                          String attr, String value, Map<String, String> extra) throws Exception {
        doc.insertString(end, "</" + element + ">", null);
        doc.insertString(start, startTag(element, attr, value, extra), null);
    }

    /**
     * Wrap several non-overlapping ranges at once, as a single edit over the covering span
     * {@code [ranges[0].start, ranges[last].end)}, so the whole batch collapses into one
     * undoable operation in the editor instead of two inserts per range.
     *
     * <p>{@code ranges} is an array of {@code {start, end}} pairs that must be sorted
     * ascending by start and must not overlap (as produced by {@link Occurrences}).</p>
     */
    static void wrapRanges(Document doc, int[][] ranges, String element,
                           String attr, String value, Map<String, String> extra) throws Exception {
        if (ranges == null || ranges.length == 0) {
            return;
        }
        if (ranges.length == 1) {
            wrapRange(doc, ranges[0][0], ranges[0][1], element, attr, value, extra);
            return;
        }
        int lo = ranges[0][0];
        int hi = ranges[ranges.length - 1][1];
        String span = doc.getText(lo, hi - lo);
        String open = startTag(element, attr, value, extra);
        String close = "</" + element + ">";
        StringBuilder b = new StringBuilder(
                span.length() + ranges.length * (open.length() + close.length()));
        int cursor = 0; // offset within span already copied out
        for (int[] r : ranges) {
            int s = r[0] - lo;
            int e = r[1] - lo;
            b.append(span, cursor, s); // text before this occurrence
            b.append(open);
            b.append(span, s, e);      // the base name itself
            b.append(close);
            cursor = e;
        }
        b.append(span, cursor, span.length()); // remaining tail
        String replacement = b.toString();
        if (doc instanceof AbstractDocument) {
            ((AbstractDocument) doc).replace(lo, hi - lo, replacement, null);
        } else {
            doc.remove(lo, hi - lo);
            doc.insertString(lo, replacement, null);
        }
    }

    private static String localName(String qName) {
        if (qName == null) {
            return "";
        }
        int c = qName.indexOf(':');
        return c >= 0 ? qName.substring(c + 1) : qName;
    }

    // --- Author mode -------------------------------------------------------

    private static RefTarget locateAuthor(WSAuthorEditorPage page, Map<String, String> mapping) {
        try {
            AuthorDocumentController ctrl = page.getDocumentController();
            int offset = balancedStart(page, page.getSelectionStart(), page.getSelectionEnd());
            // getSelectedText() reports the visual selection reliably; ctrl.getText on the node's
            // offsets can read the wrong region with pretty-printed mixed content (see selectionAuthor).
            String selected = null;
            try {
                if (page.hasSelection()) {
                    selected = page.getSelectedText();
                }
            } catch (Exception ignore) {
                // no reliable selection text — fall back to the element's own text
            }
            AuthorNode node = ctrl.getNodeAtOffset(offset);
            while (node != null) {
                if (node instanceof AuthorElement) {
                    String ln = localName(node.getName());
                    String register = mapping.get(ln);
                    if (register != null) {
                        return new AuthorRefTarget(ctrl, (AuthorElement) node, ln, register, selected);
                    }
                }
                node = node.getParent();
            }
        } catch (Exception e) {
            // fall through -> null
        }
        return null;
    }

    private static final class AuthorRefTarget implements RefTarget {
        private final AuthorDocumentController ctrl;
        private final AuthorElement el;
        private final String name;
        private final String register;
        private final String selectedText; // reliable visual selection, or null

        AuthorRefTarget(AuthorDocumentController ctrl, AuthorElement el, String name, String register,
                        String selectedText) {
            this.ctrl = ctrl;
            this.el = el;
            this.name = name;
            this.register = register;
            this.selectedText = selectedText;
        }

        public String register() { return register; }
        public String elementName() { return name; }

        public String currentText() {
            if (selectedText != null && !selectedText.trim().isEmpty()) {
                return collapse(selectedText);
            }
            try {
                int start = el.getStartOffset();
                int len = el.getEndOffset() - start;
                return collapse(ctrl.getText(start, len));
            } catch (Exception e) {
                return "";
            }
        }

        public String currentRef(String attr) {
            AttrValue a = el.getAttribute(attr);
            return a == null ? null : a.getValue();
        }

        public void setAttribute(String name, String value) {
            ctrl.setAttribute(name, new AttrValue(value), el);
        }

        public void renameElementTo(String elementName) {
            if (elementName != null && !elementName.equals(localName(el.getName()))) {
                ctrl.renameElement(el, elementName);
            }
        }

        public void removeAttribute(String attrName) {
            ctrl.removeAttribute(attrName, el);
        }
    }

    private static WrapTarget selectionAuthor(WSAuthorEditorPage page) {
        try {
            if (!page.hasSelection()) {
                return null;
            }
            AuthorDocumentController ctrl = page.getDocumentController();
            int rawStart = page.getSelectionStart();
            int rawEnd = page.getSelectionEnd();
            if (rawEnd <= rawStart) {
                return null;
            }
            // The raw selection offsets from WSAuthorEditorPage don't always line up with the
            // AuthorDocumentController content model — with pretty-printed mixed content (e.g. a
            // <persName> inside an indented <note>/<bibl>) they point at a different range, so
            // feeding them straight into surroundInFragment wraps the wrong text ("one line
            // below"). getBalancedSelection maps them to valid content offsets — the same step
            // oXygen's own surround operations perform before surroundInFragment.
            int start = rawStart;
            int end = rawEnd;
            try {
                int[] bal = page.getBalancedSelection(rawStart, rawEnd);
                if (bal != null && bal.length == 2 && bal[1] > bal[0]) {
                    start = bal[0];
                    end = bal[1];
                }
            } catch (Exception ignore) {
                // keep raw offsets
            }
            // getSelectedText() reflects the visual selection reliably; prefer it for the search
            // pre-fill (ctrl.getText on the offsets can read the wrong region in the case above).
            String sel = page.getSelectedText();
            if (sel == null || sel.trim().isEmpty()) {
                try {
                    sel = ctrl.getText(start, end - start);
                } catch (Exception e) {
                    sel = "";
                }
            }
            return new AuthorWrapTarget(ctrl, start, end, namespaceAt(ctrl, start), collapse(sel));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Map a raw editor selection {@code [rawStart, rawEnd)} to a balanced start offset that is
     * valid in the {@link AuthorDocumentController} content model. Falls back to {@code rawStart}
     * if balancing is unavailable. See {@link #selectionAuthor} for why this matters.
     */
    private static int balancedStart(WSAuthorEditorPage page, int rawStart, int rawEnd) {
        try {
            int[] bal = page.getBalancedSelection(rawStart, Math.max(rawStart, rawEnd));
            if (bal != null && bal.length == 2) {
                return bal[0];
            }
        } catch (Exception ignore) {
            // fall through
        }
        return rawStart;
    }

    /** Namespace URI of the nearest enclosing element, or "" if none/undeclared. */
    private static String namespaceAt(AuthorDocumentController ctrl, int offset) {
        try {
            AuthorNode n = ctrl.getNodeAtOffset(offset);
            while (n != null) {
                if (n instanceof AuthorElement) {
                    String ns = ((AuthorElement) n).getNamespace();
                    if (ns != null && !ns.isEmpty()) {
                        return ns;
                    }
                }
                n = n.getParent();
            }
        } catch (Exception e) {
            // fall through
        }
        return "";
    }

    private static final class AuthorWrapTarget implements WrapTarget {
        private final AuthorDocumentController ctrl;
        private final int start;
        private final int end;
        private final String ns;
        private final String text;

        AuthorWrapTarget(AuthorDocumentController ctrl, int start, int end, String ns, String text) {
            this.ctrl = ctrl;
            this.start = start;
            this.end = end;
            this.ns = ns;
            this.text = text;
        }

        public String selectedText() {
            return text;
        }

        public void wrap(String element, String attr, String value, Map<String, String> extra)
                throws Exception {
            String open = startTag(element, attr, value, extra); // <el …>
            if (ns != null && !ns.isEmpty()) {
                open = open.substring(0, open.length() - 1) + " xmlns=\"" + ns + "\">";
            }
            String fragment = open + "</" + element + ">";
            // getSelectionEnd() is exclusive (offset *after* the last selected char), but
            // surroundInFragment expects an inclusive end offset — hence end - 1, the same
            // idiom oXygen's own SurroundWithFragmentOperation uses. Without it the wrap
            // reaches one position too far right and the markup drifts off the selection.
            ctrl.surroundInFragment(fragment, start, end - 1);
        }
    }

    // --- Text mode ---------------------------------------------------------

    private static RefTarget locateText(WSTextEditorPage page, Map<String, String> mapping) {
        try {
            Document doc = page.getDocument();
            String text = doc.getText(0, doc.getLength());
            int caret = page.hasSelection() ? page.getSelectionStart() : page.getCaretOffset();

            // Find the nearest preceding start tag of any mapped element.
            int open = -1;
            String name = null;
            for (String n : mapping.keySet()) {
                int idx = lastStartTag(text, n, caret);
                if (idx > open) {
                    open = idx;
                    name = n;
                }
            }
            if (open < 0) {
                return null;
            }
            int close = text.indexOf('>', open);
            if (close < 0) {
                return null;
            }
            // Make sure the caret is actually inside this element (not after its close tag).
            if (caret > close) {
                int closeTag = indexOfCloseTag(text, name, close);
                if (closeTag >= 0 && caret > closeTag) {
                    return null; // caret is past the element -> not enclosed
                }
            }
            String register = mapping.get(name);
            if (register == null) {
                return null;
            }

            String selected = page.hasSelection() ? page.getSelectedText() : null;
            String inner = (selected != null && !selected.trim().isEmpty())
                    ? selected
                    : innerText(text, name, close);

            return new TextRefTarget(doc, open, name, register, collapse(inner));
        } catch (Exception e) {
            return null;
        }
    }

    /** Last index of a real start tag {@code <name} at or before {@code pos}. */
    private static int lastStartTag(String text, String name, int pos) {
        String token = "<" + name;
        int from = Math.min(pos, text.length() - 1);
        while (from >= 0) {
            int idx = text.lastIndexOf(token, from);
            if (idx < 0) {
                return -1;
            }
            int after = idx + token.length();
            char c = after < text.length() ? text.charAt(after) : ' ';
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '>' || c == '/') {
                return idx;
            }
            from = idx - 1; // false positive (e.g. <placeNameX) -> keep looking
        }
        return -1;
    }

    private static int indexOfCloseTag(String text, String name, int from) {
        Matcher m = Pattern.compile("</" + Pattern.quote(name) + "\\s*>").matcher(text);
        if (m.find(from)) {
            return m.start();
        }
        return -1;
    }

    private static String innerText(String text, String name, int close) {
        int end = indexOfCloseTag(text, name, close);
        if (end < 0) {
            end = Math.min(text.length(), close + 200);
        }
        return text.substring(close + 1, end).replaceAll("<[^>]*>", " ");
    }

    private static final class TextRefTarget implements RefTarget {
        private final Document doc;
        private final int tagStart;   // index of '<'
        private final String name;
        private final String register;
        private final String inner;

        TextRefTarget(Document doc, int tagStart, String name, String register, String inner) {
            this.doc = doc;
            this.tagStart = tagStart;
            this.name = name;
            this.register = register;
            this.inner = inner;
        }

        public String register() { return register; }
        public String elementName() { return name; }
        public String currentText() { return inner; }

        public String currentRef(String attr) {
            try {
                Matcher m = attrPattern(attr).matcher(liveTag());
                if (m.find()) {
                    String q = m.group(1);
                    return q.substring(1, q.length() - 1);
                }
            } catch (Exception e) {
                // ignore
            }
            return null;
        }

        /**
         * Re-reads the start tag from the live document (its length may have grown from a
         * previous attribute write) and sets/replaces {@code name="value"} in place.
         */
        public void setAttribute(String name, String value) throws Exception {
            String tag = liveTag();
            int close = tagEnd();
            String newTag = buildTag(tag, name, value);
            doc.remove(tagStart, close - tagStart + 1);
            doc.insertString(tagStart, newTag, null);
        }

        public void renameElementTo(String newName) throws Exception {
            if (newName == null || newName.equals(name)) {
                return;
            }
            String text = doc.getText(0, doc.getLength());
            int close = text.indexOf('>', tagStart);
            if (close < 0) {
                throw new Exception("Kein Tag-Ende gefunden.");
            }
            boolean selfClose = text.charAt(close - 1) == '/';
            // Rewrite the matching end tag first (it sits after the start tag, so editing the
            // start-tag name below does not shift the end-tag offset we computed here).
            if (!selfClose) {
                int endTag = indexOfCloseTag(text, name, close);
                if (endTag >= 0) {
                    int endClose = text.indexOf('>', endTag);
                    doc.remove(endTag, endClose - endTag + 1);
                    doc.insertString(endTag, "</" + newName + ">", null);
                }
            }
            // Then rename the element in the start tag: '<' + name -> '<' + newName.
            doc.remove(tagStart + 1, name.length());
            doc.insertString(tagStart + 1, newName, null);
        }

        public void removeAttribute(String attrName) throws Exception {
            String tag = liveTag();
            Matcher m = attrPattern(attrName).matcher(tag);
            if (!m.find()) {
                return;
            }
            int close = tagEnd();
            String newTag = tag.substring(0, m.start()) + tag.substring(m.end());
            doc.remove(tagStart, close - tagStart + 1);
            doc.insertString(tagStart, newTag, null);
        }

        private int tagEnd() throws Exception {
            String text = doc.getText(0, doc.getLength());
            int close = text.indexOf('>', tagStart);
            if (close < 0) {
                throw new Exception("Kein Tag-Ende gefunden.");
            }
            return close;
        }

        private String liveTag() throws Exception {
            String text = doc.getText(0, doc.getLength());
            int close = text.indexOf('>', tagStart);
            if (close < 0) {
                throw new Exception("Kein Tag-Ende gefunden.");
            }
            return text.substring(tagStart, close + 1);
        }

        private static Pattern attrPattern(String attr) {
            return Pattern.compile(
                    "(?s)\\s" + Pattern.quote(attr) + "\\s*=\\s*(\"[^\"]*\"|'[^']*')");
        }

        private static String buildTag(String startTag, String attr, String value) {
            boolean selfClose = startTag.endsWith("/>");
            String body = startTag.substring(1, selfClose ? startTag.length() - 2 : startTag.length() - 1);
            String attrText = " " + attr + "=\"" + value + "\"";
            Matcher m = attrPattern(attr).matcher(body);
            if (m.find()) {
                body = body.substring(0, m.start()) + attrText + body.substring(m.end());
            } else {
                Matcher nm = Pattern.compile("^(\\s*[\\w:.\\-]+)").matcher(body);
                if (nm.find()) {
                    body = body.substring(0, nm.end()) + attrText + body.substring(nm.end());
                } else {
                    body = body + attrText;
                }
            }
            return "<" + body + (selfClose ? "/>" : ">");
        }
    }

    private static WrapTarget selectionText(WSTextEditorPage page) {
        try {
            if (!page.hasSelection()) {
                return null;
            }
            int start = page.getSelectionStart();
            int end = page.getSelectionEnd();
            if (end <= start) {
                return null;
            }
            Document doc = page.getDocument();
            String sel = page.getSelectedText();
            if (sel == null) {
                sel = doc.getText(start, end - start);
            }
            if (sel.trim().isEmpty()) {
                return null;
            }
            return new TextWrapTarget(doc, start, end, collapse(sel));
        } catch (Exception e) {
            return null;
        }
    }

    private static final class TextWrapTarget implements WrapTarget {
        private final Document doc;
        private final int start;
        private final int end;
        private final String text;

        TextWrapTarget(Document doc, int start, int end, String text) {
            this.doc = doc;
            this.start = start;
            this.end = end;
            this.text = text;
        }

        public String selectedText() {
            return text;
        }

        public void wrap(String element, String attr, String value, Map<String, String> extra)
                throws Exception {
            // Insert the end tag first so the start offset stays valid.
            doc.insertString(end, "</" + element + ">", null);
            doc.insertString(start, startTag(element, attr, value, extra), null);
        }
    }

    // --- shared ------------------------------------------------------------

    private static String collapse(String s) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 80 ? s.substring(0, 80).trim() : s;
    }
}
