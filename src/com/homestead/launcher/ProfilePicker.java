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
 * "Who's watching?": every profile as a large avatar, plus Add for the owner. Picking one
 * switches the whole screen to that profile's look and hidden tiles.
 */
final class ProfilePicker {
    private ProfilePicker() { }

    static View build(final Context c, final SettingsPanel.Host host) {
        final Palette pal = Prefs.palette(c);
        FrameLayout root = overlay(c, pal);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(title(c, pal, c.getString(R.string.profiles_who)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        final Profiles ps = Prefs.profiles(c);
        final Profiles.Profile current = Prefs.current(c);
        View focusFirst = null;
        for (int i = 0; i < ps.size(); i++) {
            final Profiles.Profile p = ps.get(i);
            View item = item(c, pal, p.initial(), p.name, p.avatarColor(), false);
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.setCurrent(c, p.id);
                    host.closePanel();
                    host.profileChanged();
                }
            });
            row.addView(item, itemLp(c, i));
            if (p.id.equals(current.id)) focusFirst = item;
        }
        boolean canAdd = Profiles.canSeeEveryone(current) && !Prefs.simpleMode(c) && ps.size() < Profiles.MAX;
        if (canAdd) {
            View add = item(c, pal, "+", c.getString(R.string.profiles_add), 0, true);
            add.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    host.closePanel();
                    host.showPanel(ProfileCreator.build(c, host));
                }
            });
            row.addView(add, itemLp(c, ps.size()));
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Px.of(c, 48);
        col.addView(row, rlp);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        popIn(col);
        if (focusFirst != null) {
            final View f = focusFirst;
            root.post(new Runnable() { public void run() { f.requestFocus(); } });
        }
        return root;
    }

    private static LinearLayout.LayoutParams itemLp(Context c, int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) lp.leftMargin = Px.of(c, 44);
        return lp;
    }

    /** The full-screen frosted backdrop shared by the picker and the creator. */
    static FrameLayout overlay(Context c, Palette pal) {
        FrameLayout root = new FrameLayout(c);
        root.setClickable(true);
        // Clickable makes a view focusable by default from Android 8; the backdrop must not
        // take the focus from the avatars.
        root.setFocusable(false);
        root.setBackgroundColor((pal.overlay & 0x00FFFFFF) | 0xE6000000);
        root.setClipChildren(false);
        return root;
    }

    static TextView title(Context c, Palette pal, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(pal.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 40));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** A large round avatar over a name; dashed and empty for Add. Focus grows it and rings it. */
    static View item(Context c, final Palette pal, String initial, String name, final int color, final boolean dashed) {
        final LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        item.setAlpha(0.7f);
        final TextView circle = new TextView(c);
        circle.setText(initial);
        circle.setGravity(Gravity.CENTER);
        circle.setTextColor(dashed ? pal.sub : 0xFFFFFFFF);
        circle.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, dashed ? 56 : 52));
        circle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int s = Px.of(c, 132);
        item.addView(circle, new LinearLayout.LayoutParams(s, s));
        TextView label = new TextView(c);
        label.setText(name);
        label.setTextColor(pal.text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 20));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Room for the circle to grow on focus without covering the name.
        llp.topMargin = Px.of(c, 22);
        item.addView(label, llp);
        final Context ctx = c;
        View.OnFocusChangeListener f = new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                circle.setBackground(circleBg(ctx, pal, color, dashed, has));
                float sc = has ? 1.1f : 1f;
                item.animate().scaleX(sc).scaleY(sc).alpha(has ? 1f : 0.7f)
                        .setDuration(260).setInterpolator(Px.EASE).start();
            }
        };
        item.setOnFocusChangeListener(f);
        f.onFocusChange(item, false);
        return item;
    }

    /** The avatar circle, inset so its ring (which straddles the edge) is never cut off. */
    static android.graphics.drawable.Drawable circleBg(Context c, Palette pal, int color, boolean dashed, boolean ring) {
        return new android.graphics.drawable.InsetDrawable(circle(c, pal, color, dashed, ring), Px.of(c, 3));
    }

    private static GradientDrawable circle(Context c, Palette pal, int color, boolean dashed, boolean ring) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (dashed) {
            g.setColor(0);
            g.setStroke(Px.of(c, 3), ring ? pal.ring : pal.sub, Px.f(c, 10), Px.f(c, 8));
        } else {
            g.setColor(color);
            if (ring) g.setStroke(Px.of(c, 4), pal.ring);
        }
        return g;
    }

    static void popIn(View v) {
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(Motion.POP_MS).setInterpolator(Px.IOS).start();
    }
}
