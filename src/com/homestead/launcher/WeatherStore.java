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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The forecast, cached in the settings so nothing ever waits on the network. A refresh
 * runs at most every 30 minutes, on its own thread, with a 10-second limit; any failure
 * keeps what is cached (or nothing) and is logged once. Off until a city is chosen.
 */
final class WeatherStore {
    private static final String TAG = "Homestead";
    static final long STALE_MS = 30L * 60L * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean fetching;
    /** After a failure, wait this long before trying again rather than on every Home press. */
    static final long RETRY_MS = 5L * 60L * 1000L;
    private static volatile long failedAt;
    private static String parsedFrom;
    private static Weather.Forecast parsed;

    private WeatherStore() { }

    /** The cached forecast, or null (no city, nothing fetched yet, or an unreadable reply). */
    static synchronized Weather.Forecast get(Context c) {
        if (Prefs.weatherCity(c) == null) return null;
        if (!Weather.isFresh(Prefs.weatherCacheAt(c), System.currentTimeMillis())) return null;
        String json = Prefs.weatherCache(c);
        if (json == null) return null;
        if (!json.equals(parsedFrom)) {
            parsedFrom = json;
            try { parsed = Weather.parse(json); }
            catch (RuntimeException e) { parsed = null; Log.w(TAG, "weather cache unreadable: " + e); }
        }
        return parsed;
    }

    /** Fetch if a city is set and the cache is older than 30 minutes; onNew runs on the main thread. */
    static void refreshIfStale(Context ctx, final Runnable onNew) {
        final Context c = ctx.getApplicationContext();
        if (Prefs.weatherCity(c) == null || fetching) return;
        long now = System.currentTimeMillis();
        if (now - Prefs.weatherCacheAt(c) < STALE_MS || now - failedAt < RETRY_MS) return;
        fetching = true;
        final String url = Weather.forecastUrl(Prefs.weatherLat(c), Prefs.weatherLon(c), Prefs.fahrenheit(c));
        new Thread(new Runnable() {
            public void run() {
                try {
                    String json = fetch(url);
                    Weather.parse(json); // a reply we cannot read is not cached
                    // The city or unit changed while this was in flight: it is not wanted.
                    if (Prefs.weatherCity(c) == null || !url.equals(Weather.forecastUrl(
                            Prefs.weatherLat(c), Prefs.weatherLon(c), Prefs.fahrenheit(c)))) return;
                    Prefs.setWeatherCache(c, json, System.currentTimeMillis());
                    failedAt = 0L;
                    if (onNew != null) MAIN.post(onNew);
                } catch (Throwable t) {
                    failedAt = System.currentTimeMillis();
                    Log.w(TAG, "weather not updated: " + t);
                } finally {
                    fetching = false;
                }
            }
        }, "homestead-weather").start();
    }

    /** A GET with 10-second limits; the body as UTF-8. Off the main thread only. */
    static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Homestead");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                if (out.size() > 512 * 1024) throw new java.io.IOException("reply too large");
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
