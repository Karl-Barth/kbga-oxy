package ch.kr.kbga.oxy;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Modal dialog that searches the KBGA meta database live (one HTTP request per query,
 * debounced) and lets the editor pick an entity. The register can be switched at any
 * time (Akteure / Orte / Literatur / Lieder); it is pre-selected from the element under
 * the caret. The chosen {@link KbgaEntity} carries the finally selected register, which
 * drives the attribute the plugin writes.
 */
final class SearchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final KbgaClient client;
    private final Config config;

    private final JComboBox<Registers.Register> registerCombo;
    private final JTextField searchField;
    private final DefaultListModel<KbgaEntity> model = new DefaultListModel<KbgaEntity>();
    private final JList<KbgaEntity> resultList = new JList<KbgaEntity>(model);
    private final JLabel status = new JLabel(" ");
    private final JButton okButton = new JButton("Einfügen");
    private final JButton browseButton = new JButton("Im Browser…");

    private final Timer debounce;
    private SwingWorker<List<KbgaEntity>, Void> running;
    private long querySeq = 0;

    private KbgaEntity result; // null = cancelled

    SearchDialog(Window owner, KbgaClient client, Config config,
                 String initialRegister, String elementName, String prefill, String currentRef,
                 boolean creating) {
        super(owner, "KBGA-Referenz einfügen", ModalityType.APPLICATION_MODAL);
        this.client = client;
        this.config = config;

        registerCombo = new JComboBox<Registers.Register>(
                Registers.all().toArray(new Registers.Register[0]));
        selectRegister(initialRegister);

        searchField = new JTextField(prefill == null ? "" : prefill, 32);

        // --- north: context + register + search field ---
        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        StringBuilder ctx = new StringBuilder("<html>");
        if (creating) {
            ctx.append("Auswahl <b>„").append(escape(prefill == null ? "" : prefill))
               .append("“</b> auszeichnen &amp; referenzieren &nbsp;·&nbsp; "
                       + "Register wählen — das Element wird neu erzeugt");
        } else {
            ctx.append("Element <b>&lt;").append(elementName).append("&gt;</b>");
            if (currentRef != null && !currentRef.isEmpty()) {
                ctx.append(" &nbsp;·&nbsp; aktuell: <code>").append(escape(currentRef)).append("</code>");
            }
        }
        ctx.append("</html>");
        north.add(new JLabel(ctx.toString()), BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(registerCombo, BorderLayout.WEST);
        row.add(searchField, BorderLayout.CENTER);
        north.add(row, BorderLayout.SOUTH);

        // --- center: results ---
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setVisibleRowCount(12);
        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        // --- south: status + buttons ---
        JButton settings = new JButton("Einstellungen…");
        JButton cancel = new JButton("Abbrechen");

        browseButton.setToolTipText(
                "Gewählten Eintrag im KBGA-Portal öffnen — oder (ohne Treffer) im Portal suchen/anlegen");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(settings);
        buttons.add(browseButton);
        buttons.add(cancel);
        buttons.add(okButton);

        JPanel south = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 6));
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        okButton.setEnabled(false);

        // --- behaviour ---
        debounce = new Timer(300, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runSearch();
            }
        });
        debounce.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });

        registerCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { runSearch(); }
        });

        searchField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    debounce.stop();
                    runSearch();
                    if (!model.isEmpty()) {
                        resultList.requestFocusInWindow();
                        resultList.setSelectedIndex(0);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN && !model.isEmpty()) {
                    resultList.requestFocusInWindow();
                    resultList.setSelectedIndex(0);
                }
            }
        });

        resultList.addListSelectionListener(e -> {
            okButton.setEnabled(resultList.getSelectedValue() != null);
            updateBrowseButton();
        });

        browseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { openInBrowser(); }
        });

        resultList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && resultList.getSelectedValue() != null) {
                    accept();
                }
            }
        });

        resultList.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && resultList.getSelectedValue() != null) {
                    accept();
                }
            }
        });

        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { accept(); }
        });
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { result = null; dispose(); }
        });
        settings.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (SettingsDialog.edit(SearchDialog.this, config)) {
                    runSearch();
                }
            }
        });

        getRootPane().setDefaultButton(okButton);
        setMinimumSize(new Dimension(640, 440));
        pack();
        setLocationRelativeTo(owner);

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                searchField.requestFocusInWindow();
                searchField.selectAll();
                runSearch(); // prefill → live search; empty → recently used picks
            }
        });
    }

    /** @return the chosen entity (with its register set), or null if cancelled. */
    KbgaEntity showDialog() {
        setVisible(true);
        return result;
    }

    private void accept() {
        KbgaEntity sel = resultList.getSelectedValue();
        if (sel != null) {
            result = sel;
            config.addRecent(sel);
            dispose();
        }
    }

    private void updateBrowseButton() {
        boolean hasSel = resultList.getSelectedValue() != null;
        boolean hasQuery = searchField.getText().trim().length() > 0;
        browseButton.setEnabled(hasSel || hasQuery);
    }

    /** Open the selected entity's portal page, or (nothing selected) a portal search. */
    private void openInBrowser() {
        KbgaEntity sel = resultList.getSelectedValue();
        String url = (sel != null)
                ? config.entityUrl(sel.register, sel.id)
                : config.portalSearchUrl(currentRegister(), searchField.getText().trim());
        if (!Browser.open(url)) {
            status.setText("Konnte Browser nicht öffnen: " + url);
        }
    }

    /** True for the bibl-based registers, which are searched together (Literatur + Lieder). */
    private static boolean isBiblFamily(String register) {
        return "bibls".equals(register) || "songs".equals(register);
    }

    private String currentRegister() {
        Registers.Register r = (Registers.Register) registerCombo.getSelectedItem();
        return r == null ? "actors" : r.key;
    }

    private void selectRegister(String register) {
        for (int i = 0; i < registerCombo.getItemCount(); i++) {
            if (registerCombo.getItemAt(i).key.equals(register)) {
                registerCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void runSearch() {
        final String query = searchField.getText().trim();
        final String register = currentRegister();
        if (running != null) {
            running.cancel(true);
        }
        if (query.isEmpty()) {
            showRecent(register);
            return;
        }
        final boolean biblFamily = isBiblFamily(register);
        final long seq = ++querySeq;
        status.setText("Suche „" + query + "“ …");
        running = new SwingWorker<List<KbgaEntity>, Void>() {
            protected List<KbgaEntity> doInBackground() throws Exception {
                if (biblFamily) {
                    // Literatur and Lieder both live in <bibl>; search both so the editor
                    // never has to know which register a title belongs to.
                    List<KbgaEntity> merged = new ArrayList<KbgaEntity>();
                    merged.addAll(client.search("bibls", query));
                    merged.addAll(client.search("songs", query));
                    return merged;
                }
                return client.search(register, query);
            }
            protected void done() {
                if (seq != querySeq || isCancelled()) {
                    return; // a newer query superseded this one
                }
                try {
                    List<KbgaEntity> hits = get();
                    model.clear();
                    for (KbgaEntity e : hits) {
                        model.addElement(e);
                    }
                    if (hits.isEmpty()) {
                        status.setText("Keine Treffer für „" + query
                                + "“ — „Im Browser…“ öffnet die Portalsuche zum Anlegen.");
                    } else {
                        int limit = biblFamily ? config.getPerPage() * 2 : config.getPerPage();
                        status.setText(hits.size() + " Treffer"
                                + (biblFamily ? " (Literatur + Lieder)" : "")
                                + (hits.size() >= limit ? " (ggf. mehr — Suche verfeinern)" : ""));
                        resultList.setSelectedIndex(0);
                    }
                    okButton.setEnabled(resultList.getSelectedValue() != null);
                    updateBrowseButton();
                } catch (Exception ex) {
                    model.clear();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("<html><font color='red'>Fehler: "
                            + escape(cause.getMessage()) + "</font></html>");
                }
            }
        };
        running.execute();
    }

    /** With an empty search field, offer the recently used picks for the current register. */
    private void showRecent(String register) {
        model.clear();
        List<KbgaEntity> recent;
        if (isBiblFamily(register)) {
            recent = new ArrayList<KbgaEntity>();
            recent.addAll(config.getRecent("bibls"));
            recent.addAll(config.getRecent("songs"));
        } else {
            recent = config.getRecent(register);
        }
        for (KbgaEntity e : recent) {
            model.addElement(e);
        }
        if (recent.isEmpty()) {
            status.setText("Suchbegriff eingeben…");
            okButton.setEnabled(false);
        } else {
            status.setText("Zuletzt verwendet (" + recent.size() + ") — oder Suchbegriff eingeben…");
            resultList.setSelectedIndex(0);
            okButton.setEnabled(true);
        }
        updateBrowseButton();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
