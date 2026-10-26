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

import java.util.Random;

/** The line under the greeting, and which greeting fits the hour. No Android classes. */
final class Flavor {
    static final String[] LINES = {
            "Pick up where you left off.",
            "Popcorn optional, but encouraged.",
            "The couch has been expecting you.",
            "One more episode never hurt anyone.",
            "Remote found. Great start.",
            "Settle in.",
            "Your shows missed you.",
            "No ads here. Just your stuff.",
    };
    static final int MORNING = 0, AFTERNOON = 1, EVENING = 2;

    private Flavor() { }

    /** A random line other than avoid (an index into LINES, or -1). */
    static int pick(Random r, int avoid) {
        int n = r.nextInt(LINES.length);
        return n == avoid ? (n + 1) % LINES.length : n;
    }

    /** 5:00-11:59 morning, 12:00-17:59 afternoon, otherwise evening. */
    static int greetingFor(int hour) {
        if (hour >= 5 && hour < 12) return MORNING;
        if (hour >= 12 && hour < 18) return AFTERNOON;
        return EVENING;
    }
}
