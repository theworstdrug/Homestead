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

final class MotionTest {
    private MotionTest() { }

    static void run() {
        iosCurveEnds();
        curvesRiseMonotonically();
        iosCurveIsFastEarly();
    }

    static void iosCurveEnds() {
        T.near(0, Motion.iosCurve(0f), 1e-4, "ios start");
        T.near(1, Motion.iosCurve(1f), 1e-4, "ios end");
        T.near(0, Motion.easeOut(0f), 1e-4, "ease start");
        T.near(1, Motion.easeOut(1f), 1e-4, "ease end");
        for (int i = 0; i <= 100; i++) {
            T.yes(Motion.iosCurve(i / 100f) <= 1.006f, "no more than a hair of overshoot at " + i);
        }
    }

    static void curvesRiseMonotonically() {
        float last = -1f;
        for (int i = 0; i <= 100; i++) {
            float v = Motion.iosCurve(i / 100f);
            T.yes(v >= last - 1e-5f, "ios rises at " + i);
            last = v;
        }
    }

    static void iosCurveIsFastEarly() {
        // The point of the curve: most of the travel is done in the first third.
        T.yes(Motion.iosCurve(0.33f) > 0.6f, "ios is front-loaded: " + Motion.iosCurve(0.33f));
    }
}
