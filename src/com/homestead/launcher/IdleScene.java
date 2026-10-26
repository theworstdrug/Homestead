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
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * What the idle screen shows: the profile's theme with the last app's glow, the wave of
 * light, the clock and the forecast card. The clock ticks, the forecast refreshes and the
 * two drift a little while it is attached. Used by the screensaver and by its preview.
 */
class IdleScene extends FrameLayout {
    /** Every few minutes the clock and the card glide a little, so nothing stays on one spot. */
    private static final long DRIFT_MS = 3L * 60L * 1000L;
    private static final float DRIFT_X = 28f, DRIFT_Y = 18f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Palette pal;
    private final TextView clock;
    private final FrameLayout forecastSlot;

    private final Runnable tick = new Runnable() {
        public void run() {
            updateClock();
            // The forecast keeps up while the idle screen shows (at most every 30 minutes).
            WeatherStore.refreshIfStale(getContext(), new Runnable() {
                public void run() { showForecast(); }
            });
            handler.postDelayed(this, 60000L - System.currentTimeMillis() % 60000L + 50L);
        }
    };

    private final Runnable drift = new Runnable() {
        public void run() {
            for (View v : new View[]{clock, forecastSlot}) {
                v.animate().translationX(Px.f(getContext(), (random.nextFloat() * 2 - 1) * DRIFT_X))
                        .translationY(Px.f(getContext(), (random.nextFloat() * 2 - 1) * DRIFT_Y))
                        .setDuration(4000).start();
            }
            handler.postDelayed(this, DRIFT_MS);
        }
    };

    IdleScene(Context c) {
        super(c);
        Profiles.Profile p = Prefs.current(c);
        pal = Palette.resolve(p.theme, p.accent);
        GlowBackground bg = new GlowBackground(c);
        bg.setPalette(pal);
        bg.setGlow(p.glow ? Prefs.lastGlow(c) : 0, false);
        bg.setIdleDim(true);
        setBackground(bg);

        WaveView wave = new WaveView(c);
        wave.setPalette(pal);
        addView(wave, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clock = new TextView(c);
        clock.setTextColor(pal.text);
        clock.setAlpha(0.8f);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 54));
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LayoutParams clp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        clp.rightMargin = Px.of(c, 56);
        clp.bottomMargin = Px.of(c, 44);
        addView(clock, clp);

        forecastSlot = new FrameLayout(c);
        LayoutParams flp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        flp.leftMargin = Px.of(c, 56);
        flp.bottomMargin = Px.of(c, 44);
        addView(forecastSlot, flp);
        showForecast();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setAlpha(0f);
        animate().alpha(1f).setDuration(1800).start();
        handler.post(tick);
        handler.postDelayed(drift, DRIFT_MS);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(drift);
        super.onDetachedFromWindow();
    }

    private void showForecast() {
        forecastSlot.removeAllViews();
        View card = WeatherPanel.idleCard(getContext(), pal);
        if (card != null) forecastSlot.addView(card);
    }

    private void updateClock() {
        clock.setVisibility(Prefs.clockMode(getContext()) == Prefs.CLOCK_OFF ? GONE : VISIBLE);
        boolean h12 = Prefs.clockMode(getContext()) == Prefs.CLOCK_12H;
        clock.setText(new SimpleDateFormat(h12 ? "h:mm" : "HH:mm", Locale.getDefault()).format(new Date()));
    }

    /**
     * The idle screen as a page over the home screen (Settings → Preview the idle screen):
     * any key closes it, as any key ends the real one.
     */
    static View preview(Context c, final SettingsPanel.Host host) {
        final IdleScene s = new IdleScene(c) {
            @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
                if (e.getAction() == android.view.KeyEvent.ACTION_UP) host.closePanel();
                return true;
            }
        };
        s.setFocusable(true);
        s.setFocusableInTouchMode(true);
        s.setTag(new HomeGrid.BackHandler() {
            public boolean onBack() { return false; }
        });
        return s;
    }
}
