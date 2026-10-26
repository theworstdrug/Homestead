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

final class BannerColorTest {
    private BannerColorTest() { }

    private static final int FALLBACK = 0xFF123456;

    static void run() {
        bannerColorFindsRed();
        bannerColorFindsBlueOnWhite();
        bannerColorFallsBackForGreyBanners();
        bannerColorFallsBackForEmpty();
    }

    static void bannerColorFindsRed() {
        int[] px = new int[100];
        Arrays.fill(px, 0xFF000000);
        Arrays.fill(px, 0, 60, 0xFFE50914);
        int c = BannerColor.of(px, FALLBACK);
        T.yes(c != FALLBACK, "red found");
        T.near(hue(0xFFE50914), hue(c), 15, "red hue");
    }

    static void bannerColorFindsBlueOnWhite() {
        int[] px = new int[100];
        Arrays.fill(px, 0xFFFFFFFF);
        Arrays.fill(px, 0, 10, 0xFF00A8E1);
        T.near(hue(0xFF00A8E1), hue(BannerColor.of(px, FALLBACK)), 15, "blue on white");
    }

    static void bannerColorFallsBackForGreyBanners() {
        for (int grey : new int[]{0xFFFFFFFF, 0xFF808080, 0xFF000000, 0xFF141414}) {
            int[] px = new int[100];
            Arrays.fill(px, grey);
            T.eq(FALLBACK, BannerColor.of(px, FALLBACK), "grey " + Integer.toHexString(grey));
        }
        int[] px = new int[100];
        Arrays.fill(px, 0xFFFFFFFF);
        px[0] = 0xFFFF0000; // 1% colour is noise, not the banner's colour
        T.eq(FALLBACK, BannerColor.of(px, FALLBACK), "a speck of colour");
    }

    static void bannerColorFallsBackForEmpty() {
        T.eq(FALLBACK, BannerColor.of(new int[0], FALLBACK), "empty");
        T.eq(FALLBACK, BannerColor.of(null, FALLBACK), "null");
    }

    private static double hue(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d == 0) return 0;
        double h = max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }
}
