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
 * Runs the JVM unit tests for Homestead's Android-free classes (tools/unit-tests.sh).
 * Each test class has a static run(); the first failure stops the run with exit code 1.
 */
public final class UnitTests {
    private UnitTests() { }

    public static void main(String[] args) {
        try {
            T.eq(2, 1 + 1, "runner");
        } catch (Throwable t) {
            System.out.println("FAIL " + t.getMessage());
            t.printStackTrace(System.out);
            System.exit(1);
        }
        System.out.println("OK " + T.count + " tests");
    }
}
