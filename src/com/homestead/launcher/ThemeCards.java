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
 * A theme as a small card: a miniature home screen (the theme's base, a glow in the last
 * app's colour, a focused tile and two quiet ones) over the theme's name, with a tick on
 * the one in use. Used by profile creation and Settings → Theme.
 */
final class ThemeCards {
    private ThemeCards() { }

    static View card(Context c, Palette ui, Palette theme, int glow, boolean saved) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Px.of(c, 10), Px.of(c, 10), Px.of(c, 10), Px.of(c, 10));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);

        FrameLayout mini = new FrameLayout(c);
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(Px.f(c, 12));
        base.setColor(theme.base);
        base.setStroke(1, 0x40808080);
        mini.setBackground(base);
        mini.setClipToOutline(true);
        View g = new View(c);
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(Px.f(c, 80));
        int gc = glow == 0 ? theme.neutral : glow;
        int a = Math.round(255 * theme.glowStrength);
        gd.setColors(new int[]{(a << 24) | (gc & 0x00FFFFFF), gc & 0x00FFFFFF});
        g.setBackground(gd);
        mini.addView(g, new FrameLayout.LayoutParams(Px.of(c, 160), Px.of(c, 120)));
        mini.addView(bar(c, theme, true), barLp(c, 14, 70, 40));
        mini.addView(bar(c, theme, false), barLp(c, 94, 40, 23));
        mini.addView(bar(c, theme, false), barLp(c, 142, 40, 23));
        card.addView(mini, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, 104)));

        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Px.of(c, 4), Px.of(c, 12), Px.of(c, 4), Px.of(c, 2));
        TextView name = new TextView(c);
        name.setText(theme.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 18));
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tick = new TextView(c);
        tick.setText(saved ? "✓" : "");
        tick.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 20));
        line.addView(tick);
        card.addView(line);
        card.setTag(new TextView[]{name, tick});
        focus(card, ui, false);
        return card;
    }

    /** Paint a card focused (filled, slightly larger) or not. */
    static void focus(View card, Palette ui, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Px.f(card.getContext(), 18));
        bg.setColor(on ? ui.chipOn : ui.chip);
        card.setBackground(bg);
        for (TextView t : (TextView[]) card.getTag()) t.setTextColor(on ? ui.chipOnText : ui.text);
        float s = on ? 1.05f : 1f;
        card.animate().scaleX(s).scaleY(s).setDuration(240).setInterpolator(Px.EASE).start();
    }

    private static View bar(Context c, Palette t, boolean focused) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Px.f(c, focused ? 7 : 5));
        d.setColor(focused ? 0xFF2A2F3A : t.chip);
        if (focused) d.setStroke(Px.of(c, 2), t.ring);
        v.setBackground(d);
        return v;
    }

    private static FrameLayout.LayoutParams barLp(Context c, float left, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Px.of(c, w), Px.of(c, h),
                Gravity.BOTTOM | Gravity.LEFT);
        lp.leftMargin = Px.of(c, left);
        lp.bottomMargin = Px.of(c, 16);
        return lp;
    }
}
