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
 * Weather icons drawn in code, on a 24-unit grid: sun, partly (a sun behind a cloud),
 * cloud, rain, snow, fog and storm. Solid shapes and round-capped strokes in one colour.
 */
final class WeatherIcons extends Drawable {
    private final String kind;
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
        } else if (kind.equals("fog")) {
            for (int i = 0; i < 4; i++) c.drawLine(4 + (i % 2) * 2, 7 + i * 3.5f, 20 - (i % 2) * 2, 7 + i * 3.5f, stroke);
        } else {
            boolean low = kind.equals("rain") || kind.equals("snow") || kind.equals("storm");
            cloud(c, 0, low ? -3 : 0, 1f);
            if (kind.equals("rain")) {
                for (int i = 0; i < 3; i++) c.drawLine(8 + i * 4, 17, 7 + i * 4, 21, stroke);
            } else if (kind.equals("snow")) {
                for (int i = 0; i < 3; i++) c.drawCircle(8 + i * 4, 18.5f + (i % 2) * 2, 1.2f, fill);
            } else if (kind.equals("storm")) {
                Path bolt = new Path();
                bolt.moveTo(13, 15); bolt.lineTo(10, 19.5f); bolt.lineTo(12.5f, 19.5f); bolt.lineTo(11, 23);
                c.drawPath(bolt, stroke);
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
