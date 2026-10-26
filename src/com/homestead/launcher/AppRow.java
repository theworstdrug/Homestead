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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * "Your apps": one row of banner tiles. The selected tile is large and the rest small and
 * dimmed; the row glides so the selected tile sits second from the left. Outlined rank
 * numbers stand beside the tiles, Netflix Top 10 style. Everything moves with translation
 * and scale only, so a focus move costs no layout pass.
 */
final class AppRow extends FrameLayout {
    /** Design units (1280-wide screen). */
    static final float NORMAL_W = 212, NORMAL_H = 119, FOCUS_W = 400, FOCUS_H = 225;
    static final float GAP = 22, RANK_ADV = 66, RANK_OVERLAP = 26, RADIUS = 14;
    static final float HEIGHT = 250;
    /** Unselected rank numbers are drawn at this fraction of the selected size (132/200). */
    static final float RANK_SMALL = 0.66f;

    interface Listener {
        void onFocus(int index);
        void onClick(int index);
        boolean onLongPress(int index);
    }

    private final Listener listener;
    private final FrameLayout track;
    private final List<View> tiles = new ArrayList<View>();
    private final List<RankView> ranks = new ArrayList<RankView>();
    private List<AppModel.Item> items = new ArrayList<AppModel.Item>();
    private Palette palette = Palette.resolve(null, null);
    private boolean showRanks;
    private int selected;
    private boolean rowFocused;
    private final int normalW, normalH, focusW, focusH, gap, rankAdv, rankOverlap;
    private final float grow;

