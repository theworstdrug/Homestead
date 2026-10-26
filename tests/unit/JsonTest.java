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

import java.util.List;
import java.util.Map;

final class JsonTest {
    private JsonTest() { }

    static void run() {
        parsesNested();
        parseRejectsMalformed();
        unicodeAndEscapes();
    }

    @SuppressWarnings("unchecked")
    static void parsesNested() {
        Map<String, Object> m = (Map<String, Object>) Json.parse(
                "{\"a\": [1, 2.5, -3e2], \"b\": {\"c\": true, \"d\": null}, \"e\": \"x\"}");
        List<Object> a = (List<Object>) m.get("a");
        T.eq(3, a.size(), "array size");
        T.eq(2.5, a.get(1), "double");
        T.eq(-300.0, a.get(2), "exponent");
        T.eq(Boolean.TRUE, ((Map<String, Object>) m.get("b")).get("c"), "nested boolean");
        T.yes(((Map<String, Object>) m.get("b")).containsKey("d"), "null kept as a key");
        T.eq("x", m.get("e"), "string");
    }

    static void parseRejectsMalformed() {
        for (final String bad : new String[]{"{\"a\":", "[1,,2]", "", "{\"a\" 1}", "[1] x", "\"open"}) {
            T.fails(IllegalArgumentException.class, new Runnable() {
                public void run() { Json.parse(bad); }
            }, "malformed: " + bad);
        }
    }

    static void unicodeAndEscapes() {
        T.eq("°F \"q\"\n", Json.parse("\"\\u00b0F \\\"q\\\"\\n\""), "escapes");
        T.eq("°F", Json.parse("\"°F\""), "raw UTF-8");
    }
}
