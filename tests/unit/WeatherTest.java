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

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

final class WeatherTest {
    private WeatherTest() { }

    static void run() throws Exception {
        parsesFixture();
        kindMapping();
        urlHasUnits();
        parseRejectsMalformed();
        parsesPlaces();
    }

    private static String fixture(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("tests/unit/fixtures/" + name)), Charset.forName("UTF-8"));
    }

    static void parsesFixture() throws Exception {
        Weather.Forecast f = Weather.parse(fixture("forecast.json"));
        T.eq(5, f.days.length, "five days");
        T.eq(6, f.hours.length, "six hours");
        T.eq("Today", f.days[0].label, "first day is today");
        T.eq("Now", f.hours[0].label, "first hour is now");
        T.yes(f.days[0].lo <= f.days[0].hi, "low <= high");
        T.eq(47, f.now.temp, "current temperature, rounded");
        T.eq("cloud", f.now.kind, "code 3 is cloud");
        T.yes(f.now.text.length() > 0, "a description");
    }

    static void kindMapping() {
        T.eq("sun", Weather.kind(0), "0");
        T.eq("sun", Weather.kind(1), "1");
        T.eq("partly", Weather.kind(2), "2");
        T.eq("cloud", Weather.kind(3), "3");
        T.eq("fog", Weather.kind(45), "45");
        T.eq("rain", Weather.kind(61), "61");
        T.eq("rain", Weather.kind(81), "81");
        T.eq("snow", Weather.kind(73), "73");
        T.eq("storm", Weather.kind(95), "95");
    }

    static void urlHasUnits() {
        String f = Weather.forecastUrl(47.6, -122.3, true);
        T.yes(f.startsWith("https://api.open-meteo.com/v1/forecast?"), "host");
        T.yes(f.contains("temperature_unit=fahrenheit"), "fahrenheit");
        T.yes(!Weather.forecastUrl(47.6, -122.3, false).contains("temperature_unit"), "celsius is the default");
        T.yes(f.contains("latitude=47.6") && f.contains("longitude=-122.3"), "coordinates");
        T.eq("https://geocoding-api.open-meteo.com/v1/search?count=8&name=San+Jos%C3%A9", Weather.geocodeUrl("San José"), "geocode url");
    }

    static void parseRejectsMalformed() throws Exception {
        final String noDaily = fixture("forecast.json").replace("\"daily\"", "\"dailyx\"");
        T.fails(IllegalArgumentException.class, new Runnable() {
            public void run() { Weather.parse(noDaily); }
        }, "missing daily");
        T.fails(IllegalArgumentException.class, new Runnable() {
            public void run() { Weather.parse("<html>busy</html>"); }
        }, "not JSON");
    }

    static void parsesPlaces() throws Exception {
        List<Weather.Place> ps = Weather.parsePlaces(fixture("geocode.json"));
        T.yes(!ps.isEmpty(), "places");
        T.eq("Seattle", ps.get(0).name, "name");
        T.yes(ps.get(0).label().contains("Washington"), "label has the state: " + ps.get(0).label());
        T.eq(0, Weather.parsePlaces("{\"generationtime_ms\":0.1}").size(), "no results");
    }
}
