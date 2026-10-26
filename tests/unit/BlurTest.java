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

final class BlurTest {
    private BlurTest() { }

    static void run() {
        pointSpreadsSmoothly();
        flatStaysFlat();
        edgesSafe();
    }

    static void pointSpreadsSmoothly() {
        int w = 41, h = 41;
        int[] a = new int[w * h];
        a[20 * w + 20] = 255 * 81;
        Blur.box(a, w, h, 4, new int[w * h]);
        T.yes(a[20 * w + 20] > 0, "centre still lit");
        T.yes(a[20 * w + 24] > 0 && a[20 * w + 24] < a[20 * w + 20], "falls off with distance");
        T.yes(a[20 * w + 30] == 0, "nothing beyond twice the radius");
        // Smooth: no neighbouring pixels differ by more than a small step (no blocks).
        int maxStep = 0;
        for (int x = 1; x < w; x++) maxStep = Math.max(maxStep, Math.abs(a[20 * w + x] - a[20 * w + x - 1]));
        T.yes(maxStep < a[20 * w + 20] / 3, "gentle steps: " + maxStep);
    }

    static void flatStaysFlat() {
        int w = 20, h = 10;
        int[] a = new int[w * h];
        java.util.Arrays.fill(a, 100);
        Blur.box(a, w, h, 3, new int[w * h]);
        for (int v : a) T.yes(Math.abs(v - 100) <= 1, "flat field unchanged: " + v);
    }

    static void edgesSafe() {
        int w = 5, h = 3;
        int[] a = {255, 0, 0, 0, 255, 0, 0, 0, 0, 0, 255, 0, 0, 0, 255};
        Blur.box(a, w, h, 6, new int[w * h]); // radius larger than the image
        for (int v : a) T.yes(v >= 0 && v <= 255, "in range: " + v);
    }
}
