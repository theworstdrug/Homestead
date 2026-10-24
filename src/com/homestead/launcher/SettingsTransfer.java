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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings as text, for moving them between installs (MigrateProvider). One setting per
 * line: key, type and value separated by tabs. Types: b (boolean), i (int), l (long),
 * s (string), set (string set, members separated by U+001F). Backslash, tab and newline
 * inside a value are escaped as \\, \t and \n. Blank lines and lines starting with # are
 * skipped. No Android classes, so it is unit-tested on the JVM.
 */
final class SettingsTransfer {
    private static final char SEP = '\u001f';

    private SettingsTransfer() { }

    static LinkedHashMap<String, Object> parse(String text) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        String[] lines = text.split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", 3);
            if (f.length != 3 || f[0].isEmpty()) {
                throw new IllegalArgumentException("line " + (n + 1) + ": expected key, type and value");
            }
            out.put(f[0], value(f[1], f[2], n + 1));
        }
        return out;
    }

    private static Object value(String type, String raw, int line) {
        try {
            if ("b".equals(type)) {
                if (!"true".equals(raw) && !"false".equals(raw)) throw new NumberFormatException(raw);
                return Boolean.valueOf(raw);
            }
            if ("i".equals(type)) return Integer.valueOf(raw);
            if ("l".equals(type)) return Long.valueOf(raw);
            if ("s".equals(type)) return unescape(raw);
            if ("set".equals(type)) {
                Set<String> s = new HashSet<String>();
                if (raw.isEmpty()) return s;
                for (String m : raw.split(String.valueOf(SEP), -1)) s.add(unescape(m));
                return s;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("line " + line + ": bad " + type + " value \"" + raw + "\"");
        }
        throw new IllegalArgumentException("line " + line + ": unknown type \"" + type + "\"");
    }

    /** Sorted by key, so two exports of the same settings are identical. */
    static String format(Map<String, ?> all) {
        List<String> keys = new ArrayList<String>(all.keySet());
        Collections.sort(keys);
        StringBuilder b = new StringBuilder();
        for (String k : keys) {
            Object v = all.get(k);
            String type, text;
            if (v instanceof Boolean) { type = "b"; text = v.toString(); }
            else if (v instanceof Integer) { type = "i"; text = v.toString(); }
            else if (v instanceof Long) { type = "l"; text = v.toString(); }
            else if (v instanceof String) { type = "s"; text = escape((String) v); }
            else if (v instanceof Set) {
                type = "set";
                List<String> members = new ArrayList<String>();
                for (Object m : (Set<?>) v) members.add(escape(String.valueOf(m)));
                Collections.sort(members);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) sb.append(SEP);
                    sb.append(members.get(i));
                }
                text = sb.toString();
            } else {
                continue; // floats and nulls are not used by Homestead's settings
            }
            b.append(k).append('\t').append(type).append('\t').append(text).append('\n');
        }
        return b.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(++i);
                b.append(d == 't' ? '\t' : d == 'n' ? '\n' : d);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
