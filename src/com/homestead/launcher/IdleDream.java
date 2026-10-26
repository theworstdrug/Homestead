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
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.util.Log;


/**
 * Homestead's idle screen, in place of Fire OS's screensaver: the current profile's theme
 * with the last app's glow, the wave of light over it, and the clock. Android starts it on
 * its own timeout; any key ends it and does nothing else.
 */
public class IdleDream extends DreamService {
    static final String COMPONENT = "com.homestead.launcher/.IdleDream";
    private static final String TAG = "Homestead";

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(new IdleScene(this));
    }

    /**
     * Once per install of this version: make Homestead's idle screen the screensaver, when
     * Homestead may write secure settings. Without that grant, Setup does it later.
     */
    static void ensureInstalled(Context c) {
        if (Prefs.idleScreenSet(c)) return;
        if (android.os.Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            String now = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
            // Only in place of Amazon's (or none); a screensaver someone chose stays.
            if (now != null && now.length() > 0 && !now.startsWith("com.amazon")
                    && !now.equals("com.android.dreams.basic/.Colors")) {
                Prefs.setIdleScreenSet(c);
                return;
            }
            Settings.Secure.putString(c.getContentResolver(), "screensaver_components", COMPONENT);
            Settings.Secure.putInt(c.getContentResolver(), "screensaver_enabled", 1);
            Prefs.setIdleScreenSet(c);
            Log.i(TAG, "idle screen set as the screensaver");
        } catch (Throwable t) {
            Log.w(TAG, "could not set the idle screen: " + t);
        }
    }
}
