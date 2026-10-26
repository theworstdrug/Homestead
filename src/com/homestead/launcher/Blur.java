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

/**
 * A soft blur for one channel (the idle screen's glow is one colour, so only its alpha is
 * blurred): two running-sum box passes across and two down, close to a Gaussian. Edges
 * repeat the outermost value, so a flat field stays flat. No Android classes.
 */
final class Blur {
    private Blur() { }

    /** Blur a (w×h values) in place with radius r; tmp is scratch of the same size. */
    static void box(int[] a, int w, int h, int r, int[] tmp) {
        if (r <= 0 || w <= 0 || h <= 0) return;
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) line(a, tmp, y * w, 1, w, r);
            for (int x = 0; x < w; x++) line(tmp, a, x, w, h, r);
        }
    }

    /** One line from src into dst: n values starting at off, step apart. */
    private static void line(int[] src, int[] dst, int off, int step, int n, int r) {
        int div = 2 * r + 1;
        long sum = 0;
        for (int i = -r; i <= r; i++) sum += src[off + clamp(i, n) * step];
        for (int i = 0; i < n; i++) {
            dst[off + i * step] = (int) (sum / div);
            sum += src[off + clamp(i + r + 1, n) * step] - src[off + clamp(i - r, n) * step];
        }
    }

    private static int clamp(int i, int n) { return i < 0 ? 0 : i >= n ? n - 1 : i; }
}
