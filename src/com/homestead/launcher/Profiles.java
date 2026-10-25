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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The people who use this TV. One profile is the owner: only the owner sees the settings
 * that affect everyone (hiding for everyone, weather, profiles, the System pages). Each
 * profile keeps its own look and its own hidden tiles on top of the ones hidden for
 * everyone. Changes return a new Profiles; Prefs stores encode(). No Android classes.
 */
final class Profiles {
    static final int MAX = 6;
    static final int[] AVATAR_COLORS = {
            0xFF4C6FFF, 0xFFE0703A, 0xFF3A3A3A, 0xFF7FA6C9,
            0xFFD94F8A, 0xFF2FA37A, 0xFF8B5CF6, 0xFFC9A227};
    static final String[] AVATAR_NAMES =
            {"Blue", "Orange", "Graphite", "Sky", "Pink", "Green", "Violet", "Gold"};
    static final String OWNER_ID = "owner";
    /** The owner on an install that predates profiles; renamed in Settings → Profiles. */
    static final String DEFAULT_OWNER_NAME = "Me";

    static final class Profile {
        String id;
        /** Letters and spaces only, at most 12 characters (the on-screen keyboard). */
        String name;
        /** Index into AVATAR_COLORS. */
        int color;
        String theme = "midnight";
        String accent = "default";
        boolean glow = true, ranks = true, greeting = true, owner, usePhoto;
        Set<String> hidden = new HashSet<String>();
        /** A picture for the avatar (one emoji), or null for the name's first letter. */
        String emoji;

        Profile copy() {
            Profile p = new Profile();
            p.id = id;
            p.name = name;
            p.color = color;
            p.theme = theme;
            p.accent = accent;
            p.glow = glow;
            p.ranks = ranks;
            p.greeting = greeting;
            p.owner = owner;
            p.usePhoto = usePhoto;
            p.hidden = new HashSet<String>(hidden);
            p.emoji = emoji;
            return p;
        }

        int avatarColor() { return AVATAR_COLORS[Math.max(0, Math.min(AVATAR_COLORS.length - 1, color))]; }

        /** What the avatar shows: the picture, else the name's first letter. */
        String initial() {
            if (emoji != null && !emoji.isEmpty()) return emoji;
            return name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        }
    }

    private final List<Profile> list;

    private Profiles(List<Profile> list) { this.list = Collections.unmodifiableList(list); }

    int size() { return list.size(); }

    Profile get(int i) { return list.get(i); }

    List<Profile> all() { return list; }

    Profile owner() {
        for (Profile p : list) if (p.owner) return p;
        return list.isEmpty() ? null : list.get(0);
    }

    Profile byId(String id) {
        for (Profile p : list) if (p.id.equals(id)) return p;
        return null;
    }

    Profiles add(Profile p) {
        if (list.size() >= MAX) throw new IllegalStateException("at most " + MAX + " profiles");
        List<Profile> l = new ArrayList<Profile>(list);
        Profile c = p.copy();
        if (c.id == null || byId(c.id) != null) c.id = newId();
        l.add(c);
        return new Profiles(l);
    }

    Profiles remove(String id) {
        Profile p = byId(id);
        if (p == null) return this;
        if (p.owner) throw new IllegalStateException("the owner profile cannot be deleted");
        List<Profile> l = new ArrayList<Profile>(list);
        l.remove(p);
        return new Profiles(l);
    }

