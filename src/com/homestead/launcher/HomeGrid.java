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

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The launcher UI, built against a plain Context so the same screen can be hosted by an
 * Activity or a WindowManager overlay.
 *
 * From the top: the top bar (greeting, weather, date, clock, avatar), "Your apps" (one row,
 * the selected app large, the background glowing in its colour), Inputs and More. The
 * D-pad is handled here rather than by the focus search, so Up and Down move between rows
 * and return to where each row was left, and Left and Right stay in the row. Data and
 * bitmaps come from the process-wide AppModel.
 */
public class HomeGrid implements SettingsPanel.Host {

    public interface Listener {
        /** Called before the item is acted on, so an overlay host can dismiss itself. */
        void onPick(AppModel.Item it);
    }

    /**
     * Set as a panel root's tag to handle Back itself: return true to consume it, false to
     * let the panel close.
     */
    public interface BackHandler { boolean onBack(); }

    static final int ROW_TOP = -1, ROW_APPS = 0, ROW_INPUTS = 1, ROW_MORE = 2;

    private final Context ctx;
    private final Listener listener;

    public final FrameLayout root;
    private final ScrollView scroll;
    private final ObjectAnimator scrollAnim;
    private final LinearLayout column;
    private final TopBar topBar;
    private final AppRow appRow;
    private final PillRow inputRow, moreRow;
    private final TextView appsLabel, inputsLabel, moreLabel, infoName, infoMeta;
    private final View info;
    private final GlowBackground glowBg;
    private final View topFade;

    private List<AppModel.Item> apps = new ArrayList<AppModel.Item>();
    private List<AppModel.Item> inputs = new ArrayList<AppModel.Item>();
    private List<AppModel.Item> more = new ArrayList<AppModel.Item>();
    /** Index of the first app that gets a rank number (1 when Live TV leads the row). */
    private int firstRanked;
    private boolean ranksShown;

    private final Map<String, View> cellsByKey = new HashMap<String, View>();
    private String focusedKey;
    private int focusRow = ROW_APPS;
    /** Focus is being restored (handoff, rebuild): jump to the end state, no animation. */
    private boolean instant;
    private Palette palette = Palette.resolve(null, null);
    /** Set while Settings previews a theme; the saved palette comes back with applyPalette(). */
    private boolean previewing;
    private int flavor = -1;
    private String flavorFor;
    private static final Random RANDOM = new Random();

    /** AppModel.order() as of the last build, to spot a re-sort done by the other grid. */
    private String builtOrder;
    /**
     * Bumped whenever either grid changes a layout setting or hides a tile; each grid builds
     * against the value it saw, so the other one can tell it is out of date.
     */
    private static int layoutGen;
    private int builtGen = -1;
    /** Settings / TV Health render inside this window; Back pops one level. */
    private final List<View> panels = new ArrayList<View>();
    /** For each open panel, the view that had focus when the next one opened over it. */
    private final List<View> panelFocus = new ArrayList<View>();
    /**
     * What the panels opened here call back into. A panel keeps the host it was built with,
     * so when the overlay hands its panels to the activity, this is pointed at the
     * activity's grid and the overlay starts a new one.
     */
    private PanelHost panelHost = new PanelHost(this);
    /** The last key released here, logged when a panel opens from a tile. */
    private int lastKey;
    private int lastKeyDevice;
    private int lastKeySource;
    private long lastKeyAt;
    /** Shared by the overlay and activity grids, so presses across the handoff still count. */
    private static final MenuTaps menuTaps = new MenuTaps();

    /** Simple mode's way into Settings: Menu pressed five times, each within 2 s of the last. */
    static final class MenuTaps {
        static final int COUNT = 5;
        private int n;
        private long last;

        /** @return true on the press that completes the sequence. */
        boolean tap() {
            long now = android.os.SystemClock.uptimeMillis();
            n = (now - last <= 2000L) ? n + 1 : 1;
            last = now;
            if (n < COUNT) return false;
            n = 0;
            return true;
        }
    }

    /** A ScrollView that leaves scrolling to us: the rows decide where the page sits. */
    private static final class PageScroll extends ScrollView {
        PageScroll(Context c) { super(c); }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
    }

