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

/**
 * Timing and easing shared by every animation. The curves are cubic Béziers evaluated
 * directly, so they behave the same on every Fire OS version (PathInterpolator is API 21,
 * but this keeps the maths testable on the JVM). Durations are before the system's
 * animator scale, which ValueAnimator applies itself.
 */
final class Motion {
    static final long FOCUS_MS = 300, ROW_GLIDE_MS = 420, ROWS_MS = 480, GREET_MS = 380;
    static final long PANEL_IN_MS = 550, PANEL_OUT_MS = 300, POP_MS = 420, HOLD_MS = 550;
    static final long GLOW_MS = 900;

    private Motion() { }

    /** The iOS sheet curve, cubic-bezier(.32,.72,0,1): quick start, long soft settle. */
    static float iosCurve(float t) { return bezier(t, 0.32f, 0.72f, 0f, 1f); }

    /** A general ease-out, cubic-bezier(.2,.8,.2,1), for focus and scrolling. */
    static float easeOut(float t) { return bezier(t, 0.2f, 0.8f, 0.2f, 1f); }

    /** y at the x = t of the curve through (0,0), (x1,y1), (x2,y2), (1,1). */
    static float bezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        // Solve x(u) = t for u by Newton's method, falling back to bisection.
        float u = t;
        for (int i = 0; i < 8; i++) {
            float x = cubic(u, x1, x2) - t;
            float dx = slope(u, x1, x2);
            if (Math.abs(x) < 1e-5f) return cubic(u, y1, y2);
            if (Math.abs(dx) < 1e-6f) break;
            u -= x / dx;
        }
        float lo = 0f, hi = 1f;
        u = t;
        for (int i = 0; i < 30; i++) {
            float x = cubic(u, x1, x2);
            if (Math.abs(x - t) < 1e-5f) break;
            if (x < t) lo = u; else hi = u;
            u = (lo + hi) / 2f;
        }
        return cubic(u, y1, y2);
    }

    private static float cubic(float u, float p1, float p2) {
        float v = 1f - u;
        return 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u;
    }

    private static float slope(float u, float p1, float p2) {
        float v = 1f - u;
        return 3f * v * v * p1 + 6f * v * u * (p2 - p1) + 3f * u * u * (1f - p2);
    }
}
