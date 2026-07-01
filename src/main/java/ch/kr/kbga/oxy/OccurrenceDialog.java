package ch.kr.kbga.oxy;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        for (Occurrences.Match m : matches) {
            JCheckBox cb = new JCheckBox(m.snippet, true);
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
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
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
        setMinimumSize(new Dimension(680, 420));
        pack();
        setLocationRelativeTo(owner);
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
