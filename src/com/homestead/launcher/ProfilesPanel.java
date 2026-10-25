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

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * Settings → Profiles, for the owner: every profile, and for each one a page to rename it,
 * change its colour or remove it. The owner profile cannot be removed; removing the
 * profile in use switches to the owner.
 */
final class ProfilesPanel {
    private ProfilesPanel() { }

    static View build(final Context c, final SettingsPanel.Host host) {
        final Panel p = new Panel(c, "Profiles", "Select one to change it", Panel.SHEET);
        Profiles ps = Prefs.profiles(c);
        for (int i = 0; i < ps.size(); i++) {
            final Profiles.Profile pr = ps.get(i);
            Panel.Row r = p.addRow(pr.name, pr.owner ? "owner" : "", new Runnable() {
                public void run() {
                    host.closePanel();
                    host.showPanel(buildOne(c, host, pr.id));
                }
            }).opens();
            r.setValueColor(0);
            r.setSwatch(dot(c, pr.avatarColor()));
        }
        if (ps.size() < Profiles.MAX) {
            p.addRow("Add a profile", "", new Runnable() {
                public void run() {
                    host.closePanel();
                    host.showPanel(ProfileCreator.build(c, host));
                }
            }).opens();
        }
        p.addRow("Back", "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static View buildOne(final Context c, final SettingsPanel.Host host, final String id) {
        final Profiles.Profile pr = Prefs.profiles(c).byId(id);
        final Panel p = new Panel(c, pr.name, pr.owner ? "The owner profile" : null, Panel.SHEET);

        p.addRow("Rename", pr.name, new Runnable() {
            public void run() {
                host.closePanel();
                host.showPanel(buildRename(c, host, id));
            }
        }).opens().setValueColor(0);

        final Panel.Row colour = p.addRow("Colour", Profiles.AVATAR_NAMES[pr.color], null);
        colour.setValueColor(0);
        colour.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Profiles.Profile e = Prefs.profiles(c).byId(id).copy();
                e.color = (e.color + 1) % Profiles.AVATAR_COLORS.length;
                Prefs.setProfiles(c, Prefs.profiles(c).replace(e));
                colour.setValue(Profiles.AVATAR_NAMES[e.color]);
                host.profileChanged();
            }
        });
        colour.setHelp("Select to step through the colours. The avatar at the top right changes as you go.");

        if (!pr.owner) {
            final boolean[] armed = {false};
            final Panel.Row del = p.addRow("Remove this profile", "", null);
            del.setHelp("Its theme and hidden apps go with it. Apps and inputs are not touched.");
            del.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!armed[0]) {
                        armed[0] = true;
                        del.setValue("press again to remove");
                        del.setValueColor(0xFFE89A8A);
                        return;
                    }
                    Profiles ps = Prefs.profiles(c).remove(id);
                    Prefs.setProfiles(c, ps);
                    if (id.equals(Prefs.current(c).id) || Prefs.profiles(c).byId(Prefs.current(c).id) == null) {
                        Prefs.setCurrent(c, ps.owner().id);
                    }
                    host.closePanel();
                    host.profileChanged();
                    host.showPanel(build(c, host));
                }
            });
        }
        p.addRow("Back", "", new Runnable() {
            public void run() {
                host.closePanel();
                host.showPanel(build(c, host));
            }
        });
        return p.root;
    }

    /** Full screen, like naming a new profile: the keyboard is too wide for a sheet. */
    private static View buildRename(final Context c, final SettingsPanel.Host host, final String id) {
        final Profiles.Profile pr = Prefs.profiles(c).byId(id);
        Palette pal = Prefs.palette(c);
        android.widget.FrameLayout root = ProfilePicker.overlay(c, pal);
        android.widget.LinearLayout col = new android.widget.LinearLayout(c);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        col.addView(ProfilePicker.title(c, pal, "Rename " + pr.name), wrap());
        final android.widget.TextView box = new android.widget.TextView(c);
        box.setGravity(android.view.Gravity.CENTER);
        box.setTextColor(pal.text);
        box.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Px.f(c, 34));
        box.setText(pr.name + "|");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Px.f(c, 18));
        bg.setColor(pal.chip);
        bg.setStroke(Px.of(c, 2), pal.sub);
        box.setBackground(bg);
        android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(Px.of(c, 420), Px.of(c, 72));
        blp.topMargin = Px.of(c, 34);
        blp.bottomMargin = Px.of(c, 30);
        col.addView(box, blp);
        final OnScreenKeyboard kb = new OnScreenKeyboard(c, 12, "Save", new OnScreenKeyboard.Listener() {
            public void onChange(String text) { box.setText(text + "|"); }
            public void onDone(String text) {
                String n = Profiles.cleanName(text);
                if (n.isEmpty()) return;
                Profiles.Profile e = Prefs.profiles(c).byId(id).copy();
                e.name = n;
                Prefs.setProfiles(c, Prefs.profiles(c).replace(e));
                host.closePanel();
                host.profileChanged();
                host.showPanel(buildOne(c, host, id));
            }
        });
        kb.setText(pr.name);
        col.addView(kb, wrap());
        root.addView(col, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER));
        ProfilePicker.popIn(col);
        kb.post(new Runnable() { public void run() { kb.requestFocus(); } });
        return root;
    }

    private static android.widget.LinearLayout.LayoutParams wrap() {
        return new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable dot(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