    AppRow(Context c, Listener l) {
        super(c);
        listener = l;
        setClipChildren(false);
        setClipToPadding(false);
        normalW = Px.of(c, NORMAL_W);
        normalH = Px.of(c, NORMAL_H);
        focusW = Px.of(c, FOCUS_W);
        focusH = Px.of(c, FOCUS_H);
        gap = Px.of(c, GAP);
        rankAdv = Px.of(c, RANK_ADV);
        rankOverlap = Px.of(c, RANK_OVERLAP);
        grow = FOCUS_W / NORMAL_W;
        track = new FrameLayout(c);
        track.setClipChildren(false);
        track.setClipToPadding(false);
        addView(track, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Rebuild the tiles. firstRanked: the index of the first item that gets a rank number. */
    void setItems(List<AppModel.Item> list, boolean ranksOn, int firstRanked, Palette p) {
        items = list;
        palette = p;
        showRanks = ranksOn;
        track.removeAllViews();
        tiles.clear();
        ranks.clear();
        for (int i = 0; i < list.size(); i++) {
            final AppModel.Item it = list.get(i);
            final int index = i;
            RankView r = null;
            if (ranksOn && i >= firstRanked) {
                r = new RankView(getContext(), String.valueOf(i - firstRanked + 1));
                // As wide as the number at its large size: "10" and up need far more room than "1".
                LayoutParams rlp = new LayoutParams(r.textWidth() + Px.of(getContext(), 8), focusH,
                        Gravity.BOTTOM | Gravity.LEFT);
                track.addView(r, rlp);
            }
            ranks.add(r);
            View t = buildTile(it);
            t.setFocusable(true);
            t.setFocusableInTouchMode(true);
            t.setOnFocusChangeListener(new OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    if (has) listener.onFocus(index);
                }
            });
            t.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { listener.onClick(index); }
            });
            t.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) { return listener.onLongPress(index); }
            });
            t.setPivotX(0f);
            t.setPivotY(normalH);
            track.addView(t, new LayoutParams(normalW, normalH, Gravity.BOTTOM | Gravity.LEFT));
            tiles.add(t);
        }
        selected = Math.min(selected, Math.max(0, list.size() - 1));
        apply(false);
    }

    int size() { return tiles.size(); }

    View tile(int i) { return i >= 0 && i < tiles.size() ? tiles.get(i) : null; }

    int selected() { return selected; }

    /** Select index; rowFocused says whether the focus is in this row (else it is dimmer). */
    void select(int index, boolean focused, boolean animate) {
        if (tiles.isEmpty()) return;
        selected = Math.max(0, Math.min(tiles.size() - 1, index));
        rowFocused = focused;
        apply(animate);
    }

    void setPalette(Palette p) {
        palette = p;
        for (RankView r : ranks) if (r != null) r.invalidate();
        apply(false);
    }

    private void apply(boolean animate) {
        int x = 0;
        int selLeft = 0;
        int prevStart = 0;
        for (int i = 0; i < tiles.size(); i++) {
            boolean sel = i == selected;
            if (i == selected - 1) prevStart = x;
            RankView r = ranks.get(i);
            int left = x + (r != null ? rankAdvance(r, sel) : 0);
            if (sel) selLeft = left;
            View t = tiles.get(i);
            float scale = sel ? grow : 1f;
            float alpha = rowFocused ? (sel ? 1f : 0.5f) : (sel ? 0.85f : 0.4f);
            move(t, left, scale, alpha, animate);
            t.setForeground(sel && rowFocused ? ring(scale) : null);
            if (r != null) {
                r.setPivotX(r.getLayoutParams().width);
                r.setPivotY(focusH);
                r.selected = sel;
                r.invalidate();
                move(r, left + rankOverlap - r.getLayoutParams().width, sel ? 1f : RANK_SMALL, alpha, animate);
            }
            x = left + (sel ? focusW : normalW) + gap;
        }
        int total = x - gap;
        // Glide so the tile before the selected one starts at the left edge.
        int view = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int shift = Math.max(0, Math.min(prevStart, total - view + Px.of(getContext(), 64)));
        if (selLeft - shift < 0) shift = Math.max(0, selLeft);
        track.animate().cancel();
        if (animate) {
            track.animate().translationX(-shift).setDuration(Motion.ROW_GLIDE_MS).setInterpolator(Px.EASE).start();
        } else {
            track.setTranslationX(-shift);
        }
    }

    /** Room a rank number takes left of its tile: its drawn width, less the overlap. */
    private int rankAdvance(RankView r, boolean sel) {
        int w = Math.round(r.textWidth() * (sel ? 1f : RANK_SMALL)) - rankOverlap + Px.of(getContext(), 8);
        return Math.max(rankAdv, w);
    }

    private static void move(View v, float x, float scale, float alpha, boolean animate) {
        v.animate().cancel();
        if (animate) {
            v.animate().translationX(x).scaleX(scale).scaleY(scale).alpha(alpha)
                    .setDuration(Motion.FOCUS_MS).setInterpolator(Px.EASE).start();
        } else {
            v.setTranslationX(x);
            v.setScaleX(scale);
            v.setScaleY(scale);
            v.setAlpha(alpha);
        }
    }

    /** The focus ring, drawn thinner by the scale so it shows at the same width as elsewhere. */
    private GradientDrawable ring(float scale) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Px.f(getContext(), RADIUS));
        d.setStroke(Math.max(1, Math.round(Px.f(getContext(), 3) / scale)), palette.ring);
        d.setColor(Color.TRANSPARENT);
        return d;
    }

    private View buildTile(AppModel.Item it) {
        final float radius = Px.f(getContext(), RADIUS);
        FrameLayout t = new FrameLayout(getContext());
        t.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        t.setClipToOutline(true);
        if (it.type == AppModel.TYPE_LIVETV) {
            GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF168494, 0xFF0A3642});
            t.setBackground(card);
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            if (it.glyph != 0) {
                ImageView ic = new ImageView(getContext());
                ic.setImageResource(it.glyph);
                ic.setColorFilter(0xFFBFE9EE, android.graphics.PorterDuff.Mode.SRC_IN);
                int s = Px.of(getContext(), 30);
                col.addView(ic, new LinearLayout.LayoutParams(s, s));
            }
            TextView name = new TextView(getContext());
            name.setText(it.label);
            name.setTextColor(Color.WHITE);
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Px.f(getContext(), 18));
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            col.addView(name);
            t.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (it.banner != null) {
            ImageView img = new ImageView(getContext());
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            img.setImageBitmap(it.banner);
            t.addView(img, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            t.setBackgroundColor(0xFF1C2433);
            TextView name = new TextView(getContext());
            name.setText(it.label);
            name.setTextColor(Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Px.f(getContext(), 18));
            t.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return t;
    }

    /** An outlined number, right-aligned, sitting on the bottom edge. */
    private final class RankView extends View {
        final String text;
        boolean selected;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RankView(Context c, String text) {
            super(c);
            this.text = text;
            paint.setStyle(Paint.Style.STROKE);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(Px.f(c, 200));
            paint.setLetterSpacing(-0.04f);
        }

        int textWidth() { return (int) Math.ceil(paint.measureText(text) + paint.getStrokeWidth() * 2); }

        @Override protected void onDraw(Canvas c) {
            paint.setStrokeWidth(Px.f(getContext(), 3) / (selected ? 1f : RANK_SMALL));
            paint.setColor(selected ? palette.text : palette.rank);
            c.drawText(text, getWidth() - paint.getStrokeWidth(), getHeight() - Px.f(getContext(), 2), paint);
        }
    }
}
