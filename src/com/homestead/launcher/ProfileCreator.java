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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A new profile in three steps, with progress dots: a name on the on-screen keyboard, an
 * avatar colour, then a theme, which the whole screen previews as the focus moves. Back
 * steps back; Back on the first step leaves without making anything.
 */
final class ProfileCreator {
    private static final int NAME = 0, COLOR = 1, THEME = 2;

    private final Context c;
    private final SettingsPanel.Host host;
    private final Palette pal;
    private final FrameLayout root;
    private final FrameLayout page;
    private final LinearLayout dots;
    private int step;
    private String name = "";
    private int color;
    private int theme;

    static View build(Context c, SettingsPanel.Host host) {
        return new ProfileCreator(c, host).root;
    }

    private ProfileCreator(Context c, SettingsPanel.Host host) {
        this.c = c;
        this.host = host;
        this.pal = Prefs.palette(c);
        this.color = Prefs.profiles(c).size() % Profiles.AVATAR_COLORS.length;
        root = ProfilePicker.overlay(c, pal);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        // Focused keys, colours and cards grow past their rows; nothing may clip them.
        col.setClipChildren(false);
        col.setClipToPadding(false);
        dots = new LinearLayout(c);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Px.of(c, 8)));
        page = new FrameLayout(c);
        page.setClipChildren(false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Px.of(c, 30);
        col.addView(page, plp);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.setTag(new HomeGrid.BackHandler() {
            public boolean onBack() {
                if (step == NAME) return false;
                if (step == THEME) ProfileCreator.this.host.applyPalette();
                show(step - 1);
                return true;
            }
        });
        show(NAME);
    }

    private void show(int s) {
        step = s;
        page.removeAllViews();
        View v = s == NAME ? namePage() : s == COLOR ? colorPage() : themePage();
        page.addView(v);
        Px.noFocusHighlight(v);
        v.setAlpha(0f);
        v.setTranslationX(Px.f(c, 24));
        v.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(Px.IOS).start();
        paintDots();
    }

    private void paintDots() {
        dots.removeAllViews();
        for (int i = 0; i < 3; i++) {
            View d = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Px.f(c, 4));
            g.setColor(i <= step ? pal.text : (pal.sub & 0x00FFFFFF) | 0x66000000);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Px.of(c, i == step ? 34 : 8), Px.of(c, 8));
            if (i > 0) lp.leftMargin = Px.of(c, 10);
            dots.addView(d, lp);
        }
    }

    private LinearLayout column(String titleText) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        TextView t = ProfilePicker.title(c, pal, titleText);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 36));
        col.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private View namePage() {
        LinearLayout col = column(c.getString(R.string.profiles_name_title));
        final TextView box = new TextView(c);
        box.setGravity(Gravity.CENTER);
        box.setTextColor(pal.text);
        box.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 34));
        box.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.setText(name.isEmpty() ? "|" : name + "|");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Px.f(c, 18));
        bg.setColor(pal.chip);
        bg.setStroke(Px.of(c, 2), pal.sub);
        box.setBackground(bg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Px.of(c, 420), Px.of(c, 72));
        blp.topMargin = Px.of(c, 34);
        blp.bottomMargin = Px.of(c, 30);
        col.addView(box, blp);
        final OnScreenKeyboard kb = new OnScreenKeyboard(c, 12, c.getString(R.string.profiles_next),
                new OnScreenKeyboard.Listener() {
                    public void onChange(String text) {
                        name = Profiles.cleanName(text);
                        box.setText(text + "|");
                    }
                    public void onDone(String text) {
                        name = Profiles.cleanName(text);
                        if (!name.isEmpty()) show(COLOR);
                    }
                });
        kb.setText(name);
        col.addView(kb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        focusLater(kb);
        return col;
    }

    private View colorPage() {
        LinearLayout col = column(c.getString(R.string.profiles_color_title, name));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        View first = null;
        for (int i = 0; i < Profiles.AVATAR_COLORS.length; i++) {
            final int index = i;
            final int col0 = Profiles.AVATAR_COLORS[i];
            final TextView t = new TextView(c);
            t.setText(name.substring(0, 1));
            t.setGravity(Gravity.CENTER);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 38));
            t.setContentDescription(Profiles.AVATAR_NAMES[i]);
            t.setFocusable(true);
            t.setFocusableInTouchMode(true);
            t.setBackground(ProfilePicker.circleBg(c, pal, col0, false, false));
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    t.setBackground(ProfilePicker.circleBg(c, pal, col0, false, has));
                    float s = has ? 1.18f : 1f;
                    t.animate().scaleX(s).scaleY(s).setDuration(260).setInterpolator(Px.EASE).start();
                    if (has) color = index;
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { color = index; show(THEME); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Px.of(c, 96), Px.of(c, 96));
            if (i > 0) lp.leftMargin = Px.of(c, 26);
            row.addView(t, lp);
            if (i == color) first = t;
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Px.of(c, 54);
        col.addView(row, rlp);
        focusLater(first);
        return col;
    }

    private View themePage() {
        LinearLayout col = column(c.getString(R.string.profiles_theme_title));
        TextView sub = new TextView(c);
        sub.setText(R.string.profiles_theme_sub);
        sub.setTextColor(pal.sub);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 16));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Px.of(c, 6);
        col.addView(sub, slp);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        View first = null;
        int glow = Prefs.lastGlow(c);
        for (int i = 0; i < Palette.THEME_IDS.length; i++) {
            final int index = i;
            final Palette themed = Palette.resolve(Palette.THEME_IDS[i], "default");
            View card = ThemeCards.card(c, pal, themed, glow, false);
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    ThemeCards.focus(v, pal, has);
                    if (has) {
                        theme = index;
                        host.previewPalette(themed, false);
                    }
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { theme = index; finish(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Px.of(c, 220), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Px.of(c, 20);
            row.addView(card, lp);
            if (i == theme) first = card;
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Px.of(c, 40);
        col.addView(row, rlp);
        focusLater(first);
        return col;
    }

    private void finish() {
        Profiles.Profile p = new Profiles.Profile();
        p.name = name;
        p.color = color;
        p.theme = Palette.THEME_IDS[theme];
        Profiles ps = Prefs.profiles(c).add(p);
        Prefs.setProfiles(c, ps);
        Prefs.setCurrent(c, ps.get(ps.size() - 1).id);
        host.closePanel();
        host.profileChanged();
    }

    private void focusLater(final View v) {
        if (v == null) return;
        v.post(new Runnable() { public void run() { v.requestFocus(); } });
    }
}
