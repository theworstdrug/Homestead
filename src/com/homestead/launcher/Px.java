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

import android.animation.TimeInterpolator;
import android.content.Context;

/**
 * The home screen is laid out in the design's units: a 1280-wide screen. of() turns one
 * into pixels on this TV, whatever its resolution or density, so the layout matches the
 * mockup at 720p, 1080p and 4K alike.
 */
final class Px {
    private Px() { }

    static int of(Context c, float designPx) {
        return Math.round(designPx * c.getResources().getDisplayMetrics().widthPixels / 1280f);
    }

    static float f(Context c, float designPx) {
        return designPx * c.getResources().getDisplayMetrics().widthPixels / 1280f;
    }

    /** Motion's ease-out for the view animators. */
    static final TimeInterpolator EASE = new TimeInterpolator() {
        public float getInterpolation(float t) { return Motion.easeOut(t); }
    };

    /** Motion's iOS sheet curve for the view animators. */
    static final TimeInterpolator IOS = new TimeInterpolator() {
        public float getInterpolation(float t) { return Motion.iosCurve(t); }
    };
}
