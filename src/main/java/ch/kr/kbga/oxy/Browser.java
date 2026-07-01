package ch.kr.kbga.oxy;

import java.awt.Desktop;
import java.net.URI;

/** Opens a URL in the system browser, best-effort (used for KBGA portal deep-links). */
final class Browser {

    private Browser() { }

    /** @return true if the URL was handed to the system browser. */
    static boolean open(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                    return true;
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return false;
    }
}
