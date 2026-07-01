package ch.kr.kbga.oxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Thin HTTP client for the KBGA meta database search API.
 *
 * <p>Performs a live {@code GET {base}/api/{register}?format=json&amp;perPage=..&amp;search=..}
 * for every query — it never bulk-downloads a whole register. The search endpoints are
 * public, so no authentication is sent.</p>
 */
final class KbgaClient {

    private final Config config;

    KbgaClient(Config config) {
        this.config = config;
    }

    /**
     * @param register "actors" | "places" | "bibls" | "songs"
     * @param query    free-text search term
     */
    List<KbgaEntity> search(String register, String query) throws IOException {
        String url = config.getBaseUrl()
                + "/api/" + register
                + "?format=json"
                + "&perPage=" + config.getPerPage()
                + "&search=" + enc(query);

        HttpURLConnection con = open(url);
        try {
            con.setRequestMethod("GET");
            con.setRequestProperty("Accept", "application/json");
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);

            int code = con.getResponseCode();
            // The places endpoint answers 404 when nothing matches — treat that as "no hits".
            if (code == 404) {
                return new ArrayList<KbgaEntity>();
            }
            InputStream is = (code >= 200 && code < 400) ? con.getInputStream() : con.getErrorStream();
            String body = read(is);
            if (code < 200 || code >= 300) {
                throw new IOException("KBGA HTTP " + code + " für " + url + "\n" + shorten(body));
            }
            return parse(register, body);
        } finally {
            con.disconnect();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<KbgaEntity> parse(String register, String body) {
        List<KbgaEntity> out = new ArrayList<KbgaEntity>();
        Object root = Json.parse(body);
        if (!(root instanceof Map)) {
            return out;
        }
        Object data = ((Map) root).get("data");
        if (!(data instanceof List)) {
            return out;
        }
        for (Object o : (List) data) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map m = (Map) o;
            String fullId = str(m.get("full-id"));
            if (fullId == null || fullId.isEmpty()) {
                continue;
            }
            long id = 0L;
            Object idO = m.get("id");
            if (idO instanceof Number) {
                id = ((Number) idO).longValue();
            }
            out.add(new KbgaEntity(id, fullId, label(register, m), type(register, m),
                    detail(register, m), register));
        }
        return out;
    }

    // --- per-register field extraction ------------------------------------

    @SuppressWarnings("rawtypes")
    private String label(String register, Map m) {
        if ("actors".equals(register)) {
            return firstNonEmpty(m, "persName_full", "name");
        }
        if ("places".equals(register)) {
            return firstNonEmpty(m, "placeName_full", "name");
        }
        // bibls & songs both carry a ready-made citation string.
        String s = firstNonEmpty(m, "asString");
        if (!s.isEmpty()) {
            return s;
        }
        return "songs".equals(register) ? str0(m.get("title")) : "";
    }

    @SuppressWarnings("rawtypes")
    private String type(String register, Map m) {
        if ("actors".equals(register)) {
            return firstNonEmpty(m, "type", "authority_type");
        }
        if ("places".equals(register)) {
            return firstNonEmpty(m, "place_type");
        }
        if ("bibls".equals(register)) {
            return firstNonEmpty(m, "type");
        }
        return ""; // songs
    }

    @SuppressWarnings("rawtypes")
    private String detail(String register, Map m) {
        if ("actors".equals(register)) {
            return years(str(m.get("birth")), str(m.get("death")));
        }
        if ("places".equals(register)) {
            StringBuilder b = new StringBuilder();
            appendPart(b, str(m.get("city")));
            appendPart(b, str(m.get("state")));
            appendPart(b, str(m.get("country")));
            return b.toString();
        }
        // bibls & songs: the asString label already carries authors, title, place and year,
        // so no extra detail line is needed.
        return "";
    }

    /** "1886–1968", "*1886", "†1968" or "" — drops the placeholder 0000-00-00 dates. */
    private String years(String birth, String death) {
        String b = year(birth);
        String d = year(death);
        if (!b.isEmpty() && !d.isEmpty()) {
            return b + "–" + d;
        }
        if (!b.isEmpty()) {
            return "*" + b;
        }
        if (!d.isEmpty()) {
            return "†" + d;
        }
        return "";
    }

    private String year(String date) {
        if (date == null) {
            return "";
        }
        int dash = date.indexOf('-');
        String y = dash > 0 ? date.substring(0, dash) : date;
        return y.matches("0*") ? "" : y;
    }

    @SuppressWarnings("rawtypes")
    private String firstNonEmpty(Map m, String... keys) {
        for (String k : keys) {
            String v = str(m.get(k));
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private void appendPart(StringBuilder b, String part) {
        if (part != null && !part.isEmpty()) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(part);
        }
    }

    // --- low level helpers -------------------------------------------------

    private HttpURLConnection open(String url) throws IOException {
        URLConnection c = new URL(url).openConnection();
        if (c instanceof HttpsURLConnection && config.isInsecureTls()) {
            applyInsecure((HttpsURLConnection) c);
        }
        return (HttpURLConnection) c;
    }

    /** Trust-all TLS for this single connection only (used for local mkcert/DDEV hosts). */
    private void applyInsecure(HttpsURLConnection c) {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            } }, new SecureRandom());
            c.setSSLSocketFactory(sc.getSocketFactory());
            c.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) { return true; }
            });
        } catch (Exception e) {
            // fall back to default (strict) TLS
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String str0(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return s.length() > 240 ? s.substring(0, 240) + "…" : s;
    }
}
