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
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Settings → Everyone → Weather: the city (searched by name with Open-Meteo's geocoding),
 * the temperature unit, and turning the weather off. Weather is off until a city is chosen.
 */
final class CitySearchPanel {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CitySearchPanel() { }

    /** The weather settings sheet. */
    static View settings(final Context c, final SettingsPanel.Host host) {
        String city = Prefs.weatherCity(c);
        final Panel p = new Panel(c, "Weather", city == null ? "Off until a city is chosen" : city, Panel.SHEET);
        p.addRow(city == null ? "Choose a city" : "Change city", city == null ? "" : city, new Runnable() {
            public void run() {
                host.closePanel();
                host.showPanel(search(c, host));
            }
        }).opens().setValueColor(0);
        final Panel.Row units = p.addRow("Temperature", Prefs.fahrenheit(c) ? "°F" : "°C", null);
        units.setValueColor(0);
        units.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean f = !Prefs.fahrenheit(c);
                Prefs.setFahrenheit(c, f);
                units.setValue(f ? "°F" : "°C");
                host.rebuildGrid();
            }
        });
        if (city != null) {
            p.addRow("Turn weather off", "", new Runnable() {
                public void run() {
                    Prefs.clearWeather(c);
                    host.rebuildGrid();
                    host.closePanel();
                }
            });
        }
        p.addNote("Only the city's coordinates are sent, to Open-Meteo (open-meteo.com), at most "
                + "every 30 minutes while the TV is on. No account, no key, nothing else.");
        p.addRow("Back", "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    /** Type a city's name; Search lists the matches. */
    static View search(final Context c, final SettingsPanel.Host host) {
        final Palette pal = Prefs.palette(c);
        final FrameLayout root = ProfilePicker.overlay(c, pal);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(ProfilePicker.title(c, pal, "Which city?"), wrap());
        final TextView box = new TextView(c);
        box.setGravity(Gravity.CENTER);
        box.setTextColor(pal.text);
        box.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(c, 34));
        box.setText("|");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Px.f(c, 18));
        bg.setColor(pal.chip);
        bg.setStroke(Px.of(c, 2), pal.sub);
        box.setBackground(bg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Px.of(c, 520), Px.of(c, 72));
        blp.topMargin = Px.of(c, 34);
        blp.bottomMargin = Px.of(c, 30);
        col.addView(box, blp);
        final boolean[] searching = {false};
        final OnScreenKeyboard kb = new OnScreenKeyboard(c, 24, "Search", new OnScreenKeyboard.Listener() {
            public void onChange(String text) { box.setText(text + "|"); }
            public void onDone(final String text) {
                if (searching[0]) return;
                searching[0] = true;
                box.setText("Searching…");
                new Thread(new Runnable() {
                    public void run() {
                        List<Weather.Place> found = null;
                        try {
                            found = Weather.parsePlaces(WeatherStore.fetch(Weather.geocodeUrl(text)));
                        } catch (Throwable t) {
                            android.util.Log.w("Homestead", "city search failed: " + t);
                        }
                        final List<Weather.Place> result = found;
                        MAIN.post(new Runnable() {
                            public void run() {
                                searching[0] = false;
                                // Back or Home since: this page is gone, and the reply with it.
                                if (!root.isAttachedToWindow() || !root.isShown()) return;
                                if (result == null) {
                                    box.setText("No connection. Try again.");
                                    return;
                                }
                                if (result.isEmpty()) {
                                    box.setText("No city called \"" + text + "\"");
                                    return;
                                }
                                host.closePanel();
                                host.showPanel(results(c, host, result));
                            }
                        });
                    }
                }, "homestead-geocode").start();
            }
        });
        col.addView(kb, wrap());
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ProfilePicker.popIn(col);
        kb.post(new Runnable() { public void run() { kb.requestFocus(); } });
        return root;
    }

    private static View results(final Context c, final SettingsPanel.Host host, List<Weather.Place> places) {
        final Panel p = new Panel(c, "Pick your city", null, Panel.SHEET);
        for (final Weather.Place pl : places) {
            p.addRow(pl.label(), "", new Runnable() {
                public void run() {
                    Prefs.setWeatherPlace(c, pl.name, pl.lat, pl.lon);
                    host.closePanel();
                    host.rebuildGrid();
                }
            });
        }
        p.addRow("Search again", "", new Runnable() {
            public void run() {
                host.closePanel();
                host.showPanel(search(c, host));
            }
        });
        return p.root;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
