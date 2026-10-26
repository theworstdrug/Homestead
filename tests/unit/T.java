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

/** Assertions for the JVM unit tests. A failure throws, and UnitTests reports it. */
final class T {
    static int count;

    private T() { }

    static void eq(Object expected, Object actual, String what) {
        count++;
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    static void yes(boolean cond, String what) {
        count++;
        if (!cond) throw new AssertionError(what);
    }

    static void near(double expected, double actual, double tol, String what) {
        count++;
        if (Math.abs(expected - actual) > tol) {
            throw new AssertionError(what + ": expected " + expected + " +/- " + tol + " but was " + actual);
        }
    }

    /** Passes when r throws an exception of type k; returns it. */
    static <E extends Throwable> E fails(Class<E> k, Runnable r, String what) {
        count++;
        try {
            r.run();
        } catch (Throwable t) {
            if (k.isInstance(t)) return k.cast(t);
            throw new AssertionError(what + ": threw " + t + " instead of " + k.getSimpleName());
        }
        throw new AssertionError(what + ": did not throw " + k.getSimpleName());
    }
}
