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
 * What hold Select (or Menu) on an app opens: a small frosted card with Open, Hide for
 * this profile, Hide for everyone (the owner only) and Cancel.
 */
final class OptionsMenu {
    interface Actions {
        void open(AppModel.Item it);
        void hide(AppModel.Item it, boolean forEveryone);
        void close();
    }

    private OptionsMenu() { }

    static View build(Context c, final AppModel.Item it, final Actions a) {
        Palette pal = Prefs.palette(c);
        Profiles.Profile me = Prefs.current(c);
        FrameLayout root = new FrameLayout(c);
        root.setClickable(true);
        root.setFocusable(false);
        root.setBackgroundColor(0x59000000);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Px.of(c, 18), Px.of(c, 24), Px.of(c, 18), Px.of(c, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((pal.sheet & 0x00FFFFFF) | 0xF2000000);
        bg.setCornerRadius(Px.f(c, 26));
        card.setBackground(bg);
        card.setElevation(Px.f(c, 20));

        TextView title = new TextView(c);
        title.setText(it.label);
        title.setTextColor(pal.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 26));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(Px.of(c, 12), 0, Px.of(c, 12), Px.of(c, 14));
        card.addView(title);

        View first = button(c, pal, card, c.getString(R.string.options_open), new Runnable() {
            public void run() { a.close(); a.open(it); }
        });
        button(c, pal, card, c.getString(R.string.options_hide_for, me.name), new Runnable() {
            public void run() { a.close(); a.hide(it, false); }
        });
        if (Profiles.canSeeEveryone(me)) {
            button(c, pal, card, c.getString(R.string.options_hide_all), new Runnable() {
                public void run() { a.close(); a.hide(it, true); }
            });
        }
        button(c, pal, card, c.getString(R.string.options_cancel), new Runnable() {
            public void run() { a.close(); }
        });

        root.addView(card, new FrameLayout.LayoutParams(Px.of(c, 400),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ProfilePicker.popIn(card);
        final View f = first;
        root.post(new Runnable() { public void run() { f.requestFocus(); } });
        return root;
    }

    private static View button(final Context c, final Palette pal, LinearLayout card, String label,
                               final Runnable action) {
        final TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 19));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Px.of(c, 18), 0, Px.of(c, 18), 0);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        View.OnFocusChangeListener f = new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                GradientDrawable d = new GradientDrawable();
                d.setCornerRadius(Px.f(c, 14));
                d.setColor(has ? pal.chipOn : 0);
                b.setBackground(d);
                b.setTextColor(has ? pal.chipOnText : pal.text);
            }
        };
        b.setOnFocusChangeListener(f);
        f.onFocusChange(b, false);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, 52));
        lp.topMargin = Px.of(c, 4);
        card.addView(b, lp);
        return b;
    }
}
