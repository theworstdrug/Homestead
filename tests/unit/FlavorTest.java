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

final class FlavorTest {
    private FlavorTest() { }

    static void run() {
        eightLines();
        neverRepeatsImmediately();
        greetingByHour();
    }

    static void eightLines() {
        T.eq(8, Flavor.LINES.length, "eight lines");
        T.eq("Popcorn optional, but encouraged.", Flavor.LINES[1], "copy from the mockup");
    }

    static void neverRepeatsImmediately() {
        Random r = new Random(42);
        int last = -1;
        for (int i = 0; i < 200; i++) {
            int n = Flavor.pick(r, last);
            T.yes(n >= 0 && n < Flavor.LINES.length, "in range");
            T.yes(n != last, "no repeat at " + i);
            last = n;
        }
    }

    static void greetingByHour() {
        T.eq(Flavor.MORNING, Flavor.greetingFor(5), "5am");
        T.eq(Flavor.MORNING, Flavor.greetingFor(11), "11am");
        T.eq(Flavor.AFTERNOON, Flavor.greetingFor(12), "noon");
        T.eq(Flavor.EVENING, Flavor.greetingFor(18), "6pm");
        T.eq(Flavor.EVENING, Flavor.greetingFor(2), "2am");
    }
}
