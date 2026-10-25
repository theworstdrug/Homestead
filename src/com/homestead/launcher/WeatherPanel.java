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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The forecast as a sheet (the weather chip on the home screen opens it): now, the next
 * six hours, and five days with a low-to-high bar; and as a small card for the idle screen.
 */
final class WeatherPanel {
    private WeatherPanel() { }

    static View build(final Context c, final SettingsPanel.Host host) {
        Weather.Forecast f = WeatherStore.get(c);
        final Panel p = new Panel(c, "Weather", Prefs.weatherCity(c), Panel.SHEET);
        Palette pal = p.pal;
        if (f == null) {
            p.addText("No forecast yet. It appears once the TV has reached the weather service.");
        } else {
            LinearLayout now = new LinearLayout(c);
            now.setOrientation(LinearLayout.HORIZONTAL);
            now.setGravity(Gravity.CENTER_VERTICAL);
            now.addView(icon(c, f.now.kind, pal.text, 76));
            LinearLayout nt = new LinearLayout(c);
            nt.setOrientation(LinearLayout.VERTICAL);
            nt.setPadding(Px.of(c, 20), 0, 0, 0);
            nt.addView(text(c, f.now.temp + "°", 64, pal.text, true));
            Weather.Day today = f.days.length > 0 ? f.days[0] : null;
            nt.addView(text(c, f.now.text + (today == null ? "" : "  ·  H " + today.hi + "°  L " + today.lo + "°"),
                    17, pal.sub, false));
            now.addView(nt);
            p.addView(now, Px.of(c, 8));

            LinearLayout hours = card(c, pal, LinearLayout.HORIZONTAL);
            for (Weather.Hour h : f.hours) {
                LinearLayout col = new LinearLayout(c);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.addView(text(c, h.label, 14, pal.sub, false));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Px.of(c, 26), Px.of(c, 26));
                ilp.topMargin = ilp.bottomMargin = Px.of(c, 8);
                col.addView(icon(c, h.kind, pal.text, 26), ilp);
                col.addView(text(c, h.temp + "°", 17, pal.text, true));
                hours.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            p.addView(hours, Px.of(c, 22));

            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (Weather.Day d : f.days) { lo = Math.min(lo, d.lo); hi = Math.max(hi, d.hi); }
            LinearLayout days = card(c, pal, LinearLayout.VERTICAL);
            for (Weather.Day d : f.days) days.addView(dayRow(c, pal, d, lo, hi));
            p.addView(days, Px.of(c, 10));
        }
        p.addRow("Close", "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    /** The idle screen's card: now, then three days. Null when there is no forecast. */
    static View idleCard(Context c, Palette pal) {
        Weather.Forecast f = WeatherStore.get(c);
        if (f == null) return null;
        LinearLayout box = card(c, pal, LinearLayout.VERTICAL);
        box.setPadding(Px.of(c, 26), Px.of(c, 22), Px.of(c, 26), Px.of(c, 22));
        LinearLayout now = new LinearLayout(c);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.addView(icon(c, f.now.kind, pal.text, 40));
        TextView t = text(c, f.now.temp + "°", 40, pal.text, true);
        t.setPadding(Px.of(c, 14), 0, Px.of(c, 14), 0);
        now.addView(t);
        now.addView(text(c, f.now.text, 17, pal.sub, false));
        box.addView(now, wrap());
        LinearLayout days = new LinearLayout(c);
        days.setOrientation(LinearLayout.HORIZONTAL);
        days.setPadding(0, Px.of(c, 16), 0, 0);
        for (int i = 1; i < Math.min(4, f.days.length); i++) {
            Weather.Day d = f.days[i];
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            // Explicit WRAP_CONTENT: a vertical LinearLayout's default MATCH_PARENT lets a
            // wrap-content column shrink to its icon and cut the text.
            col.addView(text(c, d.label, 14, pal.sub, false), wrap());
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Px.of(c, 22), Px.of(c, 22));
            ilp.topMargin = ilp.bottomMargin = Px.of(c, 6);
            col.addView(icon(c, d.kind, pal.text, 22), ilp);
            col.addView(text(c, d.hi + "° " + d.lo + "°", 15, pal.text, true), wrap());
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 1) clp.leftMargin = Px.of(c, 22);
            days.addView(col, clp);
        }
        box.addView(days, wrap());
        return box;
    }

    private static View dayRow(Context c, Palette pal, Weather.Day d, int lo, int hi) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Px.of(c, 40));
        TextView name = text(c, d.label, 17, pal.text, true);
        row.addView(name, new LinearLayout.LayoutParams(Px.of(c, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Px.of(c, 24), Px.of(c, 24));
        ilp.rightMargin = Px.of(c, 14);
        row.addView(icon(c, d.kind, pal.text, 24), ilp);
        TextView l = text(c, d.lo + "°", 17, pal.sub, false);
        l.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(Px.of(c, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        // The day's range on the week's scale.
        FrameLayout track = new FrameLayout(c);
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(Px.f(c, 3));
        tg.setColor(pal.chip);
        track.setBackground(tg);
        View bar = new View(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF5AC8FA, 0xFFFFCC4D});
        bg.setCornerRadius(Px.f(c, 3));
        bar.setBackground(bg);
        final float from = hi == lo ? 0f : (d.lo - lo) / (float) (hi - lo);
        final float to = hi == lo ? 1f : (d.hi - lo) / (float) (hi - lo);
        final View b = bar;
        track.addView(bar, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        track.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l0, int t0, int r0, int b0, int ol, int ot, int or, int ob) {
                int w = r0 - l0;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.getLayoutParams();
                int nw = Math.max(Px.of(v.getContext(), 6), Math.round(w * (to - from)));
                int nl = Math.round(w * from);
                if (lp.width != nw || lp.leftMargin != nl) {
                    lp.width = nw;
                    lp.leftMargin = nl;
                    b.post(new Runnable() { public void run() { b.requestLayout(); } });
                }
            }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, Px.of(c, 6), 1f);
        tlp.leftMargin = tlp.rightMargin = Px.of(c, 12);
        row.addView(track, tlp);
        TextView h = text(c, d.hi + "°", 17, pal.text, true);
        row.addView(h, new LinearLayout.LayoutParams(Px.of(c, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static ImageView icon(Context c, String kind, int color, float size) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new WeatherIcons(kind, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(Px.of(c, size), Px.of(c, size)));
        return v;
    }

    private static LinearLayout card(Context c, Palette pal, int orientation) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(orientation);
        box.setPadding(Px.of(c, 18), Px.of(c, 14), Px.of(c, 18), Px.of(c, 14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Px.f(c, 18));
        g.setColor(pal.chip);
        box.setBackground(g);
        return box;
    }

    private static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, size));
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setSingleLine(true);
        return t;
    }
}