    public HomeGrid(Context c, Listener l) {
        this.ctx = c;
        this.listener = l;

        // Overridden so Back, Menu and the D-pad reach us before the focused child consumes
        // them; a key listener on the root would never fire.
        root = new FrameLayout(c) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    lastKey = e.getKeyCode();
                    lastKeyDevice = e.getDeviceId();
                    lastKeySource = e.getSource();
                    lastKeyAt = android.os.SystemClock.uptimeMillis();
                }
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && !panels.isEmpty()) {
                    if (e.getAction() == KeyEvent.ACTION_UP) {
                        View top = panels.get(panels.size() - 1);
                        Object t = top.getTag();
                        if (t instanceof BackHandler && ((BackHandler) t).onBack()) return true;
                        closePanel();
                    }
                    return true;
                }
                // Menu hides the focused tile, matching the stock Fire TV launcher's
                // convention (long-press also works). In Simple mode it hides nothing, and
                // five presses in a row open Settings instead.
                if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && panels.isEmpty()) {
                    if (e.getAction() == KeyEvent.ACTION_UP) {
                        if (!Prefs.simpleMode(ctx)) openOptions();
                        else if (menuTaps.tap()) {
                            logOpen("Settings (Menu x" + MenuTaps.COUNT + ")");
                            showPanel(SettingsPanel.build(ctx, panelHost));
                        }
                    }
                    return true;
                }
                if (panels.isEmpty() && e.getAction() == KeyEvent.ACTION_DOWN && navigate(e.getKeyCode())) {
                    return true;
                }
                // Hold Select: Android marks the first key repeat after the long-press timeout
                // with FLAG_LONG_PRESS. Acting on that here works for any remote, and the
                // release that follows must not also open the app.
                if (isSelect(e.getKeyCode())) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN && panels.isEmpty()) {
                        if (e.getRepeatCount() == 0) {
                            swallowSelectUp = false;
                            startHoldBar();
                        } else if (e.isLongPress() && openOptions()) {
                            swallowSelectUp = true;
                            return true;
                        }
                        if (e.getRepeatCount() > 0) return true;
                    } else if (e.getAction() == KeyEvent.ACTION_UP) {
                        stopHoldBar();
                        if (swallowSelectUp) {
                            swallowSelectUp = false;
                            return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(e);
            }
        };
        root.setClipChildren(false);
        glowBg = new GlowBackground(c);
        root.setBackground(glowBg);

        scroll = new PageScroll(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", 0);
        scrollAnim.setInterpolator(Px.EASE);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(Px.of(c, 64), Px.of(c, 128), 0, Px.of(c, 420));
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        appsLabel = sectionLabel(R.string.home_sec_apps, 0);
        appRow = new AppRow(c, new AppRow.Listener() {
            public void onFocus(int i) { onCellFocused(ROW_APPS, i); }
            public void onClick(int i) { pick(apps, i); }
            public boolean onLongPress(int i) { return openOptions(); }
        });
        column.addView(appRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, AppRow.HEIGHT)));
        LinearLayout infoBox = new LinearLayout(c);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setPadding(0, Px.of(c, 18), 0, 0);
        infoName = text(26, "sans-serif-medium");
        infoMeta = text(17, "sans-serif");
        infoBox.addView(infoName);
        infoBox.addView(infoMeta);
        info = infoBox;
        column.addView(infoBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, 92)));
        inputsLabel = sectionLabel(R.string.home_sec_inputs_short, 0);
        inputRow = new PillRow(c, PillRow.PILLS, new AppRow.Listener() {
            public void onFocus(int i) { onCellFocused(ROW_INPUTS, i); }
            public void onClick(int i) { pick(inputs, i); }
            public boolean onLongPress(int i) { return false; }
        });
        column.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, 64)));
        moreLabel = sectionLabel(R.string.home_sec_more, 26);
        moreRow = new PillRow(c, PillRow.TILES, new AppRow.Listener() {
            public void onFocus(int i) { onCellFocused(ROW_MORE, i); }
            public void onClick(int i) { pick(more, i); }
            public boolean onLongPress(int i) { return openOptions(); }
        });
        column.addView(moreRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, 110)));

        // Fades in over the top of the page once the rows scroll up, so they pass under the
        // clock and avatar softly instead of colliding with them.
        topFade = new View(c);
        topFade.setAlpha(0f);
        root.addView(topFade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Px.of(c, 150)));

        topBar = new TopBar(c, new TopBar.Listener() {
            public void onTopFocus(View v) { onCellFocused(ROW_TOP, 0); }
            public void onAvatar() { showPanel(ProfilePicker.build(ctx, panelHost)); }
            public void onWeather() { /* The forecast panel arrives with the weather task. */ }
        });
        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AppModel.ensureLoaded(c);
        Backgrounds.ensureFolder(c);
        applyBackground();
        build();
        updateClock();
    }

    private TextView text(float size, String face) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Px.f(ctx, size));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setSingleLine(true);
        return t;
    }

    private TextView sectionLabel(int res, float topMargin) {
        TextView t = text(15, "sans-serif-medium");
        t.setText(ctx.getString(res).toUpperCase(Locale.getDefault()));
        t.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Px.of(ctx, topMargin);
        lp.bottomMargin = Px.of(ctx, 14);
        column.addView(t, lp);
        return t;
    }

    /** What is painted now, so an unchanged setting costs only a file stat. */
    private String painted;

    /**
     * Re-read the current profile's look (theme, accent, photo) and repaint. Cheap; no
     * rebuild. A photo is decoded off the main thread, and this runs again once it has loaded.
     */
    public void applyBackground() {
        Profiles.Profile p = Prefs.current(ctx);
        Bitmap photo = null;
        String state = p.id + "|" + p.theme + "|" + p.accent;
        if (!p.usePhoto) {
            Backgrounds.drop();
        } else {
            java.io.File f = Backgrounds.photoFile(ctx);
            if (f == null) Backgrounds.drop();
            String key = Backgrounds.keyOf(f);
            photo = Backgrounds.photoFor(key);
            if (f != null && photo == null) {
                Backgrounds.load(ctx, f, new Runnable() {
                    public void run() { applyBackground(); }
                });
                // Keep the previous photo up until the replaced one has loaded.
                photo = Backgrounds.lastPhoto();
                key = "loading";
            }
            state += "|photo|" + (photo == null ? "none" : key) + "|" + Backgrounds.dimAlpha(ctx);
        }
        if (state.equals(painted) && !previewing) return;
        painted = state;
        previewing = false;
        glowBg.setPhoto(photo, photo != null ? Backgrounds.dimAlpha(ctx) : 0);
        paint(Palette.resolve(p.theme, p.accent));
        topBar.setProfile(p);
    }

    /** Back to the saved look of the current profile, after a preview or a profile switch. */
    public void applyPalette() {
        painted = null;
        applyBackground();
    }

    /** Paint p everywhere without saving it; applyPalette() puts the saved one back. */
    public void previewPalette(Palette p) {
        previewing = true;
        paint(p);
    }

    private void paint(Palette p) {
        palette = p;
        glowBg.setPalette(p);
        if (focusRow == ROW_APPS || focusRow == ROW_TOP || !Prefs.current(ctx).glow) refreshGlow(false);
        topBar.setPalette(p);
        topFade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{p.base, (p.base & 0x00FFFFFF) | 0xCC000000, p.base & 0x00FFFFFF}));
        appRow.setPalette(p);
        inputRow.setPalette(p);
        moreRow.setPalette(p);
        for (TextView t : new TextView[]{appsLabel, inputsLabel, moreLabel, infoMeta}) t.setTextColor(p.sub);
        infoName.setTextColor(p.text);
    }

    // ---------------------------------------------------------------- public API

    public void updateClock() {
        Profiles.Profile p = Prefs.current(ctx);
        Date now = new Date();
        int mode = Prefs.clockMode(ctx);
        String clock = mode == Prefs.CLOCK_OFF ? null : new SimpleDateFormat(
                mode == Prefs.CLOCK_12H ? "h:mm" : "HH:mm", Locale.getDefault()).format(now);
        int df = Prefs.dateFormat(ctx);
        String date = null;
        if (df == Prefs.DATE_LONG) {
            String pat = android.text.format.DateFormat.getBestDateTimePattern(
                    Locale.getDefault(), "EEEdMMM");
            date = new SimpleDateFormat(pat, Locale.getDefault()).format(now);
        } else if (df == Prefs.DATE_SHORT) {
            date = android.text.format.DateFormat.getDateFormat(ctx).format(now);
        }
        String greeting = null;
        if (p.greeting) {
            int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            int g = Flavor.greetingFor(h);
            greeting = ctx.getString(g == Flavor.MORNING ? R.string.home_greet_morning_name
                    : g == Flavor.AFTERNOON ? R.string.home_greet_afternoon_name
                    : R.string.home_greet_evening_name, p.name);
            // A new line of flavour text for each profile that comes to the screen.
            if (flavor < 0 || !p.id.equals(flavorFor)) {
                flavor = Flavor.pick(RANDOM, flavor);
                flavorFor = p.id;
            }
        }
        topBar.setTexts(greeting, greeting == null ? null : Flavor.LINES[flavor], date, clock);
    }

    /**
     * Another profile is current: its look, its hidden tiles, a new greeting, and the
     * focus back on its most-used app. The other grid catches up through syncOrder().
     */
    public void profileChanged() {
        newFlavor();
        layoutGen++;
        applyPalette();
        build();
        updateClock();
        if (panels.isEmpty()) takeStartFocus();
    }

    /** A new flavour line next time the greeting is drawn (a fresh Home press). */
    public void newFlavor() { flavorFor = null; }

    /** Focus for a fresh Home press: Live TV when it comes first, else the most-used app. */
    public void takeStartFocus() {
        newFlavor();
        updateClock();
        if (liveTvFirst() && selectKey(AppModel.LIVETV_KEY)) return;
        if (!apps.isEmpty() && selectKey(apps.get(0).key())) return;
        takeFocus();
    }

    /**
     * Live TV first is in effect: switched on, and this TV has the tile. Without the tile
     * the switch is not offered in Settings, so a stale "on" must not keep the layout.
     */
    private boolean liveTvFirst() {
        return Prefs.liveTvFirst(ctx) && AppModel.hasLiveTv();
    }

    public void takeFocus() {
        if (focusedKey != null && selectKey(focusedKey)) return;
        List<List<AppModel.Item>> rows = new ArrayList<List<AppModel.Item>>();
        rows.add(apps);
        rows.add(inputs);
        rows.add(more);
        for (List<AppModel.Item> r : rows) {
            if (!r.isEmpty() && selectKey(r.get(0).key())) return;
        }
    }

    // ------------------------------------------------------------ SettingsPanel.Host

    private void setGridFocusable(boolean on) {
        int f = on ? ViewGroup.FOCUS_BEFORE_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        scroll.setDescendantFocusability(f);
        topBar.setDescendantFocusability(f);
    }


    public boolean isPanelOpen() { return !panels.isEmpty(); }

    /**
     * Hide whatever tile currently has focus.
     * @return true if something was hidden.
     */
    public boolean hideFocused() {
        AppModel.Item it = focusedItem();
        return it != null && hide(it, false);
    }

    /** The item with the focus, or null (top bar, nothing focused). */
    private AppModel.Item focusedItem() {
        if (focusedKey == null) return null;
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            if (focusedKey.equals(all.get(i).key())) return all.get(i);
        }
        return null;
    }

    private static int indexOfKey(List<AppModel.Item> items, String key) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) return i;
        return -1;
    }

    /** Settings and TV Health stay reachable so hidden tiles can always be restored. */
    private static boolean hideable(AppModel.Item it) {
        return it != null && it.type != AppModel.TYPE_SETTINGS && it.type != AppModel.TYPE_HEALTH;
    }

    /** Hold Select or Menu: the options card for the focused tile. Not in Simple mode. */
    private boolean openOptions() {
        stopHoldBar();
        AppModel.Item it = focusedItem();
        if (Prefs.simpleMode(ctx) || !hideable(it) || !panels.isEmpty()) return false;
        showPanel(OptionsMenu.build(ctx, it, new OptionsMenu.Actions() {
            public void open(AppModel.Item item) {
                if (listener != null) listener.onPick(item);
                act(item);
            }
            public void hide(AppModel.Item item, boolean everyone) { HomeGrid.this.hide(item, everyone); }
            public void close() { closePanel(); }
        }));
        return true;
    }

    /**
     * Hide a tile for this profile, or for everyone (the owner only). The focus moves to
     * the tile before it in the same row, or the first one left.
     */
    private boolean hide(AppModel.Item it, boolean everyone) {
        if (!hideable(it)) return false;
        Profiles.Profile me = Prefs.current(ctx);
        if (everyone && !Profiles.canSeeEveryone(me)) return false;
        // By key: the model may have been reloaded since the menu opened, so the item can be
        // a different object for the same tile.
        List<AppModel.Item> row = indexOfKey(apps, it.key()) >= 0 ? apps
                : indexOfKey(inputs, it.key()) >= 0 ? inputs : more;
        int at = indexOfKey(row, it.key());
        String prev = at > 0 ? row.get(at - 1).key() : (row.size() > 1 ? row.get(1).key() : null);
        if (everyone) {
            Prefs.setHidden(ctx, it.key(), true);
        } else {
            Profiles.Profile e = me.copy();
            e.hidden.add(it.key());
            Prefs.updateCurrent(ctx, e);
        }
        Toasts.show(ctx, root, ctx.getString(everyone ? R.string.options_hidden_all : R.string.options_hidden_for,
                it.label, me.name));
        layoutGen++;
        rebuild();
        if (prev != null) selectKey(prev);
        return true;
    }

    // ---- the hold bar: fills along the bottom of the tile while Select is held

    /** The Select release after a hold opened the options: it must not also act. */
    private boolean swallowSelectUp;
    private ValueAnimator holdAnim;
    private android.graphics.drawable.ColorDrawable holdDrawable;
    private View holdOn;

    private static boolean isSelect(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
    }

    private void startHoldBar() {
        stopHoldBar();
        final View cell = focusedKey == null ? null : cellsByKey.get(focusedKey);
        if (cell == null || Prefs.simpleMode(ctx) || !hideable(focusedItem())) return;
        holdOn = cell;
        holdDrawable = new android.graphics.drawable.ColorDrawable(palette.ring);
        final int h = Math.max(2, Math.round(Px.f(ctx, 5) / Math.max(1f, cell.getScaleY())));
        holdDrawable.setBounds(0, cell.getHeight() - h, 0, cell.getHeight());
        cell.getOverlay().add(holdDrawable);
        holdAnim = ValueAnimator.ofInt(0, cell.getWidth());
        holdAnim.setDuration(android.view.ViewConfiguration.getLongPressTimeout());
        holdAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                if (holdDrawable != null) {
                    holdDrawable.setBounds(0, cell.getHeight() - h, (Integer) a.getAnimatedValue(), cell.getHeight());
                }
            }
        });
        holdAnim.start();
    }

    private void stopHoldBar() {
        if (holdAnim != null) holdAnim.cancel();
        if (holdOn != null && holdDrawable != null) holdOn.getOverlay().remove(holdDrawable);
        holdAnim = null;
        holdDrawable = null;
        holdOn = null;
    }

    public void showPanel(final View v) {
        // Stop the grid, and any panel underneath, from stealing D-pad focus.
        setGridFocusable(false);
        View under = panels.isEmpty() ? null : panels.get(panels.size() - 1);
        // Taken first: blocking and hiding the page below can clear its focus.
        if (under != null) panelFocus.set(panelFocus.size() - 1, under.findFocus());
        if (under instanceof ViewGroup) {
            ((ViewGroup) under).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        // Only the top page is drawn, so a sheet over a sheet does not dim the grid twice.
        if (under != null) under.setVisibility(View.INVISIBLE);
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panels.add(v);
        panelFocus.add(null);
        v.post(new Runnable() { public void run() { v.requestFocus(); } });
    }

    public void closePanel() { closePanel(true); }

    /** Pages still sliding out: off the stack already, removed when their exit ends. */
    private final List<View> exiting = new ArrayList<View>();

    /**
     * Close the top page. Animated, it slides out while the page below (or the grid) is
     * already live, so a key pressed during the exit acts at once.
     */
    private void closePanel(boolean animate) {
        if (panels.isEmpty()) return;
        final View top = panels.remove(panels.size() - 1);
        panelFocus.remove(panelFocus.size() - 1);
        if (top instanceof ViewGroup) {
            ((ViewGroup) top).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        if (animate && ValueAnimatorScale.on()) {
            exiting.add(top);
            Panel.exit(top, new Runnable() {
                public void run() {
                    if (exiting.remove(top)) root.removeView(top);
                }
            });
        } else {
            root.removeView(top);
        }
        if (panels.isEmpty()) {
            setGridFocusable(true);
            takeFocus();
        } else {
            final View below = panels.get(panels.size() - 1);
            below.setVisibility(View.VISIBLE);
            if (below instanceof ViewGroup) {
                ((ViewGroup) below).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            }
            // Back on the row that opened the closed page, if it is still there.
            final View was = panelFocus.get(panelFocus.size() - 1);
            below.post(new Runnable() {
                public void run() {
                    if (was == null || !was.isAttachedToWindow() || !was.requestFocus()) {
                        below.requestFocus();
                    }
                }
            });
        }
    }

    /**
     * Back to the plain grid. A Home press lands here, not on a page left open earlier.
     * The first-run wizard stays, since losing it halfway through setup is worse.
     */
    public void closePanels() {
        // A Home press: no exit animations, the grid is wanted clean and at once, in the
        // saved look (a theme only being previewed must not stick).
        if (previewing) applyPalette();
        for (View v : exiting) root.removeView(v);
        exiting.clear();
        while (!panels.isEmpty()) {
            if (!Prefs.onboarded(ctx)
                    && panels.get(panels.size() - 1).getTag() instanceof BackHandler) {
                return;
            }
            closePanel(false);
        }
    }

    /** Whether animations run: Android's animator scale 0 means "off". */
    private static final class ValueAnimatorScale {
        static boolean on() {
            return Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled();
        }
    }

    /**
     * Take over the panels open in another grid, as they are. The overlay hands them to the
     * activity just before it lifts, so a page opened in the first seconds after Home stays
     * open on the same row. Skipped if a panel is already open here.
     * @return true if panels were moved.
     */
    public boolean adoptPanels(HomeGrid from) {
        if (from == this || from.panels.isEmpty() || !panels.isEmpty()) return false;
        // Settings changed on those pages since this grid last caught up.
        syncOrder();
        applyBackground();
        updateClock();
        View top = from.panels.get(from.panels.size() - 1);
        // Taken first: leaving its window clears the focus.
        View focus = top.findFocus();
        setGridFocusable(false);
        for (int i = 0; i < from.panels.size(); i++) {
            View v = from.panels.get(i);
            from.root.removeView(v);
            root.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            panels.add(v);
            panelFocus.add(from.panelFocus.get(i));
        }
        from.panels.clear();
        from.panelFocus.clear();
        from.setGridFocusable(true);
        from.panelHost.grid = this;
        from.panelHost = new PanelHost(from);
        if (focus == null || !focus.requestFocus()) top.requestFocus();
        return true;
    }

    /** Passes a panel's calls on to the grid it is in now; see panelHost. */
    private static final class PanelHost implements SettingsPanel.Host {
        HomeGrid grid;
        PanelHost(HomeGrid g) { grid = g; }
        public void rebuildGrid() { grid.rebuildGrid(); }
        public void showPanel(View v) { grid.showPanel(v); }
        public void closePanel() { grid.closePanel(); }
        public void profileChanged() { grid.profileChanged(); }
        public void previewPalette(Palette p) { grid.previewPalette(p); }
        public void applyPalette() { grid.applyPalette(); }
    }

    /** A setting changed from a panel in this grid; the other grid catches up via syncOrder(). */
    public void rebuildGrid() {
        layoutGen++;
        AppModel.invalidateMetrics();
        applyBackground();
        rebuild();
    }

    /** Carried across the overlay -> activity handoff so the highlight does not jump. */
    public String getSelectedKey() { return focusedKey; }

    /** Also carried across the handoff; restoring focus alone does not restore scroll. */
    public int getScrollY() { return scroll.getScrollY(); }

    /** Where each row's selection is, carried across the handoff with the focused key. */
    public int[] getRowScrollX() {
        return new int[]{appRow.selected(), inputRow.remembered(), moreRow.remembered()};
    }

    /**
     * Apply the vertical offset and the rows' selections after layout: scrollTo() before
     * layout is clamped to 0. The second post covers a late re-layout caused by restoring focus.
     */
    public void applyScroll(final int y, final int[] rowX) {
        if (rowX != null && rowX.length == 3) {
            if (focusRow != ROW_APPS) appRow.select(rowX[0], false, false);
            if (focusRow != ROW_INPUTS) inputRow.select(rowX[1], false, false);
            if (focusRow != ROW_MORE) moreRow.select(rowX[2], false, false);
        }
        if (y <= 0) return;
        final Runnable apply = new Runnable() {
            public void run() { scrollAnim.cancel(); scroll.scrollTo(0, y); }
        };
        scroll.post(new Runnable() {
            public void run() { apply.run(); scroll.post(apply); }
        });
    }

    /** Re-select by identity. */
    public boolean selectKey(String key) {
        if (key == null) return false;
        View v = cellsByKey.get(key);
        if (v == null) return false;
        // Restored, not moved: land at the final size and position without animating.
        instant = true;
        try {
            if (!v.requestFocus()) return false;
            if (v.isFocused() && focusedKey != null && focusedKey.equals(key)) refocus(key);
        } finally {
            instant = false;
        }
        return true;
    }

    /** Re-sort; rebuilds only if the order changed. @return true if rebuilt. */
    public boolean resort() {
        String keep = focusedKey;
        int y = scroll.getScrollY();
        int[] rx = getRowScrollX();
        if (!AppModel.resort(ctx)) return false;
        build();
        if (panels.isEmpty() && keep != null) selectKey(keep);
        applyScroll(y, rx);
        return true;
    }

    /**
     * The overlay and the activity each have a grid over one shared model, so a re-sort or
     * a settings change made in one leaves the other stale. Rebuild if that happened.
     * @return true if rebuilt.
     */
    public boolean syncOrder() {
        if (AppModel.order().equals(builtOrder) && builtGen == layoutGen) return false;
        android.util.Log.i("Homestead", "grid out of date, rebuilding");
        rebuild();
        return true;
    }

    /** Rebuild the views over the current model; nothing is re-decoded. */
    public void rebuild() {
        String keep = focusedKey;
        int row = focusRow;
        int y = scroll.getScrollY();
        int[] rx = getRowScrollX();
        build();
        // Leave focus alone while a panel is open, or the grid would take it from the panel.
        if (panels.isEmpty()) {
            // A tile that went away (uninstalled, hidden) hands the focus to its neighbour.
            if (keep == null || !selectKey(keep)) {
                List<AppModel.Item> r = row == ROW_APPS ? apps : row == ROW_INPUTS ? inputs : row == ROW_MORE ? more : null;
                int at = row == ROW_APPS ? rx[0] : row == ROW_INPUTS ? rx[1] : row == ROW_MORE ? rx[2] : 0;
                if (r == null || r.isEmpty() || !selectKey(r.get(Math.max(0, Math.min(at, r.size() - 1))).key())) {
                    takeFocus();
                }
            }
        }
        if (row == ROW_APPS) rx[0] = appRow.selected();
        applyScroll(y, rx);
    }


    // ---------------------------------------------------------------- layout

    /**
     * Layout reads metadata from other packages, so one malformed package must not leave a
     * blank home screen. On any failure, draw a minimal row with Settings and TV Health.
     */
    private void build() {
        builtOrder = AppModel.order();
        builtGen = layoutGen;
        updateClock();
        try {
            buildSections();
        } catch (Throwable t) {
            android.util.Log.e("Homestead", "grid build failed, drawing fallback", t);
            try { buildFallback(); } catch (Throwable ignored) { }
        }
    }

    /** Enough to get to Settings, using nothing that can throw. */
    private void buildFallback() {
        List<AppModel.Item> safe = new ArrayList<AppModel.Item>();
        List<AppModel.Item> all = AppModel.allItems();
        for (int i = 0; i < all.size(); i++) {
            int t = all.get(i).type;
            if (t == AppModel.TYPE_SETTINGS || t == AppModel.TYPE_HEALTH
                    || t == AppModel.TYPE_AMAZON_HOME) {
                safe.add(all.get(i));
            }
        }
        fill(new ArrayList<AppModel.Item>(), new ArrayList<AppModel.Item>(), safe, 0, false);
    }

    private void buildSections() {
        List<AppModel.Item> tv = new ArrayList<AppModel.Item>();
        List<AppModel.Item> ins = new ArrayList<AppModel.Item>();
        List<AppModel.Item> nonTv = new ArrayList<AppModel.Item>();
        List<AppModel.Item> system = new ArrayList<AppModel.Item>();
        boolean tvFirst = liveTvFirst();
        AppModel.Item liveTv = null;

        List<AppModel.Item> all = AppModel.visibleItems(ctx);
        for (int i = 0; i < all.size(); i++) {
            AppModel.Item it = all.get(i);
            if (it.type == AppModel.TYPE_LIVETV) {
                if (tvFirst) liveTv = it; else ins.add(0, it);
            } else if (it.type == AppModel.TYPE_INPUT) {
                ins.add(it);
            } else if (it.type == AppModel.TYPE_AMAZON_HOME
                    || it.type == AppModel.TYPE_SETTINGS || it.type == AppModel.TYPE_HEALTH) {
                system.add(it);
            } else if (it.tvApp && it.banner != null) {
                tv.add(it);
            } else {
                nonTv.add(it);
            }
        }
        if (!Prefs.showInputs(ctx)) ins.clear();
        if (!Prefs.showNonTv(ctx)) nonTv.clear();
        // Simple mode drops Settings and TV Health, unless nothing else is left: an empty
        // screen has nothing to focus, so the Menu presses that open Settings may not reach us.
        if (Prefs.simpleMode(ctx)) {
            List<AppModel.Item> rest = new ArrayList<AppModel.Item>();
            for (AppModel.Item it : system) {
                if (it.type != AppModel.TYPE_SETTINGS && it.type != AppModel.TYPE_HEALTH) rest.add(it);
            }
            if (!rest.isEmpty() || !nonTv.isEmpty() || !tv.isEmpty() || !ins.isEmpty() || liveTv != null) {
                system = rest;
            }
        }
        List<AppModel.Item> appList = new ArrayList<AppModel.Item>();
        if (liveTv != null) appList.add(liveTv);
        appList.addAll(tv);
        List<AppModel.Item> moreList = new ArrayList<AppModel.Item>(nonTv);
        moreList.addAll(system);
        boolean ranks = Prefs.current(ctx).ranks && Prefs.sort(ctx) == Prefs.SORT_LAST_USED;
        fill(appList, ins, moreList, liveTv != null ? 1 : 0, ranks);
    }

    private void fill(List<AppModel.Item> a, List<AppModel.Item> in, List<AppModel.Item> m,
                      int ranked, boolean ranks) {
        apps = a;
        inputs = in;
        more = m;
        firstRanked = ranked;
        ranksShown = ranks;
        cellsByKey.clear();
        focusedKey = null;
        appRow.setItems(a, ranks, ranked, palette);
        inputRow.setItems(in, palette);
        moreRow.setItems(m, palette);
        for (int i = 0; i < a.size(); i++) cellsByKey.put(a.get(i).key(), appRow.tile(i));
        for (int i = 0; i < in.size(); i++) cellsByKey.put(in.get(i).key(), inputRow.cell(i));
        for (int i = 0; i < m.size(); i++) cellsByKey.put(m.get(i).key(), moreRow.cell(i));
        int vis = a.isEmpty() ? View.GONE : View.VISIBLE;
        appsLabel.setVisibility(vis);
        appRow.setVisibility(vis);
        info.setVisibility(vis);
        inputsLabel.setVisibility(in.isEmpty() ? View.GONE : View.VISIBLE);
        inputRow.setVisibility(in.isEmpty() ? View.GONE : View.VISIBLE);
        moreLabel.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
        moreRow.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
        appRow.select(0, false, false);
        updateInfo(0);
    }

    // ---------------------------------------------------------------- focus

    /** A cell in row took the focus, by the D-pad (animated) or restored (instant). */
    private void onCellFocused(int row, int index) {
        boolean animate = !instant;
        int was = focusRow;
        focusRow = row;
        if (row == ROW_TOP) {
            focusedKey = null;
        } else {
            List<AppModel.Item> items = row == ROW_APPS ? apps : row == ROW_INPUTS ? inputs : more;
            if (index >= items.size()) return;
            focusedKey = items.get(index).key();
        }
        if (row == ROW_APPS) {
            appRow.select(index, true, animate);
            updateInfo(index);
        } else if (was == ROW_APPS || appRow.size() > 0) {
            appRow.select(appRow.selected(), false, animate);
        }
        if (row == ROW_INPUTS) inputRow.select(index, true, animate);
        else if (was == ROW_INPUTS) inputRow.select(inputRow.remembered(), false, animate);
        if (row == ROW_MORE) moreRow.select(index, true, animate);
        else if (was == ROW_MORE) moreRow.select(moreRow.remembered(), false, animate);
        info.animate().alpha(row == ROW_APPS || row == ROW_TOP ? 1f : 0.35f).setDuration(Motion.FOCUS_MS).start();
        boolean below = row == ROW_INPUTS || row == ROW_MORE;
        topBar.setHeadHidden(below, animate);
        topFade.animate().cancel();
        if (animate) topFade.animate().alpha(below ? 1f : 0f).setDuration(Motion.ROWS_MS).start();
        else topFade.setAlpha(below ? 1f : 0f);
        refreshGlow(animate);
        scrollToRow(row, animate);
    }

    /** The focused view already had focus (a restore): bring the visuals to it anyway. */
    private void refocus(String key) {
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).key().equals(key)) { onCellFocused(ROW_APPS, i); return; }
        for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).key().equals(key)) { onCellFocused(ROW_INPUTS, i); return; }
        for (int i = 0; i < more.size(); i++) if (more.get(i).key().equals(key)) { onCellFocused(ROW_MORE, i); return; }
    }

    /** The glow takes the selected app's colour; the theme's own when that is switched off. */
    private void refreshGlow(boolean animate) {
        int color = 0;
        if (Prefs.current(ctx).glow && !apps.isEmpty()) {
            AppModel.Item it = apps.get(Math.min(appRow.selected(), apps.size() - 1));
            color = it.type == AppModel.TYPE_LIVETV ? 0xFF168494 : it.glow;
            if (color != 0) Prefs.setLastGlow(ctx, color);
        }
        glowBg.setGlow(color, animate);
    }

    private void updateInfo(int index) {
        if (index >= apps.size()) return;
        AppModel.Item it = apps.get(index);
        infoName.setText(it.label);
        String hold = ctx.getString(R.string.home_hold_hint);
        if (it.type == AppModel.TYPE_LIVETV) {
            infoMeta.setText(ctx.getString(R.string.home_livetv_hint));
        } else if (ranksShown) {
            int rank = index - firstRanked + 1;
            infoMeta.setText((rank == 1 ? ctx.getString(R.string.home_rank_first)
                    : ctx.getString(R.string.home_rank_n, rank)) + "  ·  " + hold);
        } else {
            infoMeta.setText(hold);
        }
    }

    /** Where the page sits for each row: the greeting's space is reused below the apps. */
    private void scrollToRow(int row, boolean animate) {
        int y = 0;
        if (row == ROW_INPUTS && inputsLabel.getVisibility() == View.VISIBLE) {
            y = inputsLabel.getTop() - Px.of(ctx, 261);
        } else if (row == ROW_MORE && moreLabel.getVisibility() == View.VISIBLE) {
            y = moreLabel.getTop() - Px.of(ctx, 288);
        }
        y = Math.max(0, y);
        scrollAnim.cancel();
        if (animate) {
            scrollAnim.setIntValues(scroll.getScrollY(), y);
            scrollAnim.setDuration(Motion.ROWS_MS);
            scrollAnim.start();
        } else {
            scroll.scrollTo(0, y);
        }
    }

    // ---------------------------------------------------------------- the D-pad

    /** Rows that have something in them, top to bottom. */
    private int[] liveRows() {
        List<Integer> out = new ArrayList<Integer>();
        out.add(ROW_TOP);
        if (!apps.isEmpty()) out.add(ROW_APPS);
        if (!inputs.isEmpty()) out.add(ROW_INPUTS);
        if (!more.isEmpty()) out.add(ROW_MORE);
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    /** @return true if the key was a D-pad move this screen handled. */
    private boolean navigate(int key) {
        int dx = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = key == KeyEvent.KEYCODE_DPAD_UP ? -1 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx == 0 && dy == 0) return false;
        View now = root.findFocus();
        if (now == null) { takeFocus(); return true; }
        if (dx != 0) {
            View next = null;
            if (focusRow == ROW_TOP) {
                List<View> top = topBar.focusables();
                int i = top.indexOf(now);
                next = i < 0 ? null : (i + dx >= 0 && i + dx < top.size() ? top.get(i + dx) : null);
            } else if (focusRow == ROW_APPS) {
                next = appRow.tile(appRow.selected() + dx);
            } else if (focusRow == ROW_INPUTS) {
                next = inputRow.cell(inputRow.selected() + dx);
            } else {
                next = moreRow.cell(moreRow.selected() + dx);
            }
            if (next != null && next.requestFocus()) {
                root.playSoundEffect(dx < 0 ? SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
            }
            return true;
        }
        int[] rows = liveRows();
        int at = 0;
        for (int i = 0; i < rows.length; i++) if (rows[i] == focusRow) at = i;
        int to = at + dy;
        if (to < 0 || to >= rows.length) return true;
        View next;
        int row = rows[to];
        if (row == ROW_TOP) {
            List<View> top = topBar.focusables();
            next = top.get(top.size() - 1);
        } else if (row == ROW_APPS) {
            next = appRow.tile(appRow.selected());
        } else if (row == ROW_INPUTS) {
            next = inputRow.cell(inputRow.remembered());
        } else {
            next = moreRow.cell(moreRow.remembered());
        }
        if (next != null && next.requestFocus()) {
            root.playSoundEffect(dy < 0 ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
        }
        return true;
    }

    private void pick(List<AppModel.Item> items, int index) {
        if (index >= items.size()) return;
        AppModel.Item it = items.get(index);
        // Settings and TV Health open a panel in this window, so they are not reported as a
        // pick; that would make an overlay host dismiss itself.
        boolean leaves = it.type != AppModel.TYPE_SETTINGS && it.type != AppModel.TYPE_HEALTH;
        if (listener != null && leaves) listener.onPick(it);
        act(it);
    }

    // ---------------------------------------------------------------- launching

    /**
     * Says what opened a panel. Device -1 means the key was injected rather than sent by a
     * remote; HDMI-CEC keys also carry the HDMI source.
     */
    private void logOpen(String what) {
        String how = (lastKeyAt == 0L) ? "no key seen"
                : "key " + android.view.KeyEvent.keyCodeToString(lastKey) + " from device "
                        + lastKeyDevice + ", source 0x" + Integer.toHexString(lastKeySource)
                        + ((lastKeySource & android.view.InputDevice.SOURCE_HDMI)
                                == android.view.InputDevice.SOURCE_HDMI ? " (HDMI-CEC)" : "")
                        + ", " + (android.os.SystemClock.uptimeMillis() - lastKeyAt) + "ms ago";
        android.util.Log.i("Homestead", "opening " + what + " (" + how + ")");
    }

    private void act(AppModel.Item it) {
        if (it.type == AppModel.TYPE_SETTINGS) {
            logOpen(it.label);
            showPanel(SettingsPanel.build(ctx, panelHost));
            return;
        }
        if (it.type == AppModel.TYPE_HEALTH) {
            logOpen(it.label);
            showPanel(HealthPanel.build(ctx, panelHost));
            return;
        }
        if (it.type == AppModel.TYPE_LIVETV) {
            logOpen(it.label);
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("livetv://showLiveTVButtonContent/"));
                i.setComponent(new ComponentName(AppModel.LIVETV_PKG, AppModel.LIVETV_SVC));
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
                else ctx.startService(i);
            } catch (Throwable t) {
                Toasts.show(ctx, root, ctx.getString(R.string.home_livetv_failed));
            }
            return;
        }
        if (it.type == AppModel.TYPE_INPUT) {
            Intent i = new Intent(ctx, HdmiActivity.class);
            i.putExtra(HdmiActivity.EXTRA_INPUT_ID, it.inputId);
            i.putExtra(HdmiActivity.EXTRA_LABEL, it.label);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(i); } catch (Throwable t) { }
            return;
        }
        if (it.type == AppModel.TYPE_AMAZON_HOME) {
            HomeRedirectService.suspend(30000L);
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setClassName(AppModel.AMAZON_LAUNCHER, AppModel.AMAZON_HOME_CLS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Throwable t) { }
            return;
        }
        AppModel.noteLaunched(ctx, it);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(it.pkg, it.cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ctx.startActivity(i);
            return;
        } catch (Throwable t) { }
        try {
            Intent f = ctx.getPackageManager().getLaunchIntentForPackage(it.pkg);
            if (f != null) { f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(f); }
        } catch (Throwable t) { }
    }
}
