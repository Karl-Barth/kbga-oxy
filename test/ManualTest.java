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
        testRegisters();
        testConfig();
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

    /** Proxy-backed WSOptionsStorage returning the given overrides, else the supplied default. */
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
                        return null;
                    }
                });
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
