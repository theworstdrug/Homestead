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
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A remote-friendly keyboard: A to Z in rows of ten, then Space, Del and a wide Next. The
 * D-pad moves between keys and Select types; a hardware keyboard's letters, Space and
 * Backspace work too. Next stays dim until there is something to submit.
 */
final class OnScreenKeyboard extends LinearLayout {
    interface Listener {
        void onChange(String text);
        void onDone(String text);
    }

    private static final String SPACE = "Space", DEL = "Del", NEXT = "Next";
    private static final int COLS = 10;

    private final Listener listener;
    private final int maxLen;
    private final Palette pal;
    private final List<TextView> keys = new ArrayList<TextView>();
    private TextView next;
    private String text = "";

    OnScreenKeyboard(Context c, int maxLen, String doneLabel, Listener l) {
        super(c);
        this.listener = l;
        this.maxLen = maxLen;
        this.pal = Prefs.palette(c);
        setOrientation(VERTICAL);
        setClipChildren(false);
        List<String> all = new ArrayList<String>();
        for (char ch = 'A'; ch <= 'Z'; ch++) all.add(String.valueOf(ch));
        all.add(SPACE);
        all.add(DEL);
        all.add(NEXT);
        LinearLayout row = null;
        int col = 0;
        for (int i = 0; i < all.size(); i++) {
            if (row == null || col == COLS) {
                row = new LinearLayout(c);
                row.setOrientation(HORIZONTAL);
                row.setClipChildren(false);
                LayoutParams rlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Px.of(c, 56));
                if (getChildCount() > 0) rlp.topMargin = Px.of(c, 8);
                addView(row, rlp);
                col = 0;
            }
            final String k = all.get(i);
            TextView key = key(k.equals(NEXT) ? doneLabel : k);
            boolean wide = k.equals(NEXT);
            LayoutParams lp = new LayoutParams(Px.of(c, wide ? 132 : 62), ViewGroup.LayoutParams.MATCH_PARENT);
            if (col > 0) lp.leftMargin = Px.of(c, 8);
            row.addView(key, lp);
            key.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { press(k); }
            });
            keys.add(key);
            if (wide) next = key;
            col += wide ? 2 : 1;
        }
        paintAll();
    }

    String text() { return text; }

    void setText(String t) {
        text = t == null ? "" : t;
        paintAll();
    }

    private void press(String k) {
        if (k.equals(NEXT)) {
            if (!text.trim().isEmpty()) listener.onDone(text.trim());
            return;
        }
        if (k.equals(DEL)) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
        } else if (k.equals(SPACE)) {
            if (!text.isEmpty() && !text.endsWith(" ") && text.length() < maxLen) text += " ";
        } else if (text.length() < maxLen) {
            boolean upper = text.isEmpty() || text.endsWith(" ");
            text += upper ? k : k.toLowerCase();
        }
        paintAll();
        listener.onChange(text);
    }

    /** Typing on a hardware keyboard: letters, Space and Backspace. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) {
                press(String.valueOf((char) ('A' + code - KeyEvent.KEYCODE_A)));
                return true;
            }
            if (code == KeyEvent.KEYCODE_SPACE) { press(SPACE); return true; }
            if (code == KeyEvent.KEYCODE_DEL && !text.isEmpty()) { press(DEL); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    private TextView key(String label) {
        final TextView t = new TextView(getContext());
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(getContext(), label.length() > 1 ? 16 : 20));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { paint(t); }
        });
        return t;
    }

    private void paintAll() {
        for (TextView k : keys) paint(k);
    }

    private void paint(TextView k) {
        boolean on = k.isFocused();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Px.f(getContext(), 12));
        bg.setColor(on ? pal.chipOn : pal.chip);
        k.setBackground(bg);
        k.setTextColor(on ? pal.chipOnText : pal.text);
        k.setAlpha(k == next && text.trim().isEmpty() ? 0.4f : 1f);
        float s = on ? 1.1f : 1f;
        k.animate().scaleX(s).scaleY(s).setDuration(160).setInterpolator(Px.EASE).start();
    }
}
