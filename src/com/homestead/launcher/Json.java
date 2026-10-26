/*
 * Homestead - a replacement home screen for Fire TV.
 * Copyright (C) 2026 Homestead contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. It is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.homestead.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON reader for the weather replies: objects become Maps, arrays Lists,
 * numbers Doubles. Anything malformed throws IllegalArgumentException. Android's org.json
 * is not on the JVM test classpath, hence this one.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("no text");
        Json j = new Json(text);
        Object v = j.value();
        j.space();
        if (j.i != j.s.length()) throw j.error("trailing text");
        return v;
    }

    private Object value() {
        space();
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') return word("true", Boolean.TRUE);
        if (c == 'f') return word("false", Boolean.FALSE);
        if (c == 'n') return word("null", null);
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw error("unexpected '" + c + "'");
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++;
        space();
        if (peek('}')) { i++; return m; }
        while (true) {
            space();
            if (!peek('"')) throw error("expected a key");
            String k = string();
            space();
            expect(':');
            m.put(k, value());
            space();
            if (peek(',')) { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<Object>();
        i++;
        space();
        if (peek(']')) { i++; return l; }
        while (true) {
            l.add(value());
            space();
            if (peek(',')) { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw error("short \\u escape");
                    try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                    catch (NumberFormatException x) { throw error("bad \\u escape"); }
                    i += 4;
                    break;
                default: b.append(e);
            }
        }
        throw error("unterminated string");
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        try { return Double.valueOf(s.substring(start, i)); }
        catch (NumberFormatException x) { throw error("bad number"); }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) throw error("expected " + w);
        i += w.length();
        return v;
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

    private void expect(char c) {
        if (!peek(c)) throw error("expected '" + c + "'");
        i++;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON at " + i + ": " + what);
    }
}
