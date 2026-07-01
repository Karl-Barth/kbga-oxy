package ch.kr.kbga.oxy;

import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension;
import ro.sync.exml.workspace.api.PluginWorkspace;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.standalone.MenuBarCustomizer;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
import ro.sync.exml.workspace.api.standalone.ToolbarComponentsCustomizer;
import ro.sync.exml.workspace.api.standalone.ToolbarInfo;

/**
 * Adds the "KBGA-Referenz einfügen" action to a toolbar and to a "KBGA" menu.
 *
 * <p>Workflow: put the caret inside a {@code persName}, {@code orgName}, {@code placeName}
 * or {@code bibl} (Text <i>or</i> Author mode), trigger the action, search the KBGA meta
 * database live, pick an entry, and the plugin writes the id into the right attribute —
 * {@code ref="kbga-actors-…"} / {@code ref="kbga-places-…"} for Akteure/Orte,
 * {@code corresp="kbga-bibls-…"} for Literatur, and {@code corresp="kbga-songs-…"} plus
 * {@code type="song"} for Lieder.</p>
 */
public class KbgaOxyPluginExtension
        implements WorkspaceAccessPluginExtension, ToolbarComponentsCustomizer, MenuBarCustomizer {

    static final String TOOLBAR_ID = "KbgaOxyToolbarID";
    private static final String ACTION_LABEL = "KBGA-Referenz einfügen";

    private StandalonePluginWorkspace workspace;
    private Config config;
    private KbgaClient client;

    public void applicationStarted(StandalonePluginWorkspace pluginWorkspaceAccess) {
        this.workspace = pluginWorkspaceAccess;
        this.config = new Config(pluginWorkspaceAccess.getOptionsStorage());
        this.client = new KbgaClient(config);
        pluginWorkspaceAccess.addToolbarComponentsCustomizer(this);
        pluginWorkspaceAccess.addMenuBarCustomizer(this);
    }

    public boolean applicationClosing() {
        return true;
    }

    // --- toolbar -----------------------------------------------------------

    public void customizeToolbar(ToolbarInfo toolbarInfo) {
        if (!TOOLBAR_ID.equals(toolbarInfo.getToolbarID())) {
            return;
        }
        JButton button = new JButton("KBGA @ref");
        button.setToolTipText("Akteur/Ort/Literatur/Lied in der KBGA suchen und Referenz einfügen");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                insertReference();
            }
        });
        toolbarInfo.setComponents(new JComponent[] { button });
    }

    // --- menu --------------------------------------------------------------

    public void customizeMainMenu(JMenuBar mainMenuBar) {
        JMenu menu = new JMenu("KBGA");
        menu.setMnemonic('K');

        JMenuItem insert = new JMenuItem(ACTION_LABEL + " …");
        insert.setAccelerator(KeyStroke.getKeyStroke("control shift K"));
        insert.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                insertReference();
            }
        });

        JMenuItem check = new JMenuItem("KBGA-Referenzen prüfen …");
        check.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                validateReferences();
            }
        });

        JMenuItem settings = new JMenuItem("KBGA-Einstellungen …");
        settings.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SettingsDialog.edit(activeWindow(), config);
            }
        });

        menu.add(insert);
        menu.add(check);
        menu.addSeparator();
        menu.add(settings);

        // Insert before the "Help" menu if present, otherwise append.
        int idx = mainMenuBar.getMenuCount();
        for (int i = 0; i < mainMenuBar.getMenuCount(); i++) {
            JMenu m = mainMenuBar.getMenu(i);
            if (m != null && ("Help".equals(m.getText()) || "Hilfe".equals(m.getText()))) {
                idx = i;
                break;
            }
        }
        mainMenuBar.add(menu, idx);
    }

    // --- the action --------------------------------------------------------

    private void insertReference() {
        WSEditor editor = workspace.getCurrentEditorAccess(PluginWorkspace.MAIN_EDITING_AREA);
        if (editor == null) {
            info("Kein XML-Dokument geöffnet.");
            return;
        }
        final Map<String, String> mapping = config.getMapping();
        final RefTargets.RefTarget target = RefTargets.locate(editor, mapping);
        if (target == null) {
            // No mapped element under the caret — fall back to marking up a raw selection.
            RefTargets.WrapTarget wrap = RefTargets.locateSelection(editor);
            if (wrap == null) {
                info("Cursor in ein konfiguriertes Element setzen ("
                        + String.join(", ", mapping.keySet()) + "),\n"
                        + "oder Text markieren, um ihn auszuzeichnen und zu referenzieren.");
                return;
            }
            insertIntoSelection(editor, wrap);
            return;
        }

        String defaultAttr = Registers.get(target.register()).attribute;
        KbgaEntity chosen = new SearchDialog(
                activeWindow(), client, config,
                target.register(), target.elementName(),
                target.currentText(), target.currentRef(defaultAttr), false
        ).showDialog();

        if (chosen == null) {
            return; // cancelled
        }
        try {
            Registers.Register reg = Registers.get(chosen.register);
            target.setAttribute(reg.attribute, config.formatRef(chosen));
            for (Map.Entry<String, String> extra : reg.extraAttributes.entrySet()) {
                target.setAttribute(extra.getKey(), extra.getValue());
            }
        } catch (Exception ex) {
            error("Konnte Referenz nicht setzen: " + ex.getMessage());
            return;
        }
        maybeTagFurther(editor, chosen, target.currentText());
    }

    /** Mark up a raw text selection: pick a register, then wrap it in the right TEI element. */
    private void insertIntoSelection(WSEditor editor, RefTargets.WrapTarget wrap) {
        KbgaEntity chosen = new SearchDialog(
                activeWindow(), client, config,
                "actors", "", wrap.selectedText(), null, true
        ).showDialog();

        if (chosen == null) {
            return; // cancelled
        }
        try {
            Registers.Register reg = Registers.get(chosen.register);
            String element = Registers.elementFor(chosen);
            wrap.wrap(element, reg.attribute, config.formatRef(chosen), reg.extraAttributes);
        } catch (Exception ex) {
            error("Konnte Auszeichnung nicht setzen: " + ex.getMessage());
            return;
        }
        maybeTagFurther(editor, chosen, wrap.selectedText());
    }

    /**
     * Offer to tag further occurrences of the just-referenced actor/place in the document
     * (Text mode only — attribute-bearing tags are rewritten by offset on the text buffer).
     */
    private void maybeTagFurther(WSEditor editor, KbgaEntity chosen, String markedText) {
        if (!config.isScanOccurrences()) {
            return;
        }
        if (!"actors".equals(chosen.register) && !"places".equals(chosen.register)) {
            return; // occurrence search only makes sense for names, not literature/songs
        }
        javax.swing.text.Document doc = RefTargets.textDocument(editor);
        if (doc == null) {
            return; // not a Text page — skip silently
        }
        String xml;
        try {
            xml = doc.getText(0, doc.getLength());
        } catch (Exception e) {
            return;
        }
        java.util.List<String> terms = Occurrences.terms(chosen, markedText);
        if (terms.isEmpty()) {
            return;
        }
        java.util.List<Occurrences.Match> matches =
                Occurrences.find(xml, config.getMapping().keySet(), terms);
        if (matches.isEmpty()) {
            return;
        }
        String label = (chosen.label != null && !chosen.label.isEmpty()) ? chosen.label : markedText;
        java.util.List<Occurrences.Match> picked = OccurrenceDialog.choose(
                activeWindow(), label, Registers.elementFor(chosen), matches);
        if (picked == null || picked.isEmpty()) {
            return;
        }
        applyOccurrences(doc, picked, chosen);
    }

    /** Wrap the chosen occurrences, working from the end so earlier offsets stay valid. */
    private void applyOccurrences(javax.swing.text.Document doc,
                                  java.util.List<Occurrences.Match> matches, KbgaEntity chosen) {
        Registers.Register reg = Registers.get(chosen.register);
        String element = Registers.elementFor(chosen);
        String value = config.formatRef(chosen);
        java.util.List<Occurrences.Match> sorted =
                new java.util.ArrayList<Occurrences.Match>(matches);
        sorted.sort((a, b) -> b.start - a.start);
        int done = 0;
        for (Occurrences.Match m : sorted) {
            try {
                RefTargets.wrapRange(doc, m.start, m.end, element, reg.attribute, value,
                        reg.extraAttributes);
                done++;
            } catch (Exception ex) {
                // skip this occurrence, keep going
            }
        }
        if (done > 0) {
            info(done + (done == 1 ? " weiteres Vorkommen" : " weitere Vorkommen") + " ausgezeichnet.");
        }
    }

    /** Check every KBGA reference in the current document against the meta database. */
    private void validateReferences() {
        WSEditor editor = workspace.getCurrentEditorAccess(PluginWorkspace.MAIN_EDITING_AREA);
        if (editor == null) {
            info("Kein XML-Dokument geöffnet.");
            return;
        }
        String xml = RefTargets.documentText(editor);
        if (xml == null) {
            info("Für die Referenzprüfung bitte in den Text-Modus wechseln\n"
                    + "(Reiter „Text“ unten am Editor) und die Aktion erneut auslösen.");
            return;
        }
        ReferenceCheckDialog.run(activeWindow(), client, config, xml);
    }

    // --- ui helpers --------------------------------------------------------

    private Window activeWindow() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(activeWindow(), msg, "KBGA-Referenz",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(activeWindow(), msg, "KBGA-Referenz",
                JOptionPane.ERROR_MESSAGE);
    }
}
