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

final class WaveTest {
    private WaveTest() { }

    static void run() {
        waveIsDeterministic();
        centreLineStaysOnScreen();
        twistSpreadsLines();
        twoSheets();
    }

    static void waveIsDeterministic() {
        T.eq(Wave.y(0, 300f, 0.2f, 12.5f, 720), Wave.y(0, 300f, 0.2f, 12.5f, 720), "same inputs, same y");
    }

    static void centreLineStaysOnScreen() {
        for (float t = 0; t <= 600; t += 0.5f) {
            for (float x = 0; x <= 1280; x += 40) {
                for (int s = 0; s < Wave.SHEETS; s++) {
                    float y = Wave.y(s, x, 0f, t, 720);
                    if (y < 200 || y > 620) T.yes(false, "sheet " + s + " at x=" + x + " t=" + t + " y=" + y);
                }
            }
        }
        T.yes(true, "centre line on screen");
    }

    static void twistSpreadsLines() {
        float max = 0;
        for (float t = 0; t <= 120; t += 0.7f) {
            for (float x = 0; x <= 1280; x += 20) {
                max = Math.max(max, Math.abs(Wave.y(0, x, 0.5f, t, 720) - Wave.y(0, x, -0.5f, t, 720)));
            }
        }
        T.yes(max <= 2 * 52 * 1.35f + 1, "spread bounded: " + max);
        T.yes(max > 40, "the lines do spread: " + max);
    }

    static void twoSheets() {
        T.eq(2, Wave.SHEETS, "two ribbons");
        T.eq(30, Wave.lines(0), "30 lines in the first");
        T.eq(20, Wave.lines(1), "20 in the second");
    }
}
