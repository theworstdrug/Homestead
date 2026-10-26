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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The quieter rows under the apps: Inputs as pills, and More as small tiles (non-TV apps,
 * Amazon Home, Settings, TV Health). The focused one fills with the theme's focus colour
 * and grows slightly; the row slides when the focus nears its edge.
 */
final class PillRow extends FrameLayout {
    static final int PILLS = 0, TILES = 1;

    private final int kind;
    private final AppRow.Listener listener;
    private final LinearLayout track;
    private final List<View> cells = new ArrayList<View>();
    private final List<TextView> labels = new ArrayList<TextView>();
    private final List<View> dots = new ArrayList<View>();
    private final List<ImageView> icons = new ArrayList<ImageView>();
    private List<AppModel.Item> items = new ArrayList<AppModel.Item>();
    private Palette palette = Palette.resolve(null, null);
    private int selected = -1;
    private boolean rowFocused;

    PillRow(Context c, int kind, AppRow.Listener l) {
        super(c);
        this.kind = kind;
        listener = l;
        setClipChildren(false);
        setClipToPadding(false);
        track = new LinearLayout(c);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setClipChildren(false);
        track.setClipToPadding(false);
        addView(track, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * The track is as long as all its cells, however wide the screen: measured at most the
     * row's width, a row with more cells than fit could only slide 64 px, and the focus
     * went off the edge.
     */
    @Override
    protected void measureChildWithMargins(android.view.View child, int widthSpec, int widthUsed,
                                           int heightSpec, int heightUsed) {
        if (child == track) {
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    getChildMeasureSpec(heightSpec, getPaddingTop() + getPaddingBottom() + heightUsed,
                            child.getLayoutParams().height));
            return;
        }
        super.measureChildWithMargins(child, widthSpec, widthUsed, heightSpec, heightUsed);
    }

    void setItems(List<AppModel.Item> list, Palette p) {
        items = list;
        palette = p;
        track.removeAllViews();
        cells.clear();
        labels.clear();
        dots.clear();
        icons.clear();
        Context c = getContext();
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            AppModel.Item it = list.get(i);
            View cell = kind == PILLS ? buildPill(it) : buildTile(it);
            cell.setFocusable(true);
            cell.setFocusableInTouchMode(true);
            cell.setOnFocusChangeListener(new OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) { if (has) listener.onFocus(index); }
            });
            cell.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { listener.onClick(index); }
            });
            cell.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) { return listener.onLongPress(index); }
            });
            LinearLayout.LayoutParams lp = kind == PILLS
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Px.of(c, 52))
                    : new LinearLayout.LayoutParams(Px.of(c, 176), Px.of(c, 96));
            if (i > 0) lp.leftMargin = Px.of(c, kind == PILLS ? 14 : 18);
            track.addView(cell, lp);
            cells.add(cell);
        }
        if (selected >= cells.size()) selected = cells.size() - 1;
        paint(false);
    }

    int size() { return cells.size(); }

    View cell(int i) { return i >= 0 && i < cells.size() ? cells.get(i) : null; }

    int selected() { return Math.max(0, selected); }

    void select(int index, boolean focused, boolean animate) {
        selected = focused ? index : -1;
        rowFocused = focused;
        if (focused) last = index;
        paint(animate);
        if (focused) slideTo(index, animate);
    }

    /** The cell the focus returns to when it comes back to this row. */
    int remembered() { return Math.min(last, Math.max(0, cells.size() - 1)); }

    private int last;

    void setPalette(Palette p) {
        palette = p;
        paint(false);
    }

    private void paint(boolean animate) {
        for (int i = 0; i < cells.size(); i++) {
            boolean on = rowFocused && i == selected;
            View cell = cells.get(i);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Px.f(getContext(), kind == PILLS ? 26 : 16));
            bg.setColor(on ? palette.chipOn : palette.chip);
            cell.setBackground(bg);
            int fg = on ? palette.chipOnText : palette.text;
            labels.get(i).setTextColor(fg);
            if (kind == PILLS) {
                AppModel.Item it = items.get(i);
                boolean live = it.type == AppModel.TYPE_LIVETV || it.connected;
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(live ? 0xFF3DDC84 : (on ? palette.chipOnText : palette.sub));
                dots.get(i).setBackground(d);
            }
            float s = on ? 1.08f : 1f;
            cell.animate().cancel();
            if (animate) {
                cell.animate().scaleX(s).scaleY(s).setDuration(260).setInterpolator(Px.EASE).start();
            } else {
                cell.setScaleX(s);
                cell.setScaleY(s);
            }
        }
    }

    /** Keep the cell before the focused one in view, and never scroll past the end. */
    private void slideTo(final int index, final boolean animate) {
        if (getWidth() == 0 || cells.isEmpty()) {
            post(new Runnable() { public void run() { if (getWidth() > 0) slideTo(index, false); } });
            return;
        }
        View prev = cells.get(Math.max(0, index - 1));
        int total = track.getWidth();
        int max = Math.max(0, total - getWidth() + Px.of(getContext(), 64));
        int shift = Math.max(0, Math.min(prev.getLeft(), max));
        track.animate().cancel();
        if (animate) {
            track.animate().translationX(-shift).setDuration(Motion.ROW_GLIDE_MS).setInterpolator(Px.EASE).start();
        } else {
            track.setTranslationX(-shift);
        }
    }

    private View buildPill(AppModel.Item it) {
        Context c = getContext();
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(Px.of(c, 24), 0, Px.of(c, 24), 0);
        View dot = new View(c);
        int ds = Px.of(c, 8);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ds, ds);
        dlp.rightMargin = Px.of(c, 10);
        p.addView(dot, dlp);
        dots.add(dot);
        TextView t = label(it.label, 18);
        p.addView(t);
        labels.add(t);
        icons.add(null);
        return p;
    }

    private View buildTile(AppModel.Item it) {
        Context c = getContext();
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setPadding(Px.of(c, 10), Px.of(c, 6), Px.of(c, 10), Px.of(c, 6));
        ImageView ic = null;
        if (it.icon != null || it.banner != null) {
            ic = new ImageView(c);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ic.setImageBitmap(it.icon != null ? it.icon : it.banner);
            int s = Px.of(c, 34);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(it.icon != null ? s : s * 16 / 9, s);
            ilp.bottomMargin = Px.of(c, 6);
            p.addView(ic, ilp);
        }
        icons.add(ic);
        dots.add(null);
        TextView t = label(it.label, ic != null ? 15 : 18);
        t.setGravity(Gravity.CENTER);
        p.addView(t);
        labels.add(t);
        return p;
    }

    private TextView label(String s, float size) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(getContext(), size));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
}
