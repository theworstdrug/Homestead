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
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The Settings pages. Everything here is the launcher's own behaviour - nothing on these
 * pages touches the system, so none of it needs a privileged permission.
 */
public final class SettingsPanel {

    /** Implemented by HomeGrid so a panel can push another panel or apply changes. */
    public interface Host {
        void rebuildGrid();
        void showPanel(View v);
        void closePanel();
        /** The current profile changed: its look, hidden tiles and greeting. */
        void profileChanged();
        /** Paint a palette everywhere without saving it (a preview); photo: over the photo. */
        void previewPalette(Palette p, boolean photo);
        /** Back to the current profile's saved look. */
        void applyPalette();
    }

    private SettingsPanel() { }

    /**
     * The hub: a sheet over the home screen with the two pages of home-screen options, then
     * Simple mode and the system pages. Changes show on the grid behind it straight away.
     */
    public static View build(final Context c, final Host host) {
        final Panel p = new Panel(c, "Settings",
                "Homestead " + UpdateChecker.installedVersionName(c), Panel.SHEET);
        final Profiles.Profile me = Prefs.current(c);
        final boolean owner = Profiles.canSeeEveryone(me);

        // ---- this profile's own look
        p.addHeader("For " + me.name);
        final Panel.Row theme = p.addRow("Theme", themeLabel(c), null).opens();
        theme.setValueColor(0);
        theme.setHelp("Midnight, OLED, Frost or Ember, an accent colour, or the photo. The "
                + "home screen behind shows each one as you move.");
        theme.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(ThemePanel.build(c, host)); }
        });
        theme.cell.setOnFocusChangeListener(chain(theme.cell.getOnFocusChangeListener(),
                new Runnable() { public void run() { theme.setValue(themeLabel(c)); } }));
        profileSwitch(c, host, p, "Glow follows the app", PROFILE_GLOW,
                "The soft light behind the home screen takes the colour of the app you are on.");
        profileSwitch(c, host, p, "Rank numbers", PROFILE_RANKS,
                "Large numbers beside your apps, most-used first. Shown when apps are sorted by "
                + "last used.");
        profileSwitch(c, host, p, "Greeting", PROFILE_GREETING,
                "\"Good evening\" with your name and a line under it, at the top left.");
        final Panel.Row mine = p.addRow("Hidden apps", hiddenLabel(Prefs.current(c).hidden.size()), null).opens();
        mine.setValueColor(0);
        mine.setHelp("Apps you hid for yourself. Others still see them.");
        mine.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildHiddenList(c, host, false)); }
        });
        mine.cell.setOnFocusChangeListener(chain(mine.cell.getOnFocusChangeListener(),
                new Runnable() { public void run() { mine.setValue(hiddenLabel(Prefs.current(c).hidden.size())); } }));

        // ---- the TV, for the owner only
        if (owner) {
            p.addHeader("Everyone");
            final Panel.Row all = p.addRow("Hidden for everyone", hiddenLabel(Prefs.hiddenCount(c)), null).opens();
            all.setValueColor(0);
            all.setHelp("The defaults: hidden for every profile. Only " + me.name + " can change them.");
            all.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { host.showPanel(buildHiddenList(c, host, true)); }
            });
            all.cell.setOnFocusChangeListener(chain(all.cell.getOnFocusChangeListener(),
                    new Runnable() { public void run() { all.setValue(hiddenLabel(Prefs.hiddenCount(c))); } }));
            final Panel.Row wx = p.addRow("Weather", Prefs.weatherCity(c) == null ? "Off" : Prefs.weatherCity(c), null).opens();
            wx.setValueColor(0);
            wx.setHelp("A forecast chip at the top of the home screen and a card on the idle "
                    + "screen. Choose a city to switch it on.");
            wx.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { host.showPanel(CitySearchPanel.settings(c, host)); }
            });
            wx.cell.setOnFocusChangeListener(chain(wx.cell.getOnFocusChangeListener(), new Runnable() {
                public void run() { wx.setValue(Prefs.weatherCity(c) == null ? "Off" : Prefs.weatherCity(c)); }
            }));
            final Panel.Row profiles = p.addRow("Profiles", Prefs.profiles(c).size() + "", null).opens();
            profiles.setValueColor(0);
            profiles.setHelp("Add, rename, recolour or remove profiles.");
            profiles.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { host.showPanel(ProfilesPanel.build(c, host)); }
            });
        }

        // ---- the home screen, for everyone
        p.addHeader("Home screen");
        final Panel.Row home = p.addRow("Layout and clock", homeSummary(c), null).opens();
        home.setValueColor(0);
        home.setHelp("Sorting, the clock and date, Live TV first and the rows below your apps.");
        final Panel.Row look = p.addRow("Background photo", Backgrounds.photoFile(c) == null
                ? "none" : Backgrounds.dimLabel(c), null).opens();
        look.setValueColor(0);
        look.setHelp("A photo of your own behind the home screen. Choose it on the Theme page.");
        final Runnable summaries = new Runnable() {
            public void run() { home.setValue(homeSummary(c)); }
        };
        home.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildHome(c, host, summaries)); }
        });
        look.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(buildLook(c, host, summaries)); }
        });

        if (!owner) {
            p.addRow("Close", "", new Runnable() {
                public void run() { host.closePanel(); }
            });
            return p.root;
        }

        p.addHeader("System");
        final Panel.Row simple = p.addRow("Simple mode", onOff(Prefs.simpleMode(c)), null).asSwitch();
        simple.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.simpleMode(c);
                Prefs.setSimpleMode(c, n);
                simple.setValue(onOff(n));
                host.rebuildGrid();
            }
        });
        p.addNote("For a TV set up for someone else: the Settings and TV Health tiles are "
                + "hidden (press MENU five times on the home screen to open Settings), MENU and "
                + "long-press no longer hide tiles, and there are no update pop-ups; check for "
                + "updates below. It does not change Debloat.");
        final Panel.Row picker = p.addRow("Who's watching at power-on", onOff(Prefs.replaceAmazonPicker(c)), null).asSwitch();
        picker.setHelp("When Fire TV shows its own \"Who's Watching Fire TV?\" after the TV is "
                + "switched on, show Homestead's instead. Amazon apps stay on the Amazon profile "
                + "used last; switch it in Fire TV's settings when needed.");
        picker.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.replaceAmazonPicker(c);
                Prefs.setReplaceAmazonPicker(c, n);
                picker.setValue(onOff(n));
            }
        });

        boolean ready = SetupPanel.serviceEnabled(c);
        final Panel.Row setup = p.addRow("Setup", ready ? "done"
                : Prefs.homeOffByUser(c) ? "Home button off" : "not finished", null).opens();
        setup.setValueColor(ready ? 0xFFA7D28F : 0xFFE8C468);
        setup.setHelp("What this launcher needs, and how to switch it on.");
        setup.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { host.showPanel(SetupPanel.build(c, host)); }
        });

        SetupPanel.addTurnOffRow(c, p, new Runnable() {
            public void run() {
                setup.setValue("Home button off");
                setup.setValueColor(0xFFE8C468);
            }
        });
        p.addNote(SetupPanel.TURN_OFF_HELP);

        p.addRow("TV Health", "check", new Runnable() {
            public void run() { host.showPanel(HealthPanel.build(c, host)); }
        }).opens().setHelp("This TV's speed and privacy settings and Amazon's advertising "
                + "packages, with a fix for each one it flags.");

        p.addRow("Debloat", Prefs.disabledByUs(c).size() + " off", new Runnable() {
            public void run() { host.showPanel(DebloatPanel.build(c, host)); }
        }).opens().setHelp("Switch Amazon's packages off, and back on again. Nothing is "
                + "ever uninstalled.");

        final Panel.Row updates = p.addRow("Check for updates",
                "v" + UpdateChecker.installedVersionName(c), null);
        updates.setValueColor(0);
        updates.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { UpdatePanel.checkNow(c, host, updates); }
        });

        p.addRow("Close", "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static final int PROFILE_GLOW = 0, PROFILE_RANKS = 1, PROFILE_GREETING = 2;

    /** An on/off row for one of the current profile's own settings. */
    private static void profileSwitch(final Context c, final Host host, Panel p, String label,
                                      final int which, String help) {
        final Panel.Row r = p.addRow(label, onOff(profileFlag(Prefs.current(c), which)), null).asSwitch();
        r.setHelp(help);
        r.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Profiles.Profile me = Prefs.current(c).copy();
                boolean n = !profileFlag(me, which);
                if (which == PROFILE_GLOW) me.glow = n;
                else if (which == PROFILE_RANKS) me.ranks = n;
                else me.greeting = n;
                Prefs.updateCurrent(c, me);
                r.setValue(onOff(n));
                host.rebuildGrid();
            }
        });
    }

    private static boolean profileFlag(Profiles.Profile p, int which) {
        return which == PROFILE_GLOW ? p.glow : which == PROFILE_RANKS ? p.ranks : p.greeting;
    }

    static String themeLabel(Context c) {
        Profiles.Profile me = Prefs.current(c);
        Palette pal = Palette.resolve(me.theme, me.accent);
        String s = me.usePhoto ? pal.name + " · Photo" : pal.name;
        int a = Palette.indexOf(Palette.ACCENT_IDS, me.accent);
        return a > 0 ? s + " · " + Palette.ACCENT_NAMES[a] : s;
    }

    private static String hiddenLabel(int n) { return n == 0 ? "None" : String.valueOf(n); }

    // ---------------------------------------------------------------- home screen

    private static View buildHome(final Context c, final Host host, final Runnable changed) {
        final Panel p = new Panel(c, "Layout and clock", null, Panel.SHEET);

        final Panel.Row sort = p.addRow("Sort apps by", sortLabel(c), null);
        sort.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setSort(c, Prefs.sort(c) == Prefs.SORT_LAST_USED
                        ? Prefs.SORT_ALPHA : Prefs.SORT_LAST_USED);
                sort.setValue(sortLabel(c));
                AppModel.resort(c);
                host.rebuildGrid();
            }
        });

        final Panel.Row clock = p.addRow("Clock", clockLabel(c), null);
        clock.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = Prefs.clockMode(c) + 1;
                if (n > Prefs.CLOCK_OFF) n = Prefs.CLOCK_24H;
                Prefs.setClockMode(c, n);
                clock.setValue(clockLabel(c));
                host.rebuildGrid();
            }
        });

        final Panel.Row date = p.addRow("Date", dateLabel(c), null);
        date.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = Prefs.dateFormat(c) + 1;
                if (n > Prefs.DATE_SHORT) n = Prefs.DATE_OFF;
                Prefs.setDateFormat(c, n);
                date.setValue(dateLabel(c));
                host.rebuildGrid();
            }
        });
        date.setHelp("Beside the clock. Both formats follow the TV's language.");

        final Panel.Row fire = p.addRow("Fire TV system icons", onOff(Prefs.fireOsIcons(c)), null).asSwitch();
        fire.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.fireOsIcons(c);
                Prefs.setFireOsIcons(c, n);
                fire.setValue(onOff(n));
                AppModel.reload(c);
                host.rebuildGrid();
            }
        });
        fire.setHelp("Amazon Home, Settings and TV Health use the icons of Fire OS's own home and "
                + "Settings, read from this TV. Where one is missing, the usual icon stays.");

        p.addHeader("Sections");

        // Only offered on a TV that has the Live TV tile.
        if (AppModel.hasLiveTv()) {
            final Panel.Row tvFirst = p.addRow("Live TV first", onOff(Prefs.liveTvFirst(c)), null).asSwitch();
            tvFirst.cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean n = !Prefs.liveTvFirst(c);
                    Prefs.setLiveTvFirst(c, n);
                    tvFirst.setValue(onOff(n));
                    host.rebuildGrid();
                }
            });
            tvFirst.setHelp("A large Live TV tile on top, then TV apps, then inputs; Home always "
                    + "lands on Live TV.");
        }

        final Panel.Row secIn = p.addRow("Show Inputs", onOff(Prefs.showInputs(c)), null).asSwitch();
        secIn.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.showInputs(c);
                Prefs.setShowInputs(c, n);
                secIn.setValue(onOff(n));
                host.rebuildGrid();
            }
        });

        final Panel.Row secNon = p.addRow("Show other apps in More", onOff(Prefs.showNonTv(c)), null).asSwitch();
        secNon.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Prefs.showNonTv(c);
                Prefs.setShowNonTv(c, n);
                secNon.setValue(onOff(n));
                host.rebuildGrid();
            }
        });

        p.addSectionHelp("Settings and TV Health always stay reachable, even with "
                + "\"Inputs & System\" switched off. Simple mode hides them; press MENU five "
                + "times on the home screen to come back here.");

        p.addHeader("Apps");
        p.addRow("Reload apps now", "", new Runnable() {
            public void run() {
                AppModel.reload(c);
                host.rebuildGrid();
            }
        }).setHelp("Reads the installed apps and the TV's inputs again.");

        p.addRow("Back", "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static String homeSummary(Context c) {
        return sortLabel(c) + " · " + clockLabel(c);
    }

    /** Runs extra work after a row's own focus handling, when it gains focus. */
    private static View.OnFocusChangeListener chain(final View.OnFocusChangeListener first,
                                                    final Runnable onGain) {
        return new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (first != null) first.onFocusChange(v, has);
                if (has) onGain.run();
            }
        };
    }

    // ---------------------------------------------------------------- background

    private static View buildLook(final Context c, final Host host, final Runnable changed) {
        final Panel p = new Panel(c, "Background photo", Backgrounds.photoFile(c) == null
                ? "No photo on this TV yet" : "Use it from Settings → Theme", Panel.SHEET);
        p.addText("Choose a picture saved on the TV below (needs the on-device tunnel), or send "
                + "a JPEG or PNG from a computer:\n\n"
                + "adb push background.jpg /sdcard/Download/\n"
                + "adb shell \"content write --uri content://" + BackgroundProvider.AUTHORITY
                + "/photo < /sdcard/Download/background.jpg\"");
        final Panel.Row dim = p.addRow("Photo dimming", Backgrounds.dimLabel(c), null);
        dim.cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setPhotoDim(c, (Prefs.photoDim(c) + 1) % Backgrounds.DIM_NAMES.length);
                dim.setValue(Backgrounds.dimLabel(c));
                host.rebuildGrid();
            }
        });
        dim.setHelp("Dimming darkens the photo so the labels stay readable.");

        p.addRow("Choose a photo on this TV", "", new Runnable() {
            public void run() { host.showPanel(PhotoPanel.build(c, host)); }
        }).opens().setHelp("Lists the pictures in the TV's Download folder. Needs the "
                + "on-device tunnel.");

        p.addRow("Back", "", new Runnable() {
            public void run() { host.closePanel(); }
        });

        return p.root;
    }


    // ---------------------------------------------------------------- hidden list

    /** everyone: the tiles hidden for every profile; else the current profile's own. */
    private static View buildHiddenList(final Context c, final Host host, final boolean everyone) {
        final Panel p = new Panel(c, everyone ? "Hidden for everyone" : "Hidden for " + Prefs.current(c).name,
                "Select one to show it on the home screen again", Panel.SHEET);

        Set<String> keys = everyone ? Prefs.hidden(c) : new java.util.HashSet<String>(Prefs.current(c).hidden);
        if (keys.isEmpty()) {
            p.addNote("Nothing is hidden. Hold Select on an app (or press MENU) to hide it.");
        } else {
            // Resolve keys to human labels through the full model, not the visible list.
            List<AppModel.Item> all = AppModel.allItems();
            List<String> rows = new ArrayList<String>();
            final List<String> rowKeys = new ArrayList<String>();
            for (String k : keys) {
                String label = k;
                for (int i = 0; i < all.size(); i++) {
                    if (k.equals(all.get(i).key())) { label = all.get(i).label; break; }
                }
                // Live TV leaves the model when its app is switched off; still name it.
                if (label.equals(AppModel.LIVETV_KEY)) label = c.getString(R.string.home_livetv);
                rows.add(label);
                rowKeys.add(k);
            }
            // Stable, readable order.
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    if (rows.get(j).compareToIgnoreCase(rows.get(i)) < 0) {
                        Collections.swap(rows, i, j);
                        Collections.swap(rowKeys, i, j);
                    }
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                final String key = rowKeys.get(i);
                final Panel.Row r = p.addRow(rows.get(i), "hidden", null);
                r.cell.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        unhide(c, key, everyone);
                        r.setValue("shown");
                        r.cell.setEnabled(false);
                        host.rebuildGrid();
                    }
                });
            }
            p.addRow("Show all hidden tiles", "", new Runnable() {
                public void run() {
                    if (everyone) {
                        Prefs.clearHidden(c);
                    } else {
                        Profiles.Profile me = Prefs.current(c).copy();
                        me.hidden.clear();
                        Prefs.updateCurrent(c, me);
                    }
                    host.rebuildGrid();
                    host.closePanel();
                }
            });
        }
        p.addRow("Back", "", new Runnable() {
            public void run() { host.closePanel(); }
        });
        return p.root;
    }

    private static void unhide(Context c, String key, boolean everyone) {
        if (everyone) {
            Prefs.setHidden(c, key, false);
        } else {
            Profiles.Profile me = Prefs.current(c).copy();
            me.hidden.remove(key);
            Prefs.updateCurrent(c, me);
        }
    }

    // ---------------------------------------------------------------- labels

    private static String onOff(boolean b) { return b ? "On" : "Off"; }

    private static String sortLabel(Context c) {
        return Prefs.sort(c) == Prefs.SORT_ALPHA ? "A – Z" : "Last used";
    }

    private static String clockLabel(Context c) {
        switch (Prefs.clockMode(c)) {
            case Prefs.CLOCK_12H: return "12-hour";
            case Prefs.CLOCK_OFF: return "Off";
            default: return "24-hour";
        }
    }

    private static String dateLabel(Context c) {
        switch (Prefs.dateFormat(c)) {
            case Prefs.DATE_LONG: return "Weekday and month";
            case Prefs.DATE_SHORT: return "Numbers";
            default: return "Off";
        }
    }
}
