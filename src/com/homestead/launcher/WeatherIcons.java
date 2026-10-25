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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Weather icons drawn in code, on a 24-unit grid: sun, moon, partly (a sun behind a
 * cloud), partlyNight (the moon behind one), cloud, rain, snow, fog and storm. Solid shapes and round-capped strokes in one colour.
 */
final class WeatherIcons extends Drawable {
    private final String kind;
    /** 0..1, looping, while animated: drops fall, flakes drift. */
    private float phase;
    private android.animation.ValueAnimator anim;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    WeatherIcons(String kind, int color) {
        this.kind = kind == null ? "cloud" : kind;
        fill.setColor(color);
        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float u = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * u) / 2f, b.top + (b.height() - 24 * u) / 2f);
        c.scale(u, u);
        stroke.setStrokeWidth(2f);
        if (kind.equals("sun")) {
            sun(c, 12, 12, 4.5f, true);
        } else if (kind.equals("partly")) {
            sun(c, 8.5f, 8.5f, 3.5f, true);
            cloud(c, 2.5f, 6f, 0.8f);
        } else if (kind.equals("moon")) {
            moon(c, 12, 12, 7.5f);
        } else if (kind.equals("partlyNight")) {
            moon(c, 9, 8.5f, 5f);
            cloud(c, 2.5f, 6f, 0.8f);
        } else if (kind.equals("fog")) {
            for (int i = 0; i < 4; i++) c.drawLine(4 + (i % 2) * 2, 7 + i * 3.5f, 20 - (i % 2) * 2, 7 + i * 3.5f, stroke);
        } else {
            boolean low = kind.equals("rain") || kind.equals("snow") || kind.equals("storm");
            cloud(c, 0, low ? -3 : 0, 1f);
            if (kind.equals("rain") || kind.equals("storm")) {
                rain(c);
                // A storm's bolt flashes now and then while animated, else it stays drawn.
                boolean flash = anim == null || (phase > 0.62f && phase < 0.7f) || (phase > 0.74f && phase < 0.78f);
                if (kind.equals("storm") && flash) {
                    Path bolt = new Path();
                    bolt.moveTo(13, 15); bolt.lineTo(10, 19.5f); bolt.lineTo(12.5f, 19.5f); bolt.lineTo(11, 23);
                    c.drawPath(bolt, stroke);
                }
            } else if (kind.equals("snow")) {
                snow(c);
            }
        }
        c.restore();
    }

    private void sun(Canvas c, float x, float y, float r, boolean rays) {
        c.drawCircle(x, y, r, fill);
        if (!rays) return;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float ix = (float) (x + Math.cos(a) * (r + 2)), iy = (float) (y + Math.sin(a) * (r + 2));
            float ox = (float) (x + Math.cos(a) * (r + 4)), oy = (float) (y + Math.sin(a) * (r + 4));
            c.drawLine(ix, iy, ox, oy, stroke);
        }
    }

    /** Three drops under the cloud; when animated they fall and fade, one after another. */
    private void rain(Canvas c) {
        int a0 = stroke.getAlpha();
        for (int i = 0; i < 3; i++) {
            float x = 8 + i * 4;
            if (anim == null) {
                c.drawLine(x, 17, x - 1, 21, stroke);
                continue;
            }
            float p = (phase * 2f + i / 3f) % 1f;
            float y = 15.5f + p * 7f;
            stroke.setAlpha(Math.round(a0 * (1f - p)));
            c.drawLine(x - p, y, x - p - 0.7f, y + 2.5f, stroke);
        }
        stroke.setAlpha(a0);
    }

    /** Three flakes under the cloud; when animated they drift down and sway. */
    private void snow(Canvas c) {
        int a0 = fill.getAlpha();
        for (int i = 0; i < 3; i++) {
            float x = 8 + i * 4;
            if (anim == null) {
                c.drawCircle(x, 18.5f + (i % 2) * 2, 1.2f, fill);
                continue;
            }
            float p = (phase + i / 3f) % 1f;
            float sway = (float) Math.sin((phase + i * 0.3f) * Math.PI * 4) * 0.8f;
            fill.setAlpha(Math.round(a0 * (1f - p * p)));
            c.drawCircle(x + sway, 16f + p * 7f, 1.2f, fill);
        }
        fill.setAlpha(a0);
    }

    /** Rain, snow and storms move; anything else is still. Stop it when the icon goes. */
    void setAnimated(boolean on) {
        boolean moves = kind.equals("rain") || kind.equals("snow") || kind.equals("storm");
        if (anim != null) { anim.cancel(); anim = null; }
        if (!on || !moves) { invalidateSelf(); return; }
        anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(kind.equals("snow") ? 4000 : 1600);
        anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                invalidateSelf();
            }
        });
        anim.start();
    }

    /** A crescent: a disc with an offset disc taken out of it. */
    private void moon(Canvas c, float x, float y, float r) {
        Path disc = new Path();
        disc.addCircle(x, y, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(x + r * 0.55f, y - r * 0.35f, r * 0.85f, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        c.drawPath(disc, fill);
    }

    /** A cloud silhouette: three overlapping circles on a rounded base. */
    private void cloud(Canvas c, float dx, float dy, float s) {
        c.save();
        c.translate(dx, dy);
        c.scale(s, s, 12, 14);
        c.drawCircle(9, 13, 4.5f, fill);
        c.drawCircle(14, 11, 5.5f, fill);
        c.drawCircle(18, 14.5f, 3.5f, fill);
        c.drawRoundRect(4.5f, 13, 21.5f, 18, 2.5f, 2.5f, fill);
        c.restore();
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); stroke.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
