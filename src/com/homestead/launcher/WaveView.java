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
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The idle screen's light: Wave's two ribbons of fine lines, a soft glow under each, and
 * faint sparkles drifting along the first, redrawn every frame. Light themes draw dark ink
 * with normal blending; dark themes add light, as out-of-focus light does.
 */
final class WaveView extends View {
    private static final float STEP = 16f;
    private static final int SPARKS = 60;

    private final Paint line = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spark = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Segments for drawLines: x0,y0,x1,y1 per segment. Paths re-tessellate on the CPU every
     *  frame, which ran at 4 fps on a Fire TV; plain line segments go straight to the GPU. */
    private final float[] seg = new float[(int) (1360 / STEP + 2) * 4];
    private int segLen;
    private final float[] sx = new float[SPARKS], off = new float[SPARKS], r = new float[SPARKS];
    private final float[] ph = new float[SPARKS], sp = new float[SPARKS], tw = new float[SPARKS];
    private final long start = SystemClock.uptimeMillis();
    private int ink = 0xFFFFFF;
    private float k = 1f;
    private boolean running;
    private int frame;

    WaveView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Px.f(c, 1.1f));
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        Random rnd = new Random(7);
        for (int i = 0; i < SPARKS; i++) {
            sx[i] = rnd.nextFloat() * 1320f;
            off[i] = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * 90f;
            r[i] = 0.6f + rnd.nextFloat() * 1.8f;
            ph[i] = rnd.nextFloat() * 6.28f;
            sp[i] = 6f + rnd.nextFloat() * 16f;
            tw[i] = 0.8f + rnd.nextFloat() * 2.2f;
        }
        setPalette(Palette.resolve(null, null));
    }

    void setPalette(Palette p) {
        ink = p.waveInk & 0x00FFFFFF;
        k = p.darkInk ? 0.75f : 1f;
        PorterDuffXfermode mode = null;
        line.setXfermode(mode);
        glow.setXfermode(mode);
        spark.setXfermode(mode);
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0) return;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        float s = w / 1280f;
        int design = Math.round(h / s);
        for (int sheet = 0; sheet < Wave.SHEETS; sheet++) {
            // The glow under the ribbon: wide soft strokes along its centre line.
            // The glow moves slowly; every other frame is enough, and halves its cost.
            if ((frame & 1) == 0 || glowBmp == null) glowLayer(sheet, t, design, sheet == 0);
            int n = Wave.lines(sheet);
            for (int j = 0; j < n; j++) {
                float u = j / (float) (n - 1) - 0.5f;
                build(sheet, u, t, s, design);
                line.setColor(argb((Wave.alpha(sheet) * (1 - Math.abs(u) * 1.5f) + 0.025f) * k, ink));
                c.drawLines(seg, 0, segLen, line);
            }
        }
        if (glowBmp != null) c.drawBitmap(glowBmp, null, new android.graphics.Rect(0, 0, (int) w, (int) h), glowBlit);
        for (int i = 0; i < SPARKS; i++) {
            float x = ((sx[i] + t * sp[i]) % 1340f) - 20f;
            float y = Wave.y(0, x, 0f, t, design) + off[i];
            float b = 0.5f + 0.5f * (float) Math.sin(t * tw[i] + ph[i]);
            float a = (0.05f + 0.3f * b * b * b) * k;
            spark.setColor(argb(a * 0.35f, ink));
            c.drawCircle(x * s, y * s, r[i] * 4f * s, spark);
            spark.setColor(argb(a, ink));
            c.drawCircle(x * s, y * s, r[i] * 1.4f * s, spark);
        }
        frame++;
        if (running) postInvalidateOnAnimation();
    }

    /**
     * The soft light under each ribbon, drawn every frame into a bitmap an eighth of the
     * screen's size and stretched over it: a few strokes on a tiny canvas cost almost
     * nothing, and the stretch blurs them, which is the look wanted.
     */
    private void glowLayer(int sheet, float t, int design, boolean clear) {
        if (glowBmp == null) {
            glowBmp = android.graphics.Bitmap.createBitmap(160, 90, android.graphics.Bitmap.Config.ARGB_8888);
            glowCanvas = new Canvas(glowBmp);
        }
        if (clear) glowBmp.eraseColor(0);
        float gs = 160f / 1280f;
        segLen = 0;
        float px = -40, py = Wave.y(sheet, px, 0f, t, design) * gs;
        for (float x = -40 + STEP * 2; x <= 1320; x += STEP * 2) {
            float y = Wave.y(sheet, x, 0f, t, design) * gs;
            seg[segLen++] = px * gs; seg[segLen++] = py; seg[segLen++] = x * gs; seg[segLen++] = y;
            px = x; py = y;
        }
        for (int pass = 0; pass < 3; pass++) {
            glow.setStrokeWidth((80 - pass * 26) * gs);
            glow.setColor(argb(0.05f * k, ink));
            glowCanvas.drawLines(seg, 0, segLen, glow);
        }
    }

    private android.graphics.Bitmap glowBmp;
    private Canvas glowCanvas;
    private final Paint glowBlit = new Paint(Paint.FILTER_BITMAP_FLAG);

    /** One line of a ribbon as segments, from just off the left edge to just off the right. */
    private void build(int sheet, float u, float t, float s, int design) {
        segLen = 0;
        float px = -20, py = Wave.y(sheet, px, u, t, design) * s;
        for (float x = -20 + STEP; x <= 1300; x += STEP) {
            float y = Wave.y(sheet, x, u, t, design) * s;
            seg[segLen++] = px * s;
            seg[segLen++] = py;
            seg[segLen++] = x * s;
            seg[segLen++] = y;
            px = x;
            py = y;
        }
    }

    private static int argb(float a, int rgb) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | rgb;
    }
}
