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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class SettingsTransferTest {
    private SettingsTransferTest() { }

    static void run() {
        parsesEveryType();
        roundTrips();
        rejectsBadType();
        ignoresCommentsAndBlanks();
    }

    static void parsesEveryType() {
        Map<String, Object> m = SettingsTransfer.parse(
                "hidden\tset\tapp:a\u001fapp:b\n"
                + "clock\ti\t1\n"
                + "simple_mode\tb\ttrue\n"
                + "update_last_check\tl\t1792880000000\n"
                + "background_id\ts\tblack\n");
        T.eq(new HashSet<String>(Arrays.asList("app:a", "app:b")), m.get("hidden"), "set");
        T.eq(1, m.get("clock"), "int");
        T.eq(Boolean.TRUE, m.get("simple_mode"), "boolean");
        T.eq(1792880000000L, m.get("update_last_check"), "long");
        T.eq("black", m.get("background_id"), "string");
    }

    static void roundTrips() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        Set<String> s = new HashSet<String>(Arrays.asList("app:x", "in:hdmi1"));
        m.put("hidden", s);
        m.put("clock", 2);
        m.put("greeting", false);
        m.put("update_started_at", 5L);
        m.put("background_id", "a b\tc");
        T.eq(m, SettingsTransfer.parse(SettingsTransfer.format(m)), "round trip");
    }

    static void rejectsBadType() {
        IllegalArgumentException e = T.fails(IllegalArgumentException.class, new Runnable() {
            public void run() { SettingsTransfer.parse("clock\tx\t1"); }
        }, "bad type");
        T.yes(e.getMessage().startsWith("line 1:"), "message names the line: " + e.getMessage());
    }

    static void ignoresCommentsAndBlanks() {
        Map<String, Object> m = SettingsTransfer.parse("# from the old app\n\n  \nclock\ti\t0\n");
        T.eq(1, m.size(), "only the setting");
    }
}
