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

import java.util.ArrayList;
import java.util.List;

/**
 * Across the top of the home screen: the greeting and a line of flavour text on the left,
 * which step aside when the focus moves below the apps; on the right the weather chip,
 * the date, the clock and the profile avatar. The chip and the avatar take focus.
 */
final class TopBar extends FrameLayout {
    interface Listener {
        void onTopFocus(View v);
        void onAvatar();
        void onWeather();
    }

    private final LinearLayout head;
    private final TextView greet, flavor, date, clock, weather, avatar;
    private Palette palette = Palette.resolve(null, null);
    private int avatarColor = Profiles.AVATAR_COLORS[0];
    private boolean headHidden;
    private View focused;

    TopBar(Context c, final Listener l) {
        super(c);
        setClipChildren(false);
        head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        greet = text(34, "sans-serif-medium");
        flavor = text(17, "sans-serif");
        // Explicit WRAP_CONTENT: a vertical LinearLayout's default is MATCH_PARENT, which
        // a wrap-content parent leaves out of its own width, clipping the greeting.
        head.addView(greet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Px.of(c, 2);
        head.addView(flavor, flp);
        LayoutParams hlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        hlp.leftMargin = Px.of(c, 64);
        hlp.topMargin = Px.of(c, 30);
        addView(head, hlp);

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        weather = text(19, "sans-serif-medium");
        weather.setGravity(Gravity.CENTER);
        weather.setPadding(Px.of(c, 16), 0, Px.of(c, 16), 0);
        weather.setVisibility(GONE);
        date = text(18, "sans-serif");
        clock = text(22, "sans-serif-medium");
        avatar = text(20, "sans-serif-medium");
        avatar.setGravity(Gravity.CENTER);
        int gap = Px.of(c, 14);
        right.addView(weather, rlp(ViewGroup.LayoutParams.WRAP_CONTENT, Px.of(c, 44), 0));
        right.addView(date, rlp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gap));
        right.addView(clock, rlp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gap));
        right.addView(avatar, rlp(Px.of(c, 48), Px.of(c, 48), Px.of(c, 20)));
        LayoutParams rp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        rp.rightMargin = Px.of(c, 44);
        rp.topMargin = Px.of(c, 30);
        addView(right, rp);

        for (final TextView v : new TextView[]{weather, avatar}) {
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
            v.setOnFocusChangeListener(new OnFocusChangeListener() {
                public void onFocusChange(View view, boolean has) {
                    focused = has ? view : (focused == view ? null : focused);
                    paint();
                    if (has) l.onTopFocus(view);
                }
            });
        }
        avatar.setOnClickListener(new OnClickListener() { public void onClick(View v) { l.onAvatar(); } });
        weather.setOnClickListener(new OnClickListener() { public void onClick(View v) { l.onWeather(); } });
    }

    private LinearLayout.LayoutParams rlp(int w, int h, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = left;
        return lp;
    }

    private TextView text(float size, String face) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(getContext(), size));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    void setTexts(String greeting, String flavorLine, String dateText, String clockText) {
        greet.setText(greeting);
        flavor.setText(flavorLine);
        head.setVisibility(greeting == null ? GONE : VISIBLE);
        date.setText(dateText == null ? "" : dateText);
        date.setVisibility(dateText == null ? GONE : VISIBLE);
        clock.setText(clockText == null ? "" : clockText);
        clock.setVisibility(clockText == null ? GONE : VISIBLE);
    }

    void setProfile(Profiles.Profile p) {
        avatar.setText(p.initial());
        avatarColor = p.avatarColor();
        paint();
    }

    /** The temperature chip; null hides it. kind is the icon (filled in with the weather task). */
    void setWeather(String tempText, String kind) {
        weather.setText(tempText == null ? "" : tempText);
        weather.setVisibility(tempText == null ? GONE : VISIBLE);
    }

    void setPalette(Palette p) {
        palette = p;
        paint();
    }

    /** The greeting fades and lifts away while the focus is below the apps row. */
    void setHeadHidden(boolean hide, boolean animate) {
        if (hide == headHidden && animate) return;
        headHidden = hide;
        head.animate().cancel();
        float a = hide ? 0f : 1f, y = hide ? -Px.f(getContext(), 24) : 0f;
        if (animate) {
            head.animate().alpha(a).translationY(y).setDuration(Motion.GREET_MS).setInterpolator(Px.EASE).start();
        } else {
            head.setAlpha(a);
            head.setTranslationY(y);
        }
    }

    /** Focusable items left to right: the weather chip when shown, then the avatar. */
    List<View> focusables() {
        List<View> out = new ArrayList<View>();
        if (weather.getVisibility() == VISIBLE) out.add(weather);
        out.add(avatar);
        return out;
    }

    View avatar() { return avatar; }

    private void paint() {
        greet.setTextColor(palette.text);
        flavor.setTextColor(palette.sub);
        date.setTextColor(palette.sub);
        clock.setTextColor(palette.text);
        weather.setTextColor(palette.text);

        GradientDrawable a = new GradientDrawable();
        a.setShape(GradientDrawable.OVAL);
        a.setColor(avatarColor);
        a.setStroke(Px.of(getContext(), focused == avatar ? 3 : 2), focused == avatar ? palette.ring : palette.sub);
        avatar.setBackground(a);
        avatar.setTextColor(0xFFFFFFFF);

        GradientDrawable w = new GradientDrawable();
        w.setCornerRadius(Px.f(getContext(), 22));
        w.setColor(palette.chip);
        if (focused == weather) w.setStroke(Px.of(getContext(), 3), palette.ring);
        weather.setBackground(w);

        for (View v : new View[]{avatar, weather}) {
            float s = v == focused ? 1.12f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(240).setInterpolator(Px.EASE).start();
        }
    }
}
