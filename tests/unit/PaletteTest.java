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

final class PaletteTest {
    private PaletteTest() { }

    static void run() {
        resolvesKnownTheme();
        unknownFallsBackToMidnight();
        accentReplacesRingAndChip();
        accentTextContrasts();
        everyCombinationResolves();
    }

    static void resolvesKnownTheme() {
        Palette p = Palette.resolve("frost", "default");
        T.yes(p.darkInk, "frost draws dark ink");
        T.eq(0xFFE8EDF4, p.base, "frost base");
        T.eq("Frost", p.name, "frost name");
        T.yes(!Palette.resolve("midnight", "default").darkInk, "midnight draws light ink");
    }

    static void unknownFallsBackToMidnight() {
        T.eq(Palette.resolve("midnight", "default").base, Palette.resolve("nope", "nope").base, "fallback");
        T.eq(Palette.resolve("midnight", "default").base, Palette.resolve(null, null).base, "null fallback");
    }

    static void accentReplacesRingAndChip() {
        Palette plain = Palette.resolve("midnight", "default");
        Palette red = Palette.resolve("midnight", "red");
        T.yes(red.ring != plain.ring, "ring takes the accent");
        T.yes(red.chipOn != plain.chipOn, "focused chip takes the accent");
        T.yes(red.neutral != plain.neutral, "neutral glow takes the accent");
        T.eq(plain.text, red.text, "text unchanged");
        T.eq(plain.base, red.base, "base unchanged");
    }

    static void accentTextContrasts() {
        T.eq(0xFF000000, Palette.resolve("midnight", "gold").chipOnText, "dark text on gold");
        T.eq(0xFFFFFFFF, Palette.resolve("midnight", "blue").chipOnText, "light text on blue");
    }

    static void everyCombinationResolves() {
        for (String t : Palette.THEME_IDS) {
            for (String a : Palette.ACCENT_IDS) {
                Palette p = Palette.resolve(t, a);
                T.yes((p.base >>> 24) == 0xFF, t + "/" + a + " base is opaque");
                T.yes(p.glowStrength > 0f && p.glowStrength <= 1f, t + " glow strength");
            }
        }
    }
}
