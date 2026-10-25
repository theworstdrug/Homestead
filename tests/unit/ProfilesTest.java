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
import java.util.HashSet;

final class ProfilesTest {
    private ProfilesTest() { }

    static void run() {
        encodeDecodeRoundTrip();
        ownerCannotBeDeleted();
        maxSix();
        nonOwnerCannotSeeEveryone();
        legacyBecomesOwnerSinclair();
        legacyPhoto();
        decodeGarbageGivesNothing();
        nameCasing();
        nameTooLong();
        settingsSectionsFor();
        defaultOwnerIsMe();
        ownerFlagRecoveredOnDecode();
    }

    static void defaultOwnerIsMe() {
        T.eq("Me", Profiles.DEFAULT_OWNER_NAME, "a fresh install's owner is called Me");
    }

    static void ownerFlagRecoveredOnDecode() {
        // The owner's line is damaged: the first remaining profile becomes the owner, so
        // someone can always reach Everyone and System.
        Profiles ps = Profiles.decode("p2|Test|5|frost|default|true|true|true|false|false|\n");
        T.yes(Profiles.canSeeEveryone(ps.owner()), "an owner always exists");
    }

    static void settingsSectionsFor() {
        Profiles ps = Profiles.fromLegacy("Sinclair", "midnight").add(kid());
        T.eq(java.util.Arrays.asList("for", "everyone", "home", "system"),
                java.util.Arrays.asList(Profiles.settingsSections(ps.owner())), "owner sees all");
        T.eq(java.util.Arrays.asList("for", "home"),
                java.util.Arrays.asList(Profiles.settingsSections(ps.byId("p2"))), "others see their own and the home screen");
    }

    static void nameCasing() {
        T.eq("Anna Marie", Profiles.cleanName("aNNA maRie"), "casing");
        T.eq("Bo", Profiles.cleanName("  bo  "), "trimmed");
        T.eq("Ann Lee", Profiles.cleanName("ann   lee"), "one space between words");
        T.eq("", Profiles.cleanName("   "), "blank");
        T.eq("Zoe", Profiles.cleanName("z|o,e"), "only letters and spaces survive");
    }

    static void nameTooLong() {
        T.eq("Abcdefghijkl", Profiles.cleanName("abcdefghijklm"), "cut to 12");
    }

    private static Profiles.Profile kid() {
        Profiles.Profile p = new Profiles.Profile();
        p.id = "p2";
        p.name = "Test";
        p.color = 5;
        p.theme = "frost";
        p.accent = "green";
        p.hidden = new HashSet<String>(Arrays.asList("app:com.hulu.plus", "in:hdmi1"));
        return p;
    }

    static void encodeDecodeRoundTrip() {
        Profiles ps = Profiles.fromLegacy("Sinclair", "deepsea").add(kid());
        Profiles back = Profiles.decode(ps.encode());
        T.eq(2, back.size(), "two profiles");
        T.eq("Sinclair", back.owner().name, "owner name");
        Profiles.Profile k = back.byId("p2");
        T.eq("Test", k.name, "name");
        T.eq(5, k.color, "colour");
        T.eq("frost", k.theme, "theme");
        T.eq("green", k.accent, "accent");
        T.eq(kid().hidden, k.hidden, "hidden");
        T.yes(!k.owner, "not owner");
    }

    static void ownerCannotBeDeleted() {
        final Profiles ps = Profiles.fromLegacy("Sinclair", "midnight").add(kid());
        T.fails(IllegalStateException.class, new Runnable() {
            public void run() { ps.remove(ps.owner().id); }
        }, "remove owner");
        T.eq(1, ps.remove("p2").size(), "others can go");
    }

    static void maxSix() {
        Profiles ps = Profiles.fromLegacy("Sinclair", "midnight");
        for (int i = 0; i < 5; i++) {
            Profiles.Profile p = kid();
            p.id = "k" + i;
            ps = ps.add(p);
        }
        T.eq(6, ps.size(), "six");
        final Profiles full = ps;
        T.fails(IllegalStateException.class, new Runnable() {
            public void run() { full.add(kid()); }
        }, "seventh");
    }

    static void nonOwnerCannotSeeEveryone() {
        Profiles ps = Profiles.fromLegacy("Sinclair", "midnight").add(kid());
        T.yes(Profiles.canSeeEveryone(ps.owner()), "owner sees everyone settings");
        T.yes(!Profiles.canSeeEveryone(ps.byId("p2")), "others do not");
        T.yes(!Profiles.canSeeEveryone(null), "nobody does not");
    }

    static void legacyBecomesOwnerSinclair() {
        Profiles ps = Profiles.fromLegacy("Sinclair", "black");
        T.eq(1, ps.size(), "one profile");
        Profiles.Profile o = ps.owner();
        T.eq("Sinclair", o.name, "name");
        T.eq("oled", o.theme, "black becomes OLED");
        T.yes(o.greeting, "the new greeting starts on, whatever the old one was");
        T.yes(o.glow && o.ranks, "glow and ranks on");
        T.eq("ember", Profiles.fromLegacy("S", "ember").owner().theme, "ember stays ember");
        T.eq("frost", Profiles.fromLegacy("S", "sky").owner().theme, "sky becomes frost");
        T.eq("midnight", Profiles.fromLegacy("S", "deepsea").owner().theme, "others become midnight");
    }

    static void legacyPhoto() {
        T.yes(Profiles.fromLegacy("S", "photo").owner().usePhoto, "photo carried");
        T.yes(!Profiles.fromLegacy("S", "black").owner().usePhoto, "no photo");
    }

    static void decodeGarbageGivesNothing() {
        T.eq(0, Profiles.decode("").size(), "empty");
        T.eq(0, Profiles.decode(null).size(), "null");
        T.eq(0, Profiles.decode("not|a|profile").size(), "short line skipped");
    }
}
