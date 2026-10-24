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
 * An app's colour, for the background glow: the dominant saturated colour of its banner.
 * Pixels that are near grey, near black or near white are ignored, so a white banner with
 * a red logo gives red, and an all-grey banner gives the fallback. No Android classes.
 */
final class BannerColor {
    private static final int BUCKETS = 12;
    private static final float MIN_SAT = 0.25f, MIN_VAL = 0.15f, MAX_VAL = 0.95f;
    /** Below this share of colourful pixels the banner counts as colourless. */
    private static final float MIN_SHARE = 0.04f;

    private BannerColor() { }

    static int of(int[] argb, int fallback) {
        if (argb == null || argb.length == 0) return fallback;
        float[] weight = new float[BUCKETS];
        float[] r = new float[BUCKETS], g = new float[BUCKETS], b = new float[BUCKETS];
        int counted = 0;
        for (int p : argb) {
            if ((p >>> 24) < 128) continue;
            int pr = (p >> 16) & 255, pg = (p >> 8) & 255, pb = p & 255;
            int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
            float val = max / 255f;
            float sat = max == 0 ? 0 : (max - min) / (float) max;
            if (sat < MIN_SAT || val < MIN_VAL || val > MAX_VAL && sat < 0.5f) continue;
            float hue = hue(pr, pg, pb, max, min);
            int k = Math.min(BUCKETS - 1, (int) (hue / (360f / BUCKETS)));
            float w = sat * val;
            weight[k] += w;
            r[k] += pr * w;
            g[k] += pg * w;
            b[k] += pb * w;
            counted++;
        }
        if (counted < argb.length * MIN_SHARE) return fallback;
        int best = 0;
        for (int k = 1; k < BUCKETS; k++) if (weight[k] > weight[best]) best = k;
        if (weight[best] <= 0) return fallback;
        return 0xFF000000
                | (Math.round(r[best] / weight[best]) << 16)
                | (Math.round(g[best] / weight[best]) << 8)
                | Math.round(b[best] / weight[best]);
    }

    private static float hue(int r, int g, int b, int max, int min) {
        float d = max - min;
        if (d == 0) return 0;
        float h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }
}
