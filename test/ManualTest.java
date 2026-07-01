package ch.kr.kbga.oxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import java.util.HashMap;
import java.util.Map;

import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.editor.page.text.WSTextEditorPage;
import ro.sync.exml.workspace.api.options.WSOptionsStorage;

/**
 * Stand-alone sanity checks for the JSON parser, the register table and the Text-mode
 * attribute rewriting. Not a JUnit test (keeps the build dependency-free) — run via
 * test/run.sh.
 */
public class ManualTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testJson();
        testTextMode();
        testSelectionWrap();
        testRegisters();
        testElementFor();
        testReferences();
        testConfig();
        testRecent();
        testUrls();
        System.out.println(failures == 0 ? "\nALL TESTS PASSED" : "\n" + failures + " TEST(S) FAILED");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // --- JSON ---------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void testJson() {
        String body = "{\"data\":[{\"id\":10,\"full-id\":\"kbga-actors-10\","
                + "\"type\":\"K\\u00f6rperschaft\",\"name\":\"Sp\\u00fcli\","
                + "\"persName_full\":\"Cornelius Spielman\"}],\"meta\":{\"total\":1}}";
        Object root = Json.parse(body);
        java.util.Map m = (java.util.Map) root;
        java.util.List data = (java.util.List) m.get("data");
        java.util.Map a = (java.util.Map) data.get(0);
        check("json id", "10", String.valueOf(a.get("id")));
        check("json full-id", "kbga-actors-10", a.get("full-id"));
        check("json unicode", "Körperschaft", a.get("type"));
        check("json persName_full", "Cornelius Spielman", a.get("persName_full"));
    }

    // --- Text mode ----------------------------------------------------------

    private static void testTextMode() throws Exception {
        // 1) persName without ref -> actors register, @ref written
        writeRef("persName, no ref",
                "<p>Wie <persName>Cornelius Spilman</persName>, von</p>",
                "<persName>".length() + 4,
                "actors", "ref", "kbga-actors-10",
                "<persName ref=\"kbga-actors-10\">");

        // 2) persName with existing ref -> replaced
        writeRef("persName, replace ref",
                "<p>der <persName ref=\"kbga-actors-99\">Gouverneur</persName> kam</p>",
                25,
                "actors", "ref", "kbga-actors-17",
                "<persName ref=\"kbga-actors-17\">");

        // 3) placeName with other attributes preserved
        writeRef("placeName with attrs",
                "<p>nach <placeName n=\"1\" type=\"itinerar\">Basel</placeName> zu</p>",
                40,
                "places", "ref", "kbga-places-5",
                "<placeName ref=\"kbga-places-5\" n=\"1\" type=\"itinerar\">");

        // 4) bibl -> Literatur register, @corresp written
        writeRef("bibl -> corresp (Literatur)",
                "<p>vgl. <bibl>Barth, KD I/1</bibl>.</p>",
                "<p>vgl. <bibl>Ba".length(),
                "bibls", "corresp", "kbga-bibls-1",
                "<bibl corresp=\"kbga-bibls-1\">");

        // 5) bibl as Lied -> @corresp + type="song" (two setAttribute calls on the live tag)
        songRewrite();

        // 6) nested: caret inside <hi> within persName -> tags the persName
        writeRef("nested hi inside persName",
                "<p><persName>Herr <hi rend=\"latin\">Lobs</hi> kam</persName></p>",
                "<p><persName>Herr <hi rend=\"latin\">L".length(),
                "actors", "ref", "kbga-actors-18",
                "<persName ref=\"kbga-actors-18\">");

        // 7) caret outside any target -> no target located
        WSEditor ed = editor("<p>nur Text ohne Tag</p>", 8, -1);
        check("outside -> null target", "null", String.valueOf(RefTargets.locate(ed)));
    }

    private static void writeRef(String label, String xml, int caret, String expectRegister,
                                 String attr, String value, String expectStartTag) throws Exception {
        WSEditor ed = editor(xml, caret, -1);
        RefTargets.RefTarget t = RefTargets.locate(ed);
        if (t == null) {
            fail(label + ": no target located");
            return;
        }
        check(label + " register", expectRegister, t.register());
        t.setAttribute(attr, value);
        String after = lastDoc.getText(0, lastDoc.getLength());
        if (after.contains(expectStartTag)) {
            pass(label);
        } else {
            fail(label + "\n   expected tag: " + expectStartTag + "\n   result: " + after);
        }
    }

    /** A bibl switched to the Lieder register: corresp + type="song" set in sequence. */
    private static void songRewrite() throws Exception {
        WSEditor ed = editor("<p>Lied <bibl>Ach! stirbt denn</bibl>.</p>",
                "<p>Lied <bibl>Ac".length(), -1);
        RefTargets.RefTarget t = RefTargets.locate(ed);
        if (t == null) {
            fail("song: no target located");
            return;
        }
        Registers.Register songs = Registers.get("songs");
        t.setAttribute(songs.attribute, "kbga-songs-247");
        for (Map.Entry<String, String> e : songs.extraAttributes.entrySet()) {
            t.setAttribute(e.getKey(), e.getValue());
        }
        // Each new attribute is inserted right after the element name, so the final order is
        // type-then-corresp; assert on presence of both (order is XML-irrelevant).
        String after = lastDoc.getText(0, lastDoc.getLength());
        boolean ok = after.contains("<bibl ") && after.contains("corresp=\"kbga-songs-247\"")
                && after.contains("type=\"song\"");
        check("song corresp+type written", "true", String.valueOf(ok));
    }

    // --- Registers ----------------------------------------------------------

    private static void testRegisters() {
        check("actors attr", "ref", Registers.get("actors").attribute);
        check("places attr", "ref", Registers.get("places").attribute);
        check("bibls attr", "corresp", Registers.get("bibls").attribute);
        check("songs attr", "corresp", Registers.get("songs").attribute);
        check("songs extra type", "song", Registers.get("songs").extraAttributes.get("type"));
        check("actors no extra", "true", String.valueOf(Registers.get("actors").extraAttributes.isEmpty()));
        check("register count", "4", String.valueOf(Registers.all().size()));
        check("unknown falls back to actors", "actors", Registers.get("nope").key);
    }

    // --- Config: template / mapping -----------------------------------------

    private static void testConfig() throws Exception {
        Config def = new Config(store(new HashMap<String, String>()));
        check("default mapping persName", "actors", def.getMapping().get("persName"));
        check("default mapping bibl", "bibls", def.getMapping().get("bibl"));

        // template {fullId} (default)
        check("template fullId", "kbga-actors-10",
                def.formatRef(new KbgaEntity(10, "kbga-actors-10", "x", "", "", "actors")));

        // custom template using parsed placeholders
        Map<String, String> o = new HashMap<String, String>();
        o.put(Config.OPT_TEMPLATE, "#{slug}:{register}:{id}");
        Config tpl = new Config(store(o));
        check("template parsed", "#kbga:places:14",
                tpl.formatRef(new KbgaEntity(14, "kbga-places-14", "x", "", "", "places")));

        // custom mapping (unknown registers dropped), end to end in text mode
        Map<String, String> o2 = new HashMap<String, String>();
        o2.put(Config.OPT_MAPPING, "rs=actors\n# comment\nfoo=nonsense\nplaceName=places");
        Config cfg = new Config(store(o2));
        check("custom mapping rs", "actors", cfg.getMapping().get("rs"));
        check("custom mapping drops unknown", "false",
                String.valueOf(cfg.getMapping().containsKey("foo")));

        WSEditor ed = editor("<p>der <rs>Lobs</rs> kam</p>", 9, -1);
        RefTargets.RefTarget t = RefTargets.locate(ed, cfg.getMapping());
        if (t == null) {
            fail("custom element <rs> not located");
        } else {
            t.setAttribute("ref", cfg.formatRef(new KbgaEntity(18, "kbga-actors-18", "Lobs", "", "", "actors")));
            String after = lastDoc.getText(0, lastDoc.getLength());
            check("custom element written", "true",
                    String.valueOf(after.contains("<rs ref=\"kbga-actors-18\">")));
        }
    }

    /** Proxy-backed WSOptionsStorage: reads from and writes to the given (mutable) map. */
    private static WSOptionsStorage store(final Map<String, String> opts) {
        return (WSOptionsStorage) Proxy.newProxyInstance(
                ManualTest.class.getClassLoader(),
                new Class[] { WSOptionsStorage.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] a) {
                        if (method.getName().equals("getOption")) {
                            String k = (String) a[0];
                            return opts.containsKey(k) ? opts.get(k) : a[1];
                        }
                        if (method.getName().equals("setOption")) {
                            opts.put((String) a[0], (String) a[1]);
                            return null;
                        }
                        return null;
                    }
                });
    }

    // --- selection wrap (mark up a raw selection) ---------------------------

    private static void testSelectionWrap() throws Exception {
        // 1) plain selection -> wrapped in persName with @ref
        String xml = "<p>Brief von Karl Barth an Eduard</p>";
        int start = xml.indexOf("Karl Barth");
        int end = start + "Karl Barth".length();
        WSEditor ed = editor(xml, start, end);
        RefTargets.WrapTarget w = RefTargets.locateSelection(ed);
        check("wrap located", "true", String.valueOf(w != null));
        check("wrap selected text", "Karl Barth", w.selectedText());
        w.wrap("persName", "ref", "kbga-actors-1", java.util.Collections.<String, String>emptyMap());
        String after = lastDoc.getText(0, lastDoc.getLength());
        check("wrap persName", "true", String.valueOf(
                after.contains("<persName ref=\"kbga-actors-1\">Karl Barth</persName>")));

        // 2) selection wrapped as a song -> corresp + type="song"
        String xml2 = "<p>Lied Ach stirbt denn hier</p>";
        int s2 = xml2.indexOf("Ach stirbt denn");
        int e2 = s2 + "Ach stirbt denn".length();
        WSEditor ed2 = editor(xml2, s2, e2);
        RefTargets.WrapTarget w2 = RefTargets.locateSelection(ed2);
        w2.wrap("bibl", "corresp", "kbga-songs-9", Registers.get("songs").extraAttributes);
        String after2 = lastDoc.getText(0, lastDoc.getLength());
        check("wrap song", "true", String.valueOf(
                after2.contains("<bibl corresp=\"kbga-songs-9\" type=\"song\">Ach stirbt denn</bibl>")));

        // 3) no selection -> no wrap target
        WSEditor ed3 = editor("<p>ohne Auswahl</p>", 3, -1);
        check("no selection -> null wrap", "null", String.valueOf(RefTargets.locateSelection(ed3)));
    }

    // --- Registers.elementFor ----------------------------------------------

    private static void testElementFor() {
        check("elementFor person", "persName",
                Registers.elementFor(new KbgaEntity(1, "kbga-actors-1", "x", "Person", "", "actors")));
        check("elementFor organisation", "orgName",
                Registers.elementFor(new KbgaEntity(2, "kbga-actors-2", "x", "Körperschaft", "", "actors")));
        check("elementFor place", "placeName",
                Registers.elementFor(new KbgaEntity(3, "kbga-places-3", "x", "", "", "places")));
        check("elementFor song", "bibl",
                Registers.elementFor(new KbgaEntity(4, "kbga-songs-4", "x", "", "", "songs")));
        check("isOrganisation Verein", "true", String.valueOf(Registers.isOrganisation("Verein")));
        check("isOrganisation Person", "false", String.valueOf(Registers.isOrganisation("Person")));
    }

    // --- References.scan ----------------------------------------------------

    private static void testReferences() {
        String doc = "<p><persName ref=\"kbga-actors-10\">A</persName> und "
                + "<persName ref=\"kbga-actors-10\">A</persName>, "
                + "<placeName ref=\"kbga-places-5\">B</placeName>, "
                + "<bibl corresp=\"kbga-bibls-1\"/> <bibl corresp=\"kbga-songs-9\" type=\"song\"/></p>";
        java.util.List<References.Ref> refs = References.scan(doc);
        check("refs distinct count", "4", String.valueOf(refs.size()));
        check("refs total occurrences", "5", String.valueOf(References.totalOccurrences(refs)));
        check("refs first register", "actors", refs.get(0).register);
        check("refs first count", "2", String.valueOf(refs.get(0).count));
        check("refs song id", "9", String.valueOf(refs.get(3).id));
        check("empty doc -> no refs", "0", String.valueOf(References.scan("").size()));
    }

    // --- Config: recent picks (MRU) -----------------------------------------

    private static void testRecent() {
        Config c = new Config(store(new HashMap<String, String>()));
        check("recent empty initially", "0", String.valueOf(c.getRecent("places").size()));
        c.addRecent(new KbgaEntity(5, "kbga-places-5", "Basel", "Stadt", "CH", "places"));
        c.addRecent(new KbgaEntity(6, "kbga-places-6", "Bern", "Stadt", "CH", "places"));
        java.util.List<KbgaEntity> rec = c.getRecent("places");
        check("recent size", "2", String.valueOf(rec.size()));
        check("recent newest first", "kbga-places-6", rec.get(0).fullId);
        check("recent label round-trip", "Basel", rec.get(1).label);
        // re-adding an existing pick moves it to the front without duplicating
        c.addRecent(new KbgaEntity(5, "kbga-places-5", "Basel", "Stadt", "CH", "places"));
        java.util.List<KbgaEntity> rec2 = c.getRecent("places");
        check("recent dedup size", "2", String.valueOf(rec2.size()));
        check("recent moved to front", "kbga-places-5", rec2.get(0).fullId);
    }

    // --- Config: browser deep-links -----------------------------------------

    private static void testUrls() {
        Config c = new Config(store(new HashMap<String, String>()));
        check("portal search url", "https://meta.karl-barth.ch/actors?search=Barth",
                c.portalSearchUrl("actors", "Barth"));
        check("entity url", "https://meta.karl-barth.ch/places/5", c.entityUrl("places", 5));
    }

    // --- proxy plumbing -----------------------------------------------------

    private static Document lastDoc;

    private static WSEditor editor(String xml, final int caret, int selEnd) throws Exception {
        final PlainDocument doc = new PlainDocument();
        doc.insertString(0, xml, null);
        lastDoc = doc;
        final int selStart = caret;
        final boolean hasSel = selEnd >= 0;
        final int selectionEnd = selEnd;

        final WSTextEditorPage page = (WSTextEditorPage) Proxy.newProxyInstance(
                ManualTest.class.getClassLoader(),
                new Class[] { WSTextEditorPage.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] a) {
                        String n = method.getName();
                        if (n.equals("getDocument")) return doc;
                        if (n.equals("getCaretOffset")) return caret;
                        if (n.equals("getSelectionStart")) return selStart;
                        if (n.equals("getSelectionEnd")) return hasSel ? selectionEnd : selStart;
                        if (n.equals("hasSelection")) return hasSel;
                        if (n.equals("getSelectedText")) return null;
                        return defaultFor(method.getReturnType());
                    }
                });

        return (WSEditor) Proxy.newProxyInstance(
                ManualTest.class.getClassLoader(),
                new Class[] { WSEditor.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] a) {
                        if (method.getName().equals("getCurrentPage")) {
                            return page;
                        }
                        return defaultFor(method.getReturnType());
                    }
                });
    }

    private static Object defaultFor(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == double.class) return 0d;
        if (t == float.class) return 0f;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        return null;
    }

    // --- assertions ---------------------------------------------------------

    private static void check(String label, String expect, Object actual) {
        if (expect.equals(String.valueOf(actual))) {
            pass(label);
        } else {
            fail(label + " — expected <" + expect + "> got <" + actual + ">");
        }
    }

    private static void pass(String label) { System.out.println("  ok   " + label); }

    private static void fail(String label) { failures++; System.out.println("  FAIL " + label); }
}
