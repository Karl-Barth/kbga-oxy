package ch.kr.kbga.oxy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON parser (Java 8 compatible).
 *
 * <p>Only what is needed to read the KBGA {@code /api/actors}, {@code /api/places},
 * {@code /api/bibls} and {@code /api/songs} responses: objects, arrays, strings
 * (incl. {@code \\uXXXX} escapes), numbers, booleans and null. Keeps the plugin a
 * single self-contained jar with no external dependencies to download at build time.</p>
 */
final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Parse a JSON document into Map / List / String / Long / Double / Boolean / null. */
    static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        return j.value();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private Object value() {
        ws();
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default:  return num();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++; // {
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws();
            i++; // :
            Object v = value();
            m.put(k, v);
            ws();
            char c = s.charAt(i++);
            if (c == '}') break;
            // otherwise ','
        }
        return m;
    }

    private List<Object> arr() {
        List<Object> a = new ArrayList<Object>();
        i++; // [
        ws();
        if (s.charAt(i) == ']') { i++; return a; }
        while (true) {
            a.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') break;
            // otherwise ','
        }
        return a;
    }

    private String str() {
        StringBuilder b = new StringBuilder();
        i++; // opening quote
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"':  b.append('"');  break;
                    case '\\': b.append('\\'); break;
                    case '/':  b.append('/');  break;
                    case 'b':  b.append('\b'); break;
                    case 'f':  b.append('\f'); break;
                    case 'n':  b.append('\n'); break;
                    case 'r':  b.append('\r'); break;
                    case 't':  b.append('\t'); break;
                    case 'u':
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:   b.append(e);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private Object num() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        String n = s.substring(start, i);
        if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) {
            return Double.parseDouble(n);
        }
        return Long.parseLong(n);
    }
}
