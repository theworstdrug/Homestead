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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/**
 * The home screen's background: the theme's base colour (or the dimmed photo), two very
 * large soft glows in the focused app's colour, and a darkening gradient towards the
 * bottom. A colour change cross-fades the old glow out and the new one in. There is no
 * blur on Fire OS 8 (RenderEffect is API 31), so the glows are radial gradients whose
 * falloff imitates a heavily blurred ellipse.
 */
final class GlowBackground extends Drawable {
    /** Positions from the 1280x720 mockup: centre, radii and blur of each glow. */
    private static final float[][] GLOWS = {
            {290f, 100f, 450f, 320f, 140f, 1.0f},
            {1130f, 300f, 350f, 260f, 160f, 0.6f},
    };
    private static final float[] STOPS = {0f, 0.35f, 0.6f, 0.8f, 1f};
    private static final float[] FALLOFF = {1f, 0.9f, 0.55f, 0.2f, 0f};

    private final Paint fill = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint layerPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    /** Layer size: 1/6 of 1080p, which the bilinear stretch keeps smooth. */
    private static final int LAYER_W = 320, LAYER_H = 180;
    private Bitmap layerNew, layerOld, scrimLayer;
    private int layerNewColor, layerOldColor, scrimColor;
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix m = new Matrix();

    private Palette palette = Palette.resolve(null, null);
    private Bitmap photo;
    private int photoDim;
    private boolean idleDim;

    /** The glow fading in, the one fading out, and how far the fade has got (0..1). */
    private int glow, oldGlow;
    private float fade = 1f;
    private ValueAnimator fader;

    GlowBackground(Context c) {
        glow = palette.neutral;
    }

    void setPalette(Palette p) {
        boolean wasNeutral = glow == palette.neutral;
        palette = p;
        if (wasNeutral) glow = p.neutral;
        invalidateSelf();
    }

    Palette palette() { return palette; }

    /** A photo under the glow, darkened by dimAlpha (0..255); null for the theme's base colour. */
    void setPhoto(Bitmap b, int dimAlpha) {
        photo = b;
        photoDim = dimAlpha;
        invalidateSelf();
    }

    /** The glow colour; 0 means the palette's neutral colour. */
    void setGlow(int color, boolean animate) {
        int c = color == 0 ? palette.neutral : color;
        if (c == glow && fade >= 1f) return;
        if (fader != null) fader.cancel();
        if (!animate) {
            glow = c;
            fade = 1f;
            invalidateSelf();
            return;
        }
        oldGlow = glow;
        glow = c;
        fade = 0f;
        fader = ValueAnimator.ofFloat(0f, 1f);
        fader.setDuration(Motion.GLOW_MS);
        fader.setInterpolator(new LinearInterpolator());
        fader.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                fade = (Float) a.getAnimatedValue();
                invalidateSelf();
            }
        });
        fader.start();
    }

    int glow() { return glow; }

    /** Softer glows behind the idle screen. */
    void setIdleDim(boolean dim) {
        idleDim = dim;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        if (photo != null) {
            drawPhoto(c, b);
        } else {
            fill.setColor(palette.base);
            c.drawRect(b, fill);
        }
        float strength = palette.glowStrength * (idleDim ? 0.45f : 1f);
        if (fade < 1f) drawLayer(c, b, layer(oldGlow, b, false), strength * (1f - fade));
        drawLayer(c, b, layer(glow, b, true), strength * fade);
        if (!idleDim) drawLayer(c, b, scrim(b), 1f);
    }

    private void drawPhoto(Canvas c, Rect b) {
        float s = Math.max(b.width() / (float) photo.getWidth(), b.height() / (float) photo.getHeight());
        float w = photo.getWidth() * s, h = photo.getHeight() * s;
        RectF dst = new RectF(b.centerX() - w / 2f, b.centerY() - h / 2f, b.centerX() + w / 2f, b.centerY() + h / 2f);
        c.drawBitmap(photo, null, dst, photoPaint);
        if (photoDim > 0) {
            fill.setColor(photoDim << 24);
            c.drawRect(b, fill);
        }
    }

    /** Small pre-rendered layers, stretched to the screen; the content is all soft gradients. */
    private void drawLayer(Canvas c, Rect b, Bitmap layer, float alpha) {
        if (alpha <= 0.003f) return;
        layerPaint.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        c.drawBitmap(layer, null, b, layerPaint);
    }

    private Bitmap layer(int color, Rect b, boolean isNew) {
        if (isNew && layerNew != null && layerNewColor == color) return layerNew;
        if (!isNew && layerOld != null && layerOldColor == color) return layerOld;
        if (!isNew && layerNew != null && layerNewColor == color) return layerNew;
        Bitmap bm = Bitmap.createBitmap(LAYER_W, LAYER_H, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(bm);
        int[] colors = new int[STOPS.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = (Math.round(255 * FALLOFF[i]) << 24) | (color & 0x00FFFFFF);
        }
        Shader sh = new RadialGradient(0f, 0f, 1f, colors, STOPS, Shader.TileMode.CLAMP);
        float s = LAYER_W / 1280f;
        glowPaint.setShader(sh);
        for (float[] g : GLOWS) {
            m.reset();
            m.setScale((g[2] + g[4]) * s, (g[3] + g[4]) * s);
            m.postTranslate(g[0] * s, g[1] * s);
            sh.setLocalMatrix(m);
            glowPaint.setAlpha(Math.round(255 * g[5]));
            lc.drawRect(0, 0, LAYER_W, LAYER_H, glowPaint);
        }
        glowPaint.setShader(null);
        if (isNew) {
            if (layerNew != null && layerNew != layerOld) {
                if (layerOld != null) layerOld.recycle();
                layerOld = layerNew;
                layerOldColor = layerNewColor;
            }
            layerNew = bm;
            layerNewColor = color;
        } else {
            if (layerOld != null && layerOld != layerNew) layerOld.recycle();
            layerOld = bm;
            layerOldColor = color;
        }
        return bm;
    }

    private Bitmap scrim(Rect b) {
        if (scrimLayer != null && scrimColor == palette.scrimTo) return scrimLayer;
        if (scrimLayer != null) scrimLayer.recycle();
        scrimLayer = Bitmap.createBitmap(LAYER_W, LAYER_H, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, LAYER_H * 0.78f,
                palette.scrimTo & 0x00FFFFFF, palette.scrimTo, Shader.TileMode.CLAMP));
        new Canvas(scrimLayer).drawRect(0, 0, LAYER_W, LAYER_H, p);
        scrimColor = palette.scrimTo;
        return scrimLayer;
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
