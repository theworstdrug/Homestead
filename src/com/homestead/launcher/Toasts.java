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
import android.widget.TextView;

/**
 * A short message in a frosted pill at the bottom centre, drawn in Homestead's own window
 * so it matches the theme (the system toast is white-on-grey everywhere). It rises in,
 * stays 2.6 s and fades; a new one replaces the one showing.
 */
final class Toasts {
    private static final long SHOW_MS = 2600L;
    private static final String TAG = "homestead-toast";

    private Toasts() { }

    static void show(Context c, ViewGroup host, String text) {
        View old = host.findViewWithTag(TAG);
        if (old != null) host.removeView(old);
        Palette p = Prefs.palette(c);
        final TextView t = new TextView(c);
        t.setTag(TAG);
        t.setText(text);
        t.setTextColor(p.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 17));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setPadding(Px.of(c, 26), Px.of(c, 14), Px.of(c, 26), Px.of(c, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((p.sheet & 0x00FFFFFF) | 0xF0000000);
        bg.setCornerRadius(Px.f(c, 30));
        t.setBackground(bg);
        t.setElevation(Px.f(c, 12));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Px.of(c, 40);
        host.addView(t, lp);
        t.setAlpha(0f);
        t.setTranslationY(Px.f(c, 16));
        t.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Px.IOS).start();
        t.postDelayed(new Runnable() {
            public void run() {
                t.animate().alpha(0f).translationY(Px.f(t.getContext(), 8)).setDuration(260)
                        .withEndAction(new Runnable() {
                            public void run() {
                                ViewGroup parent = (ViewGroup) t.getParent();
                                if (parent != null) parent.removeView(t);
                            }
                        }).start();
            }
        }, SHOW_MS);
    }
}