    Profiles replace(Profile p) {
        List<Profile> l = new ArrayList<Profile>(list);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(p.id)) {
                Profile c = p.copy();
                c.owner = l.get(i).owner; // ownership never moves by editing
                l.set(i, c);
            }
        }
        return new Profiles(l);
    }

    private String newId() {
        for (int n = 1; ; n++) if (byId("p" + n) == null) return "p" + n;
    }

    static boolean canSeeEveryone(Profile p) { return p != null && p.owner; }

    /** The Settings sections a profile sees, in order: the owner sees all four. */
    static String[] settingsSections(Profile p) {
        return canSeeEveryone(p) ? new String[]{"for", "everyone", "home", "system"}
                : new String[]{"for", "home"};
    }

    /**
     * A name as the on-screen keyboard makes it: letters and single spaces only, each word
     * capitalised, at most 12 characters. The stored format splits on | and , so nothing
     * else may get through.
     */
    static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        boolean startWord = true;
        for (int i = 0; i < raw.length() && b.length() < 12; i++) {
            char ch = raw.charAt(i);
            if (Character.isLetter(ch)) {
                b.append(startWord ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                startWord = false;
            } else if (ch == ' ' && b.length() > 0 && !startWord) {
                b.append(' ');
                startWord = true;
            }
        }
        return b.toString().trim();
    }

    /**
     * The first profile on an install that predates profiles, made from its old settings.
     * The greeting starts on: the new one (name and flavour line) replaces the old option.
     */
    static Profiles fromLegacy(String ownerName, String backgroundId) {
        Profile o = new Profile();
        o.id = OWNER_ID;
        o.name = ownerName;
        o.color = 0;
        o.owner = true;
        o.greeting = true;
        if ("black".equals(backgroundId)) o.theme = "oled";
        else if ("ember".equals(backgroundId)) o.theme = "ember";
        else if ("sky".equals(backgroundId)) o.theme = "frost";
        else o.theme = "midnight";
        o.usePhoto = "photo".equals(backgroundId);
        List<Profile> l = new ArrayList<Profile>();
        l.add(o);
        return new Profiles(l);
    }

    // ---------------------------------------------------------------- storage

    /** One profile per line, fields separated by |, hidden keys by commas. */
    String encode() {
        StringBuilder b = new StringBuilder();
        for (Profile p : list) {
            List<String> h = new ArrayList<String>(p.hidden);
            Collections.sort(h);
            StringBuilder hs = new StringBuilder();
            for (int i = 0; i < h.size(); i++) {
                if (i > 0) hs.append(',');
                hs.append(h.get(i));
            }
            b.append(p.id).append('|').append(p.name).append('|').append(p.color)
                    .append('|').append(p.theme).append('|').append(p.accent)
                    .append('|').append(p.glow).append('|').append(p.ranks)
                    .append('|').append(p.greeting).append('|').append(p.owner)
                    .append('|').append(p.usePhoto).append('|').append(hs)
                    .append('|').append(p.emoji == null ? "" : p.emoji).append('\n');
        }
        return b.toString();
    }

    /** Lines that do not parse are skipped, so a damaged entry costs that profile only. */
    static Profiles decode(String s) {
        List<Profile> l = new ArrayList<Profile>();
        if (s != null) {
            for (String line : s.split("\n")) {
                String[] f = line.split("\\|", -1);
                // Eleven fields before profile pictures, twelve since.
                if ((f.length != 11 && f.length != 12) || f[0].isEmpty() || f[1].isEmpty()) continue;
                try {
                    Profile p = new Profile();
                    p.id = f[0];
                    p.name = f[1];
                    p.color = Integer.parseInt(f[2]);
                    p.theme = f[3];
                    p.accent = f[4];
                    p.glow = Boolean.parseBoolean(f[5]);
                    p.ranks = Boolean.parseBoolean(f[6]);
                    p.greeting = Boolean.parseBoolean(f[7]);
                    p.owner = Boolean.parseBoolean(f[8]);
                    p.usePhoto = Boolean.parseBoolean(f[9]);
                    if (!f[10].isEmpty()) {
                        for (String k : f[10].split(",")) p.hidden.add(k);
                    }
                    if (f.length == 12 && !f[11].isEmpty()) p.emoji = f[11];
                    l.add(p);
                } catch (NumberFormatException ignored) { }
            }
        }
        // A damaged owner line must not leave the TV with nobody who can reach the owner's
        // settings: the first profile left takes over.
        boolean hasOwner = false;
        for (Profile p : l) hasOwner |= p.owner;
        if (!hasOwner && !l.isEmpty()) l.get(0).owner = true;
        return new Profiles(l);
    }
}
