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
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings → Theme for the current profile: the four themes (and the photo, once one has
 * been sent) as cards, then a row of accent colours. Moving over a card or a colour shows
 * it on the home screen beside the page; Select keeps it; Back leaves the saved one.
 */
final class ThemePanel {
    private ThemePanel() { }

    static View build(final Context c, final SettingsPanel.Host host) {
        final Panel p = new Panel(c, "Theme", "The home screen beside this page shows each one "
                + "as you move. Select keeps it; Back cancels.", Panel.SHEET);
        final Palette ui = p.pal;
        final int glow = Prefs.lastGlow(c);
        final List<View> cards = new ArrayList<View>();
        final List<String> ids = new ArrayList<String>();
        List<String> all = new ArrayList<String>();
        for (String t : Palette.THEME_IDS) all.add(t);
        if (Backgrounds.photoFile(c) != null) all.add("photo");

        // Focused cards and dots grow; padding gives them room so the edges are not cut.
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(Px.of(c, 10), Px.of(c, 8), Px.of(c, 10), Px.of(c, 8));
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = Px.of(c, 18);
                grid.addView(row, rlp);
            }
            final String id = all.get(i);
            boolean photo = id.equals("photo");
            Profiles.Profile me = Prefs.current(c);
            final Palette shown = Palette.resolve(photo ? me.theme : id, me.accent);
            View card = ThemeCards.card(c, ui, Palette.resolve(photo ? me.theme : id, "default"), glow,
                    isSaved(me, id));
            if (photo) ((TextView[]) card.getTag())[0].setText("Photo");
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    ThemeCards.focus(v, ui, has);
                    if (has) host.previewPalette(shown);
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Profiles.Profile me = Prefs.current(c).copy();
                    if (id.equals("photo")) me.usePhoto = true;
                    else { me.theme = id; me.usePhoto = false; }
                    Prefs.updateCurrent(c, me);
                    host.applyPalette();
                    for (int k = 0; k < cards.size(); k++) {
                        ((TextView[]) cards.get(k).getTag())[1].setText(isSaved(me, ids.get(k)) ? "✓" : "");
                    }
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 1) clp.leftMargin = Px.of(c, 18);
            row.addView(card, clp);
            cards.add(card);
            ids.add(id);
        }
        if (all.size() % 2 == 1) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        p.addView(grid, Px.of(c, 14));

        TextView label = new TextView(c);
        label.setText("ACCENT");
        label.setTextColor(ui.sub);
        label.setLetterSpacing(0.12f);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        p.addView(label, Px.of(c, 26));

        LinearLayout swatches = new LinearLayout(c);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setClipChildren(false);
        swatches.setPadding(Px.of(c, 10), Px.of(c, 10), Px.of(c, 10), Px.of(c, 10));
        final List<View> sw = new ArrayList<View>();
        for (int i = 0; i < Palette.ACCENT_IDS.length; i++) {
            final String acc = Palette.ACCENT_IDS[i];
            final View dot = new View(c);
            dot.setFocusable(true);
            dot.setFocusableInTouchMode(true);
            dot.setContentDescription(Palette.ACCENT_NAMES[i]);
            paintSwatch(c, ui, dot, acc, false, acc.equals(Prefs.current(c).accent));
            dot.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    paintSwatch(c, ui, v, acc, has, acc.equals(Prefs.current(c).accent));
                    if (has) host.previewPalette(Palette.resolve(Prefs.current(c).theme, acc));
                }
            });
            dot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Profiles.Profile me = Prefs.current(c).copy();
                    me.accent = acc;
                    Prefs.updateCurrent(c, me);
                    host.applyPalette();
                    for (int k = 0; k < sw.size(); k++) {
                        String a = Palette.ACCENT_IDS[k];
                        paintSwatch(c, ui, sw.get(k), a, sw.get(k).isFocused(), a.equals(acc));
                    }
                }
            });
            int s = Px.of(c, 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            if (i > 0) lp.leftMargin = Px.of(c, 7);
            swatches.addView(dot, lp);
            sw.add(dot);
        }
        p.addView(swatches, Px.of(c, 4));

        // Back cancels a preview; the page then closes as usual.
        p.root.setTag(new HomeGrid.BackHandler() {
            public boolean onBack() {
                host.applyPalette();
                return false;
            }
        });
        // Start on the theme in use.
        Profiles.Profile me = Prefs.current(c);
        int start = me.usePhoto ? ids.indexOf("photo") : ids.indexOf(me.theme);
        final View first = cards.get(Math.max(0, start));
        p.root.post(new Runnable() {
            public void run() { p.root.post(new Runnable() { public void run() { first.requestFocus(); } }); }
        });
        return p.root;
    }

    private static boolean isSaved(Profiles.Profile me, String id) {
        return id.equals("photo") ? me.usePhoto : !me.usePhoto && id.equals(me.theme);
    }

    /** A colour dot; "default" is drawn as an outlined ring. The one in use gets a halo. */
    private static void paintSwatch(Context c, Palette ui, View v, String acc, boolean focused, boolean saved) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        int col = Palette.accentColor(acc);
        if (col == 0) {
            d.setColor(0);
            d.setStroke(Px.of(c, 3), ui.text);
        } else {
            d.setColor(col);
            if (saved || focused) d.setStroke(Px.of(c, focused ? 3 : 2), ui.text);
        }
        // Focus grows the dot inside its own space instead of scaling the view, which the
        // page clips. An oval's stroke straddles its edge, so even the largest is inset.
        int in = Px.of(c, focused ? 2 : 6);
        v.setBackground(new android.graphics.drawable.InsetDrawable(d, in));
    }
}
