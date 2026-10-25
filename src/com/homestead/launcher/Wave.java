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
 * The idle screen's wave: two ribbons of fine lines rolling across the screen and slowly
 * twisting, after the PS3 menu. Each line's height is a sum of sines; u (-0.5..0.5) is a
 * line's place across its ribbon, so the lines bunch and spread as the twist turns. x is in
 * the design's 1280-wide units. No Android classes.
 */
final class Wave {
    static final int SHEETS = 2;
    //                         base  a1  k1      s1     a2  k2      s2    tw  k3      s3     n   alpha
    private static final float[][] P = {
            {0.56f, 74, 0.0041f, 0.32f, 32, 0.0093f, 0.21f, 52, 0.0029f, 0.24f, 30, 0.13f},
            {0.60f, 58, 0.0035f, -0.26f, 42, 0.0071f, 0.29f, 34, 0.0046f, -0.19f, 20, 0.09f},
    };

    private Wave() { }

    static int lines(int sheet) { return (int) P[sheet][10]; }

    static float alpha(int sheet) { return P[sheet][11]; }

    /** Height in px (height = the screen's) of line u of a ribbon at x, time t in seconds. */
    static float y(int sheet, float x, float u, float t, int height) {
        float[] p = P[sheet];
        double v = p[0] * height
                + p[1] * Math.sin(x * p[2] + t * p[3])
                + p[4] * Math.sin(x * p[5] + t * p[6] + 1.3)
                + u * p[7] * 2 * Math.sin(x * p[8] + t * p[9] + 0.7) * (1 + 0.35 * Math.sin(t * 0.17));
        return (float) v;
    }
}
