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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The forecast from Open-Meteo (free, no account or key): the request addresses and the
 * reading of the replies. Only coordinates and the unit are sent. No Android classes.
 */
final class Weather {
    static final class Now { int temp; String kind, text; }
    static final class Hour { String label; int temp; String kind; }
    static final class Day { String label; int lo, hi; String kind; }

    static final class Forecast {
        Now now;
        Hour[] hours;
        Day[] days;
    }

    static final class Place {
        String name, admin1, country;
        double lat, lon;

        /** "Seattle, Washington, United States", without empty parts. */
        String label() {
            StringBuilder b = new StringBuilder(name);
            if (admin1 != null && !admin1.isEmpty() && !admin1.equals(name)) b.append(", ").append(admin1);
            if (country != null && !country.isEmpty()) b.append(", ").append(country);
            return b.toString();
        }
    }

    private Weather() { }

    static String forecastUrl(double lat, double lon, boolean fahrenheit) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code"
                + "&hourly=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=5"
                + (fahrenheit ? "&temperature_unit=fahrenheit" : "");
    }

    static String geocodeUrl(String name) {
        try {
            return "https://geocoding-api.open-meteo.com/v1/search?count=8&name=" + URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** WMO weather code to the icon: sun, partly, cloud, fog, rain, snow or storm. */
    static String kind(int code) {
        if (code <= 1) return "sun";
        if (code == 2) return "partly";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if (code >= 95) return "storm";
        return "cloud";
    }

    static String describe(String kind) {
        if ("sun".equals(kind)) return "Clear";
        if ("partly".equals(kind)) return "Partly cloudy";
        if ("fog".equals(kind)) return "Fog";
        if ("rain".equals(kind)) return "Rain";
        if ("snow".equals(kind)) return "Snow";
        if ("storm".equals(kind)) return "Thunderstorms";
        return "Cloudy";
    }

    @SuppressWarnings("unchecked")
    static Forecast parse(String json) {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) throw new IllegalArgumentException("not an object");
        Map<String, Object> m = (Map<String, Object>) root;
        Map<String, Object> cur = obj(m, "current");
        Map<String, Object> hourly = obj(m, "hourly");
        Map<String, Object> daily = obj(m, "daily");

        Forecast f = new Forecast();
        f.now = new Now();
        f.now.temp = round(cur.get("temperature_2m"));
        f.now.kind = kind(round(cur.get("weather_code")));
        f.now.text = describe(f.now.kind);

        List<Object> ht = list(hourly, "time"), htemp = list(hourly, "temperature_2m"), hcode = list(hourly, "weather_code");
        String nowTime = String.valueOf(cur.get("time"));
        String nowHour = nowTime.length() >= 13 ? nowTime.substring(0, 13) : nowTime;
        int start = 0;
        for (int i = 0; i < ht.size(); i++) {
            if (String.valueOf(ht.get(i)).startsWith(nowHour)) { start = i; break; }
        }
        int n = Math.max(0, Math.min(6, ht.size() - start));
        f.hours = new Hour[n];
        for (int k = 0; k < n; k++) {
            Hour h = new Hour();
            h.label = k == 0 ? "Now" : hourLabel(String.valueOf(ht.get(start + k)));
            h.temp = round(htemp.get(start + k));
            h.kind = kind(round(hcode.get(start + k)));
            f.hours[k] = h;
        }

        List<Object> dt = list(daily, "time"), dmax = list(daily, "temperature_2m_max"),
                dmin = list(daily, "temperature_2m_min"), dcode = list(daily, "weather_code");
        f.days = new Day[dt.size()];
        for (int k = 0; k < dt.size(); k++) {
            Day d = new Day();
            d.label = k == 0 ? "Today" : dayLabel(String.valueOf(dt.get(k)));
            d.hi = round(dmax.get(k));
            d.lo = round(dmin.get(k));
            d.kind = kind(round(dcode.get(k)));
            f.days[k] = d;
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    static List<Place> parsePlaces(String json) {
        Object root = Json.parse(json);
        List<Place> out = new ArrayList<Place>();
        if (!(root instanceof Map)) return out;
        Object r = ((Map<String, Object>) root).get("results");
        if (!(r instanceof List)) return out;
        for (Object o : (List<Object>) r) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> e = (Map<String, Object>) o;
            if (!(e.get("latitude") instanceof Double) || !(e.get("longitude") instanceof Double)) continue;
            Place p = new Place();
            p.name = String.valueOf(e.get("name"));
            p.admin1 = e.get("admin1") instanceof String ? (String) e.get("admin1") : null;
            p.country = e.get("country") instanceof String ? (String) e.get("country") : null;
            p.lat = (Double) e.get("latitude");
            p.lon = (Double) e.get("longitude");
            out.add(p);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Map)) throw new IllegalArgumentException("missing " + key);
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof List)) throw new IllegalArgumentException("missing " + key);
        return (List<Object>) v;
    }

    private static int round(Object v) {
        if (!(v instanceof Double)) throw new IllegalArgumentException("not a number: " + v);
        return (int) Math.round((Double) v);
    }

    /** "2026-10-25T15:00" → "3 PM" (in the TV's language). */
    private static String hourLabel(String iso) {
        Calendar c = calendar(iso);
        return new SimpleDateFormat("h a", Locale.getDefault()).format(c.getTime());
    }

    /** "2026-10-26" → "Mon". */
    private static String dayLabel(String iso) {
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(calendar(iso).getTime());
    }

    private static Calendar calendar(String iso) {
        try {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1,
                    Integer.parseInt(iso.substring(8, 10)));
            if (iso.length() >= 13) c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(iso.substring(11, 13)));
            return c;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("bad time " + iso);
        }
    }
}
