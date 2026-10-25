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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Homestead's idle screen, in place of Fire OS's screensaver: the current profile's theme
 * with the last app's glow, the wave of light over it, and the clock. Android starts it on
 * its own timeout; any key ends it and does nothing else.
 */
public class IdleDream extends DreamService {
    static final String COMPONENT = "com.homestead.launcher/.IdleDream";
    private static final String TAG = "Homestead";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock;
    /** Filled in by the weather: the frosted forecast card, bottom left. */
    FrameLayout forecastSlot;

    private Palette pal;

    private final Runnable tick = new Runnable() {
        public void run() {
            updateClock();
            // The forecast keeps up while the idle screen shows (at most every 30 minutes).
            WeatherStore.refreshIfStale(IdleDream.this, new Runnable() {
                public void run() { showForecast(); }
            });
            handler.postDelayed(this, 60000L - System.currentTimeMillis() % 60000L + 50L);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        Profiles.Profile p = Prefs.current(this);
        pal = Palette.resolve(p.theme, p.accent);
        FrameLayout root = new FrameLayout(this);
        GlowBackground bg = new GlowBackground(this);
        bg.setPalette(pal);
        bg.setGlow(p.glow ? Prefs.lastGlow(this) : 0, false);
        bg.setIdleDim(true);
        root.setBackground(bg);

        WaveView wave = new WaveView(this);
        wave.setPalette(pal);
        root.addView(wave, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clock = new TextView(this);
        clock.setTextColor(pal.text);
        clock.setAlpha(0.8f);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(this, 54));
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        clp.rightMargin = Px.of(this, 56);
        clp.bottomMargin = Px.of(this, 44);
        root.addView(clock, clp);

        forecastSlot = new FrameLayout(this);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        flp.leftMargin = Px.of(this, 56);
        flp.bottomMargin = Px.of(this, 44);
        root.addView(forecastSlot, flp);
        showForecast();

        setContentView(root);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(1800).start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    private void showForecast() {
        if (forecastSlot == null) return;
        forecastSlot.removeAllViews();
        android.view.View card = WeatherPanel.idleCard(this, pal);
        if (card != null) forecastSlot.addView(card);
    }

    private void updateClock() {
        if (clock == null) return;
        boolean h12 = Prefs.clockMode(this) == Prefs.CLOCK_12H;
        clock.setText(new SimpleDateFormat(h12 ? "h:mm" : "HH:mm", Locale.getDefault()).format(new Date()));
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
