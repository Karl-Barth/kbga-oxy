package ch.kr.kbga.oxy;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Lets the editor pick which further occurrences of a just-referenced entity should also be
 * tagged. Each occurrence is shown with its context; the base name to be wrapped is marked
 * with «…». Returns the selected matches, or {@code null} if the editor cancels.
 */
final class OccurrenceDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final List<Occurrences.Match> matches;
    private final List<JCheckBox> boxes = new ArrayList<JCheckBox>();
    private final JLabel countLabel = new JLabel();
    private boolean confirmed;

    private OccurrenceDialog(Window owner, String entityLabel, String element,
                             List<Occurrences.Match> matches) {
        super(owner, "Weitere Vorkommen auszeichnen", ModalityType.APPLICATION_MODAL);
        this.matches = matches;

        JLabel header = new JLabel("<html>" + matches.size() + " weitere Vorkommen von <b>"
                + escape(entityLabel) + "</b> gefunden.<br/>Ausgewählte werden als <code>&lt;"
                + escape(element) + "&gt;</code> mit derselben Referenz ausgezeichnet "
                + "(Genitiv-Endung bleibt außerhalb).</html>");
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        ItemListener recount = new ItemListener() {
            public void itemStateChanged(ItemEvent e) { updateCount(); }
        };
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        for (Occurrences.Match m : matches) {
            JCheckBox cb = new JCheckBox(htmlRow(m.snippet), true);
            cb.addItemListener(recount);
            boxes.add(cb);
            listPanel.add(cb);
        }
        JScrollPane scroll = new JScrollPane(listPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JButton all = new JButton("Alle");
        JButton none = new JButton("Keine");
        JButton ok = new JButton("Auszeichnen");
        JButton cancel = new JButton("Abbrechen");

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        left.add(all);
        left.add(none);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        right.add(cancel);
        right.add(ok);
        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
        buttons.add(countLabel, BorderLayout.CENTER);
        buttons.add(right, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        all.addActionListener(setAll(true));
        none.addActionListener(setAll(false));
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { confirmed = true; dispose(); }
        });
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { confirmed = false; dispose(); }
        });

        getRootPane().setDefaultButton(ok);
        updateCount();
        setMinimumSize(new Dimension(680, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    private void updateCount() {
        int sel = 0;
        for (JCheckBox cb : boxes) {
            if (cb.isSelected()) {
                sel++;
            }
        }
        countLabel.setText(sel + " / " + boxes.size() + " ausgewählt");
    }

    /** Render one preview row with the base name (inside «…») set in bold, context normal. */
    static String htmlRow(String snippet) {
        if (snippet == null) {
            return "";
        }
        int l = snippet.indexOf('«');
        int r = snippet.indexOf('»');
        if (l < 0 || r < 0 || r < l) {
            return "<html>" + escape(snippet) + "</html>";
        }
        String before = snippet.substring(0, l);
        String base = snippet.substring(l + 1, r);
        String after = snippet.substring(r + 1);
        return "<html>" + escape(before) + "<b>" + escape(base) + "</b>"
                + escape(after) + "</html>";
    }

    private ActionListener setAll(final boolean value) {
        return new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for (JCheckBox cb : boxes) {
                    cb.setSelected(value);
                }
            }
        };
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Show the dialog and return the occurrences the editor chose to tag, or {@code null}
     * if cancelled (empty list means confirmed but nothing selected).
     */
    static List<Occurrences.Match> choose(Window owner, String entityLabel, String element,
                                          List<Occurrences.Match> matches) {
        // show occurrences in document order so the list reads top-to-bottom like the text.
        List<Occurrences.Match> ordered = new ArrayList<Occurrences.Match>(matches);
        ordered.sort((a, b) -> a.start - b.start);
        matches = ordered;
        OccurrenceDialog d = new OccurrenceDialog(owner, entityLabel, element, matches);
        d.setVisible(true);
        if (!d.confirmed) {
            return null;
        }
        List<Occurrences.Match> chosen = new ArrayList<Occurrences.Match>();
        for (int i = 0; i < matches.size(); i++) {
            if (d.boxes.get(i).isSelected()) {
                chosen.add(matches.get(i));
            }
        }
        return chosen;
    }
}
