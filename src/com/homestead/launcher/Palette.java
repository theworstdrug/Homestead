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
 * The colours of one theme with one accent. Every screen paints from the current profile's
 * palette, so a theme or accent change is a repaint, never a rebuild. ARGB ints; no
 * Android classes, so it is unit-tested on the JVM.
 */
final class Palette {
    static final String[] THEME_IDS = {"midnight", "oled", "frost", "ember"};
    static final String[] ACCENT_IDS =
            {"default", "blue", "violet", "pink", "red", "orange", "gold", "green", "teal"};
    private static final int[] ACCENTS = {
            0, 0xFF4C6FFF, 0xFF8B5CF6, 0xFFD94F8A, 0xFFE5484D,
            0xFFF28A3C, 0xFFE8B931, 0xFF2FA37A, 0xFF21A6B5};
    static final String[] ACCENT_NAMES =
            {"Theme default", "Blue", "Violet", "Pink", "Red", "Orange", "Gold", "Green", "Teal"};

    final String id, accentId, name;
    /** Screen background, main and secondary text. */
    final int base, text, sub;
    /** Unfocused and focused chip/row fills, and the text on a focused one. */
    final int chip, chipOn, chipOnText;
    /** Outlined rank numbers, the focus ring, full-screen overlays, frosted panels. */
    final int rank, ring, overlay, sheet;
    /** The glow colour when it does not follow an app, and the idle screen's light. */
    final int neutral, light;
    /** Bottom of the darkening gradient behind the lower rows. */
    final int scrimTo;
    /** Line colour of the idle-screen wave. */
    final int waveInk;
    /** Opacity of the background glow, 0..1. */
    final float glowStrength;
    /** Light theme: the wave is drawn as dark ink with normal blending. */
    final boolean darkInk;

    private Palette(String id, String accentId, String name, int base, int text, int sub,
                    float glow, int neutral, int scrimTo, int chip, int chipOn, int chipOnText,
                    int rank, int ring, int overlay, int sheet, int light, boolean darkInk) {
        this.id = id;
        this.accentId = accentId;
        this.name = name;
        this.base = base;
        this.text = text;
        this.sub = sub;
        this.glowStrength = glow;
        this.neutral = neutral;
        this.scrimTo = scrimTo;
        this.chip = chip;
        this.chipOn = chipOn;
        this.chipOnText = chipOnText;
        this.rank = rank;
        this.ring = ring;
        this.overlay = overlay;
        this.sheet = sheet;
        this.light = light;
        this.darkInk = darkInk;
        this.waveInk = darkInk ? 0xFF121722 : 0xFFFFFFFF;
    }

    private static Palette theme(String id) {
        if ("oled".equals(id)) {
            return new Palette("oled", "default", "OLED", 0xFF000000, 0xFFFFFFFF, 0xA8FFFFFF,
                    0.28f, 0xFF3A3A3A, 0xF5000000, 0x12FFFFFF, 0xFFFFFFFF, 0xFF000000,
                    0x4DFFFFFF, 0xFFFFFFFF, 0xB8000000, 0xD10A0A0A, 0xFFFFFFFF, false);
        }
        if ("frost".equals(id)) {
            return new Palette("frost", "default", "Frost", 0xFFE8EDF4, 0xFF121722, 0xAD121722,
                    0.32f, 0xFF8FA7D6, 0xE0E8EDF4, 0x12121722, 0xFF121722, 0xFFF4F6FB,
                    0x47121722, 0xFF121722, 0x8CE8EDF4, 0xBDF6F8FB, 0xFFFFFFFF, true);
        }
        if ("ember".equals(id)) {
            return new Palette("ember", "default", "Ember", 0xFF170C0E, 0xFFFBF1EE, 0xB3FBF1EE,
                    0.50f, 0xFFA8432F, 0xEB170C0E, 0x14FFFFFF, 0xFFFBF1EE, 0xFF170C0E,
                    0x5CFBF1EE, 0xFFFBF1EE, 0x9E140A0C, 0xB81E1012, 0xFFFFD9C7, false);
        }
        return new Palette("midnight", "default", "Midnight", 0xFF0A1020, 0xFFF4F6FB, 0xB8F4F6FB,
                0.55f, 0xFF2C4BB8, 0xEB0A1020, 0x14FFFFFF, 0xFFF4F6FB, 0xFF0A1020,
                0x61F4F6FB, 0xFFF4F6FB, 0x9E080C18, 0xB80E1426, 0xFFDCE6FF, false);
    }

    /** Unknown or missing ids fall back to Midnight and the theme's own colours. */
    static Palette resolve(String themeId, String accentId) {
        Palette t = theme(themeId);
        int a = indexOf(ACCENT_IDS, accentId);
        if (a <= 0) return t;
        int c = ACCENTS[a];
        return new Palette(t.id, ACCENT_IDS[a], t.name, t.base, t.text, t.sub, t.glowStrength,
                c, t.scrimTo, t.chip, c, textOn(c), t.rank, c, t.overlay, t.sheet, t.light,
                t.darkInk);
    }

    static int accentColor(String accentId) {
        int a = indexOf(ACCENT_IDS, accentId);
        return a <= 0 ? 0 : ACCENTS[a];
    }

    static int indexOf(String[] ids, String id) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(id)) return i;
        return -1;
    }

    /** Black or white, whichever reads better on c (relative luminance, with a light bias). */
    static int textOn(int c) {
        double l = 0.2126 * lin((c >> 16) & 255) + 0.7152 * lin((c >> 8) & 255) + 0.0722 * lin(c & 255);
        return l > 0.4 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
