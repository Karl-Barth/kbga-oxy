package ch.kr.kbga.oxy;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * Checks every KBGA reference in the current document against the meta database and reports
 * which ids still resolve, which are missing (HTTP 404 — a broken reference) and which could
 * not be verified (network/other error). Resolution runs off the EDT with live progress.
 */
final class ReferenceCheckDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** One checked reference row. */
    private static final class Row {
        final References.Ref ref;
        final String state;   // "ok" | "missing" | "error"
        final String label;   // resolved label or error text
        Row(References.Ref ref, String state, String label) {
            this.ref = ref;
            this.state = state;
            this.label = label;
        }
        public String toString() {
            String badge = "ok".equals(state) ? "✓" : "missing".equals(state) ? "✗ FEHLT" : "? n/a";
            StringBuilder b = new StringBuilder();
            b.append(badge).append("  ").append(ref.fullId);
            if (ref.count > 1) {
                b.append("  (").append(ref.count).append("×)");
            }
            if (label != null && !label.isEmpty()) {
                b.append("  —  ").append(label);
            }
            return b.toString();
        }
    }

    private final Config config;
    private final DefaultListModel<Row> model = new DefaultListModel<Row>();
    private final JList<Row> list = new JList<Row>(model);
    private final JLabel status = new JLabel(" ");

    ReferenceCheckDialog(Window owner, final KbgaClient client, Config config,
                         final List<References.Ref> refs) {
        super(owner, "KBGA-Referenzen prüfen", ModalityType.APPLICATION_MODAL);
        this.config = config;

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(16);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton browse = new JButton("Im Browser öffnen");
        JButton close = new JButton("Schließen");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(browse);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 6));
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(new JLabel("  " + refs.size() + " verschiedene Referenzen ("
                + References.totalOccurrences(refs) + " Vorkommen)"), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        browse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { openSelected(); }
        });
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { dispose(); }
        });
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });

        setMinimumSize(new Dimension(680, 460));
        pack();
        setLocationRelativeTo(owner);

        status.setText("Prüfe 0/" + refs.size() + " …");
        new SwingWorker<Void, Row>() {
            private int ok, missing, error;
            protected Void doInBackground() {
                int done = 0;
                for (References.Ref r : refs) {
                    Row row;
                    try {
                        KbgaEntity e = client.resolve(r.register, r.id);
                        if (e == null) {
                            row = new Row(r, "missing", "existiert nicht mehr");
                        } else {
                            row = new Row(r, "ok", e.label);
                        }
                    } catch (Exception ex) {
                        row = new Row(r, "error", "nicht prüfbar: " + ex.getMessage());
                    }
                    publish(row);
                    setProgress(Math.min(100, (int) (100.0 * (++done) / refs.size())));
                }
                return null;
            }
            protected void process(List<Row> chunks) {
                for (Row row : chunks) {
                    model.addElement(row);
                    if ("ok".equals(row.state)) {
                        ok++;
                    } else if ("missing".equals(row.state)) {
                        missing++;
                    } else {
                        error++;
                    }
                }
                status.setText("Prüfe " + model.getSize() + "/" + refs.size()
                        + " …  (" + ok + " ok, " + missing + " fehlen, " + error + " n/a)");
            }
            protected void done() {
                status.setText("Fertig: " + ok + " ok, " + missing + " fehlen, "
                        + error + " nicht prüfbar.");
            }
        }.execute();
    }

    private void openSelected() {
        Row row = list.getSelectedValue();
        if (row != null) {
            Browser.open(config.entityUrl(row.ref.register, row.ref.id));
        }
    }

    /** Scan the editor, then show the check dialog (or an info if there is nothing to check). */
    static void run(Window owner, KbgaClient client, Config config, String documentXml) {
        List<References.Ref> refs = References.scan(documentXml);
        if (refs.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(owner,
                    "Keine KBGA-Referenzen im Dokument gefunden.", "KBGA-Referenzen prüfen",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new ReferenceCheckDialog(owner, client, config, refs).setVisible(true);
    }
}
