# Homestead Sleek Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Homestead's look app-wide (glow home screen, focus-grow app row, themes and accents, profiles with an owner, two-layer hiding, iOS-style panels, a PS3-style idle screen replacing the Fire OS screensaver, weather) while keeping every existing feature and the owner's current settings.

**Architecture:** Android-free logic (themes, colour maths, motion curves, profiles, settings transfer, JSON and weather parsing) lives in small plain-Java classes with JVM unit tests. Android view code (home screen, panels, idle screen) consumes them and is verified on the owner's TV over ADB with key events and screenshots. The overlay/activity double grid and its handover API stay as they are.

**Tech Stack:** Java (javac `--release 11`), Android framework only (minSdk 22, targetSdk 28), aapt2/d8/apksigner via `build.sh`, no Gradle, no libraries. JVM unit tests with a tiny in-repo runner.

**Spec:** `docs/superpowers/specs/2026-10-24-sleek-redesign-design.md` (read it first; the mockup it links is the visual reference, and its `THEMES`, `drawWave` and layout values are the source for numbers not repeated here).

## Global Constraints

- No Gradle, no libraries, no XML layouts; plain Java views. `build.sh` and `build.ps1` stay in step (this plan does not change either).
- minSdk 22, targetSdk 28. No `RenderEffect` (API 31); Fire OS 8 is API 30.
- javac `--release 11`: no Java language features newer than 11.
- No network traffic except GitHub releases (updates), localhost (tunnel) and Open-Meteo (weather, only once a location is set). No analytics.
- Home press → overlay shown stays about 170 ms (logcat line `overlay shown in N`); no animation delays input; every animation is interruptible.
- Springs settle with at most ~0.5% overshoot; panel in ≈ 550 ms on `cubic-bezier(.32,.72,0,1)`, out ≈ 300 ms.
- Respect the system animator scale (0 → instant).
- Owner profile is "Sinclair" on the owner's TV; only the owner sees Hide for everyone, the Everyone section and the System pages.
- Updates come from `theworstdrug/Homestead` releases, signed with this fork's own key.
- Every screen Homestead draws uses the current profile's palette (no hard-coded navy left).
- TV for verification: `192.168.1.98:5555`, model `AFTDEC012E`, Fire OS 8.1.8.5.

## Review Focus

1. A Home press while a panel is sliding out, or while a theme/accent is only being previewed: Home must land on the clean grid with the saved theme, never the previewed one, and the overlay→activity handover must not lose focus or scroll. (Tests: Task 7 step 6, Task 9 step 7.)
2. Apps whose banner is white, grey, black or missing: the glow must fall back to the palette's neutral colour, not paint the screen grey or white. (Test: Task 3 `bannerColorFallsBackForGreyBanners`.)
3. No network, a slow Open-Meteo, or a malformed reply: no weather chip or card, nothing waits on the network, no crash. (Tests: Task 12 `parseRejectsMalformed`, step 8.)
4. A non-owner profile is current in Simple mode: Settings → System must stay unreachable for them, yet the owner must always be able to get back (avatar → Sinclair). Deleting or renaming must never remove the owner. (Tests: Task 4 `ownerCannotBeDeleted`, `nonOwnerCannotSeeEveryone`; Task 9 step 6.)
5. An app is uninstalled, or hidden, while it is the focused/last app, or every TV app is hidden: no crash, the focus lands on the next app (or the Inputs row), and the glow colour updates. (Tests: Task 6 step 6, Task 10 step 6.)

---

## Shared setup for every task

```bash
export JAVA_HOME=~/.local/share/homestead-toolchain/jdk PATH=~/.local/share/homestead-toolchain/jdk/bin:$PATH
export ADB=~/Android/Sdk/platform-tools/adb ADB_TARGET=192.168.1.98:5555
cd ~/Projects/Homestead
```

- Unit tests: `tools/unit-tests.sh` → last line `OK n tests`.
- Build and install: `./build.sh install` → ends with `Success`.
- Screenshot: `$ADB -s $ADB_TARGET exec-out screencap -p > /tmp/claude-1000/-home-sin/496a1e24-2623-44e3-8a58-2829d8b2ef1e/scratchpad/shot-<name>.png`, then open it and compare with the mockup.
- Keys: `$ADB -s $ADB_TARGET shell input keyevent KEYCODE_DPAD_RIGHT` (also `_LEFT/_UP/_DOWN/_CENTER`, `KEYCODE_BACK`, `KEYCODE_MENU`, `KEYCODE_HOME`); long press: `input keyevent --longpress KEYCODE_DPAD_CENTER`.
- Work on branch `redesign`; commit at the end of each task with the attribution lines from the session.

---

### Task 1: Toolchain, signing key, unit-test runner, update repo

**Files:**
- Create: `tools/unit-tests.sh`, `tests/unit/T.java`, `tests/unit/UnitTests.java`
- Modify: `src/com/homestead/launcher/UpdateChecker.java:45` (`REPO`), `AndroidManifest.xml` (versionName `2.0.0-dev`, versionCode +1), `.gitignore` (`build-tests/`)
- Create (outside repo): `~/.homestead-keys/homestead-release.keystore`, `~/.homestead-keys/KEY-INFO.txt`

**Interfaces:**
- Produces: `tests/unit/T.java` with `static void eq(Object expected, Object actual, String what)`, `static void yes(boolean cond, String what)`, `static void near(double expected, double actual, double tol, String what)`; `UnitTests.main` calls each test class's `static void run()` and prints `OK n tests` or the first failure and exits 1. `tools/unit-tests.sh` compiles the Android-free sources listed in a `PURE=( … )` array plus `tests/unit/*.java` with `javac --release 11` into `build-tests/` and runs `UnitTests`. Later tasks append their class to `PURE` and their test class to `UnitTests.main`.

- [ ] **Step 1:** Create branch `redesign`. Generate the key: `keytool -genkeypair -keystore ~/.homestead-keys/homestead-release.keystore -alias homestead -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Homestead (theworstdrug)"` with a random 24-character password, and write `Password: <it>` to `KEY-INFO.txt` (mode 600). Tell the owner where the key is and that losing it breaks self-update.
- [ ] **Step 2:** Write `T.java`, `UnitTests.java` (with one self-test `T.eq(2, 1 + 1, "runner")`) and `tools/unit-tests.sh`.
- [ ] **Step 3:** Run `tools/unit-tests.sh`. Expected: `OK 1 tests`.
- [ ] **Step 4:** Set `REPO = "theworstdrug/Homestead"`, bump the version, run `./build.sh` (no install yet). Expected: `==> homestead.apk  v2.0.0-dev`.
- [ ] **Step 5:** Commit.

---

### Task 2: Settings transfer (import/export provider) and moving the owner's settings

**Files:**
- Create: `src/com/homestead/launcher/SettingsTransfer.java` (pure), `src/com/homestead/launcher/MigrateProvider.java`, `tests/unit/SettingsTransferTest.java`
- Modify: `AndroidManifest.xml` (provider `com.homestead.launcher.migrate`, `exported="true"`, `readPermission`/`writePermission` = `android.permission.DUMP`, like the background provider's guard: check how `BackgroundProvider` is protected and copy it), `README.md` (Removing Homestead: export first)

**Interfaces:**
- Produces: `SettingsTransfer.parse(String text) -> java.util.LinkedHashMap<String, Object>` where each line is `key<TAB>type<TAB>value`, type ∈ `b|i|l|s|set` (`set` values joined by `\u001f`); blank lines and `#` comments ignored; unknown types and malformed lines throw `IllegalArgumentException("line N: …")`. `SettingsTransfer.format(Map<String, ?> all) -> String` (inverse, sorted by key). `MigrateProvider`: `content write --uri content://com.homestead.launcher.migrate/prefs` replaces every key it names in Prefs' file (never deletes others); `content read --uri …/prefs` returns `format(sp.getAll())`. A successful write logs `Homestead: imported N settings`.

- [ ] **Step 1: Write the failing tests** in `SettingsTransferTest`: `parsesEveryType` (`"hidden\tset\tapp:a\u001fapp:b"` → a `Set` of 2; `"clock\ti\t1"` → `1`; `"simple_mode\tb\ttrue"` → `true`), `roundTrips` (`parse(format(m)).equals(m)`), `rejectsBadType` (message starts `line 1:`), `ignoresCommentsAndBlanks`.
- [ ] **Step 2:** Run `tools/unit-tests.sh`. Expected: compile failure (class missing).
- [ ] **Step 3:** Implement `SettingsTransfer` and `MigrateProvider`.
- [ ] **Step 4:** Run tests. Expected: `OK 5 tests`.
- [ ] **Step 5: Record the old app's state** (owner's TV, old app still installed). Save into `~/Documents/homestead-migration/` (not the repo): `pm list packages -d`; screenshots of Settings → Home screen, Background, hidden tiles list, Simple mode, and System pages (stepping with key events); the current values of the secure settings TV Health checks. From these, write `owner.prefs` in the transfer format using the keys in `Prefs.java` (`hidden`, `sort`, `clock`, `background_id`, `photo_dim`, `simple_mode`, `livetv_first`, `greeting`, `date_format`, `fire_os_icons`, `sec_inputs`, `sec_nontv`, `adb_reapply_on_boot`, `onboarded`=true, `setup_seen`=true, and `disabled_by_us` = the disabled packages that appear in `Debloat`'s list). Show the file to the owner before using it.
- [ ] **Step 6: Swap the app.** Ask the owner before uninstalling. Then `$ADB -s $ADB_TARGET uninstall com.homestead.launcher`; `./build.sh install`; `$ADB -s $ADB_TARGET push owner.prefs /data/local/tmp/`; `$ADB -s $ADB_TARGET shell "content write --uri content://com.homestead.launcher.migrate/prefs < /data/local/tmp/owner.prefs"`. Expected logcat: `imported N settings`. Then have the owner run Setup → Finish setup automatically on the TV (approve the new tunnel key, tick Always allow). Check Home lands on Homestead and the hidden tiles are still hidden. If a photo background was used, re-send it with the README's two commands.
- [ ] **Step 7:** Commit (code and README only).

---

### Task 3: Palette, accent, banner colour and motion curves (pure)

**Files:**
- Create: `src/com/homestead/launcher/Palette.java`, `src/com/homestead/launcher/BannerColor.java`, `src/com/homestead/launcher/Motion.java`, tests `tests/unit/PaletteTest.java`, `BannerColorTest.java`, `MotionTest.java`

**Interfaces:**
- Produces:
  - `Palette.THEME_IDS = {"midnight","oled","frost","ember"}`; `Palette.ACCENT_IDS = {"default","blue","violet","pink","red","orange","gold","green","teal"}`; `static Palette resolve(String themeId, String accentId)` (unknown ids → `midnight` / `default`). Public final int fields: `base, text, sub, chip, chipOn, chipOnText, rank, ring, overlay, sheet, neutral, light, scrimTo, waveInk` plus `float glowStrength`, `boolean darkInk` (true for Frost), `String name`. Values: the mockup's `THEMES` table (convert `rgba(r,g,b,a)` to ARGB). Accent (non-default) replaces `ring`, `chipOn` and `neutral`; `chipOnText` becomes black or white, whichever contrasts more with the accent.
  - `BannerColor.of(int[] argbPixels, int fallback) -> int`: the dominant colour among pixels with saturation ≥ 0.25 and value in 0.15–0.95 (HSV, 12 hue buckets weighted by saturation × value), returned at that bucket's average; fewer than 4% qualifying pixels → `fallback`.
  - `Motion.iosCurve(float t) -> float` (the cubic-bezier `.32,.72,0,1`); `Motion.easeOut(float t)` (`.2,.8,.2,1`); constants `FOCUS_MS=300, ROW_GLIDE_MS=420, ROWS_MS=480, GREET_MS=380, PANEL_IN_MS=550, PANEL_OUT_MS=300, POP_MS=420, HOLD_MS=550`.

- [ ] **Step 1: Write the failing tests:** `resolvesKnownTheme` (`resolve("frost","default").darkInk == true`, `.base == 0xFFE8EDF4`), `unknownFallsBackToMidnight`, `accentReplacesRingAndChip` (`resolve("midnight","red").ring != resolve("midnight","default").ring`, and `.text` unchanged), `accentTextContrasts` (gold → black text, blue → white), `bannerColorFindsRed` (a 10×10 mostly-`0xFFE50914` array on black → a red hue within 15° of it), `bannerColorFallsBackForGreyBanners` (all `0xFFFFFFFF`, all `0xFF808080`, all black → fallback), `iosCurveEnds` (`iosCurve(0)=0`, `iosCurve(1)=1` within 1e-4, `iosCurve(t) <= 1.006` for every t in 0..1 step 0.01).
- [ ] **Step 2:** Run tests. Expected: compile failure.
- [ ] **Step 3:** Implement the three classes; add them to `PURE`.
- [ ] **Step 4:** Run tests. Expected: all pass.
- [ ] **Step 5:** Commit.

---

### Task 4: Profiles model (pure) and Prefs integration

**Files:**
- Create: `src/com/homestead/launcher/Profiles.java` (pure), `tests/unit/ProfilesTest.java`
- Modify: `src/com/homestead/launcher/Prefs.java` (new keys `profiles`, `profile_current`, `profiles_migrated`; `hidden` keeps its key and now means "hidden for everyone"), `src/com/homestead/launcher/AppModel.java` (`visibleItems` applies both layers)

**Interfaces:**
- Produces: `Profiles.Profile` with fields `String id, name; int color` (index 0–7 into `Profiles.AVATAR_COLORS = {0xFF4C6FFF,0xFFE0703A,0xFF3A3A3A,0xFF7FA6C9,0xFFD94F8A,0xFF2FA37A,0xFF8B5CF6,0xFFC9A227}`); `String theme, accent; boolean glow, ranks, greeting, owner, usePhoto; java.util.Set<String> hidden`.
  `Profiles` (a list, immutable-style updates): `static Profiles decode(String s)`, `String encode()` (one profile per line, `|`-separated, hidden keys joined by `,`; names are A–Z and space only, ≤ 12 chars); `Profile owner()`; `Profile byId(String)`; `Profiles add(Profile)` (throws if 6 already); `Profiles remove(String id)` (throws `IllegalStateException` for the owner); `Profiles replace(Profile)`; `static boolean canSeeEveryone(Profile p)` = `p.owner`; `static Profiles fromLegacy(String ownerName, String backgroundId, boolean greeting)` (one owner profile; theme `midnight` unless the legacy background id is `black` → `oled`, `ember` → `ember`, `sky` → `frost`; `usePhoto` when the legacy background is the photo; glow/ranks on; greeting from legacy).
  In `Prefs`: `static Profiles profiles(Context)` (migrates once from legacy with owner name "Sinclair" when `profiles_migrated` is false, and sets the screensaver once — that part lands in Task 11); `static void setProfiles(Context, Profiles)`; `static Profiles.Profile current(Context)`; `static void setCurrent(Context, String id)`.
  `AppModel.visibleItems(Context)` hides an item if its key is in `Prefs.hidden` **or** in `Prefs.current(c).hidden`; Settings and TV Health never hidden.

- [ ] **Step 1: Write the failing tests:** `encodeDecodeRoundTrip` (2 profiles with hidden sets), `ownerCannotBeDeleted`, `maxSix`, `nonOwnerCannotSeeEveryone`, `legacyBecomesOwnerSinclair` (`fromLegacy("Sinclair","black",true)` → one owner named Sinclair, theme `oled`, greeting true), `legacyPhoto` (`fromLegacy(…, "photo", …).usePhoto`).
- [ ] **Step 2:** Run tests. Expected: compile failure.
- [ ] **Step 3:** Implement `Profiles`; add to `PURE`; wire `Prefs` and `AppModel`.
- [ ] **Step 4:** Run tests (all pass) and `./build.sh install`. On the TV the home screen still looks as before (the old UI) and the migrated hidden tiles are still hidden.
- [ ] **Step 5:** Commit.

---

### Task 5: Glow background

**Files:**
- Create: `src/com/homestead/launcher/GlowBackground.java` (a `Drawable`)
- Modify: `src/com/homestead/launcher/AppModel.java` (`Item.glow`, computed with `BannerColor.of` when a banner or icon bitmap is built, sampled from a ≤ 64×36 downscale), `src/com/homestead/launcher/Backgrounds.java` (keep photo loading; presets give way to `Palette`)

**Interfaces:**
- Consumes: `Palette`, `BannerColor`, `Motion`.
- Produces: `GlowBackground(Context c)`; `void setPalette(Palette p)`; `void setPhoto(Bitmap photoOrNull, int dimAlpha)`; `void setGlow(int color, boolean animate)` (cross-fade 900 ms); `void setIdleDim(boolean dim)`. Draws: palette `base` (or the dimmed photo), then two radial gradients in the glow colour at the mockup's positions and sizes (900×640 at (-160,-220) and 700×520 at right -200, top 40, scaled from 1280×720 to the screen), alpha `glowStrength` and `0.6 × glowStrength`, then a vertical gradient from transparent to `scrimTo` at 78%. Gradients are cached and only rebuilt on size or colour change; a cross-fade animates a single float, not a rebuild per frame. `AppModel.Item.glow` is an `int`, `0` when the colour fell back.

- [ ] **Step 1:** Implement both changes. Temporarily set the new drawable as the old grid's root background, with the glow following the focused tile's `glow` (or `Palette.neutral` when it is 0).
- [ ] **Step 2:** `./build.sh install`, then move focus Netflix → YouTube → Prime Video with key events, taking a screenshot after each. Expected: a soft red, then red, then blue glow top-left, with no banding visible and no grey wash on white-banner apps.
- [ ] **Step 3:** Check the frame rate during the cross-fade: `$ADB -s $ADB_TARGET shell dumpsys gfxinfo com.homestead.launcher reset`, move focus 10 times, then `dumpsys gfxinfo com.homestead.launcher` → "Janky frames" below 10%.
- [ ] **Step 4:** Commit.

---

### Task 6: The new home screen

**Files:**
- Create: `src/com/homestead/launcher/AppRow.java` (focus-grow, gliding app row with rank numbers), `src/com/homestead/launcher/PillRow.java` (Inputs and More rows), `src/com/homestead/launcher/TopBar.java` (greeting and flavour, weather slot, date, clock, avatar), `src/com/homestead/launcher/Flavor.java` (pure: the eight lines from the mockup's `FLAVOR`, `static String pick(java.util.Random r, int avoidIndex)` and `static int indexOf(String)`)
- Modify: `src/com/homestead/launcher/HomeGrid.java` (rebuilt around the new rows; the public API listed below is kept with the same signatures), `res/values/strings.xml` (greeting strings with a `%1$s` name; the flavour lines)
- Test: `tests/unit/FlavorTest.java`

**Interfaces:**
- Consumes: `Palette`, `GlowBackground`, `Motion`, `Profiles`/`Prefs.current`, `AppModel.Item.glow`.
- Produces (kept): `HomeGrid(Context, Listener)`, `root`, `takeStartFocus()`, `takeFocus()`, `selectKey(String)`, `getSelectedKey()`, `getScrollY()`, `getRowScrollX()`, `applyScroll(int, int[])`, `resort()`, `syncOrder()`, `rebuild()`, `rebuildGrid()`, `applyBackground()`, `updateClock()`, `showPanel(View)`, `closePanel()`, `closePanels()`, `adoptPanels(HomeGrid)`, `isPanelOpen()`, `hideFocused()`. New: `void applyPalette()` (re-reads `Prefs.current` and repaints everything without a rebuild); `void previewPalette(Palette p)` (paints `p` without saving; `applyPalette()` restores); `TopBar.setWeather(String tempText, String kind)` (`null` hides the chip; filled in by Task 12).
  Layout at 1080p = the mockup's 1280×720 values × 1.5: content left margin 96 px; apps row tiles 318×178 normal, 600×338 focused, gap 33, rank column 138 px with -39 px overlap; focused tile parked second from the left once scrolled (index 0 at the left edge); rows translate `[0, 0, -357, -495]` px for focus on top bar / apps / Inputs / More. Rank numbers are drawn only when the sort is by usage and `ranks` is on for the profile, and never on the Live TV tile. Live TV first on: Live TV is tile 0 of the apps row; off: it is the first Inputs pill. Non-TV apps, Amazon Home, Settings and TV Health go in More. Up from the apps row enters the top bar (weather chip if shown, then the avatar); the greeting hides whenever focus is below the apps row.

- [ ] **Step 1: Write the failing test** `FlavorTest.neverRepeatsImmediately` (for 200 picks with a seeded `Random`, `pick(r, last)` never equals `last`) and `eightLines`.
- [ ] **Step 2:** Run tests. Expected: compile failure.
- [ ] **Step 3:** Implement `Flavor`, `AppRow`, `PillRow` and `TopBar`, and rebuild `HomeGrid` around them. Old settings that no longer apply (`columns`, `nontv_wide`) stay in `Prefs` unused and drop out of Settings in Task 9.
- [ ] **Step 4:** Run tests, then `./build.sh install`.
- [ ] **Step 5: Verify on the TV,** with a screenshot at each stage:
  - Home press: the top bar shows "Good evening, Sinclair" and a flavour line, and the focused app is large with the others dimmed.
  - Right ×3: the row glides, keeping the focused tile second from the left.
  - Down: the greeting fades out and the rows move up.
  - Down: the More row shows.
  - Up ×2: the greeting comes back.
  - Up: focus moves to the avatar.
  - Check the overlay timing: `$ADB -s $ADB_TARGET logcat -d | grep "overlay shown in"`. The last 5 should be ≤ 200 ms.
- [ ] **Step 6: Focused app removed.** Install any small sideloaded app, focus it, then uninstall it with `$ADB uninstall` while it is focused. Expected: no crash (no `FATAL` in `logcat -d`), and focus moves to the neighbouring app.
- [ ] **Step 7:** Commit.

---

### Task 7: Panels, app-wide

**Files:**
- Modify: `src/com/homestead/launcher/Panel.java` (palette colours instead of the constants at lines 49–53; frosted sheet; `Motion` spring in and out; focused row = `chipOn` pill), `src/com/homestead/launcher/HomeGrid.java` (`closePanel()` plays the exit before removing the panel; a second Back during an exit finishes it at once), `src/com/homestead/launcher/OnboardingPanel.java`, `src/com/homestead/launcher/HdmiActivity.java`, `src/com/homestead/launcher/UpdatePanel.java`, `src/com/homestead/launcher/PhotoPanel.java`, plus every other file with hard-coded colours (find them with `grep -n '0xFF[0-9A-F]\{6\}' src/com/homestead/launcher/*.java`)
- Create: `src/com/homestead/launcher/Toasts.java` (the mockup's frosted pill toast, bottom centre, 2.6 s)

**Interfaces:**
- Consumes: `Palette`, `Motion`, `Prefs.current`.
- Produces: `Panel.exit(Runnable done)` (slides out over `PANEL_OUT_MS`, then runs `done`); `Toasts.show(Context, ViewGroup host, String text)`. The frosted fill is the palette's `sheet` colour over a downscaled (1/8), blurred copy of the current `GlowBackground` (a box blur in Java, cached per palette and glow colour). There is no live blur of the tiles.

- [ ] **Step 1:** Implement the Panel restyle, enter/exit and Toasts.
- [ ] **Step 2:** Replace the hard-coded colours in each listed file with palette values. The grep above should show only `Palette.java` and artwork (`tools/MakeArt.java`, `Debloat` data) afterwards.
- [ ] **Step 3:** `./build.sh install`. Open Settings and go Back, taking 4 screenshots during the slide in (`screencap` in a loop). Expected: the sheet arrives with no visible bounce, and on Back it slides out instead of vanishing.
- [ ] **Step 4:** Open TV Health, Debloat, Setup and Commands, and take a screenshot of each. Expected: all use the current theme, with no navy left.
- [ ] **Step 5:** Set the animator scale to 0 (`settings put global animator_duration_scale 0`). Panels should appear and disappear instantly. Restore it to `1.0`.
- [ ] **Step 6: Home mid-exit.** Open Settings, press Back, then press `KEYCODE_HOME` straight away. Expected: the clean grid with focus on the start tile, no half-drawn panel, and no `FATAL` in logcat.
- [ ] **Step 7:** Commit.

---

### Task 8: Profile picker and creation flow

**Files:**
- Create: `src/com/homestead/launcher/ProfilePicker.java` ("Who's watching?"), `src/com/homestead/launcher/ProfileCreator.java` (name → colour → theme), `src/com/homestead/launcher/OnScreenKeyboard.java` (A–Z, Space, Del, Next; 10 columns; reused by the weather city search)

**Interfaces:**
- Consumes: `Profiles`, `Prefs`, `Palette`, `HomeGrid.showPanel/closePanel/previewPalette/applyPalette`.
- Produces: `ProfilePicker.build(Context, HomeGrid host) -> View` (avatars at 198 px, the focused one scaled 1.1 with a ring; "Add" only when the current profile is the owner, Simple mode is off, and there are fewer than 6); `ProfileCreator.build(Context, HomeGrid host) -> View`; `OnScreenKeyboard(Context, int maxLen, Listener)` with `interface Listener { void onChange(String text); void onDone(String text); }`. Names: first letter of each word upper-case, the rest lower-case, at most 12 characters; Next is disabled until the trimmed name is non-empty. Picking a profile calls `Prefs.setCurrent`, `HomeGrid.applyPalette()` and picks a new flavour line.

- [ ] **Step 1: Write the failing tests** in `ProfilesTest`: `nameCasing` (`Profiles.cleanName("aNNA maRie") == "Anna Marie"`) and `nameTooLong` (13 characters are cut to 12). Add `static String cleanName(String)` to `Profiles`.
- [ ] **Step 2:** Run the tests. Expected: they fail.
- [ ] **Step 3:** Implement `cleanName`, the picker, the creator and the keyboard.
- [ ] **Step 4:** Run the tests, then `./build.sh install`.
- [ ] **Step 5: Verify on the TV.** Avatar → Add → type "TEST" on the keyboard with D-pad moves → Next → choose green → choose Frost. Expected: you land on the home screen as "Test" in Frost. Switch back to Sinclair through the avatar.
- [ ] **Step 6:** Commit.

---

### Task 9: Settings reorganised, owner gating, Theme and accent page

**Files:**
- Modify: `src/com/homestead/launcher/SettingsPanel.java`
- Create: `src/com/homestead/launcher/ThemePanel.java` (four preview cards in a 2×2 grid, plus a fifth "Photo" card when a photo has been sent (sets the profile's `usePhoto`), then the accent swatch row; all preview live), `src/com/homestead/launcher/ProfilesPanel.java` (list; rename, colour and delete per profile; the owner cannot be deleted)

**Interfaces:**
- Consumes: `Profiles.canSeeEveryone`, `HomeGrid.previewPalette/applyPalette`, `Panel`.
- Produces: the Settings hub sections in this order:
  - **"For <name>"**: Theme (value "Midnight · Blue"), Glow follows the app, Rank numbers, Greeting, Hidden apps (count and restore).
  - **"Everyone"** (owner only): Weather location (filled in by Task 12), Temperature units, Hidden for everyone (count and restore), Profiles.
  - **"Home screen"**: Sort, Clock, Date, Sections, Live TV first, Fire TV icons. The Columns and wide-tile rows are gone.
  - **"System"** (owner only): as it is today.

  The Theme page saves on Select and reverts on Back. Every close path — Back, Home and the overlay handover — calls `applyPalette()`, so a preview is never kept.

- [ ] **Step 1: Write the failing test** `ProfilesTest.settingsSectionsFor` against a new pure helper, `Profiles.settingsSections(Profile p) -> String[]`: the owner gets `{"for","everyone","home","system"}`; anyone else gets `{"for","home"}`.
- [ ] **Step 2:** Run the tests. Expected: they fail.
- [ ] **Step 3:** Implement the helper and the three panels.
- [ ] **Step 4:** Run the tests, then `./build.sh install`.
- [ ] **Step 5: Verify on the TV.** As Sinclair: Settings → Theme, move to Ember (the whole screen previews Ember), Back (back to the saved theme). Then pick Ember and pick the red accent (the focus ring turns red). Take screenshots.
- [ ] **Step 6:** Switch to the Test profile. Expected: Settings shows only "For Test" and "Home screen". With Simple mode on and Test current, Menu ×5 opens Settings with no System section, and the avatar can still switch to Sinclair.
- [ ] **Step 7: Preview never sticks.** As Sinclair: Settings → Theme, move to Frost, press `KEYCODE_HOME`. Expected: the home screen uses the saved theme, not Frost, in both the overlay and the activity. Press Home a second time to check after the handover.
- [ ] **Step 8:** Commit.

---

### Task 10: Hold Select / Menu options and two-layer hiding

**Files:**
- Create: `src/com/homestead/launcher/OptionsMenu.java`
- Modify: `src/com/homestead/launcher/HomeGrid.java` (key handling: long press of DPAD_CENTER/ENTER ≥ `HOLD_MS`, shown as a 5 dp bar filling along the tile's bottom, or MENU → options; Simple mode keeps Menu ×5 → Settings and allows no hiding), `src/com/homestead/launcher/AppRow.java` (the hold bar)

**Interfaces:**
- Consumes: `Profiles`, `Prefs.setHidden` (everyone), `Prefs.setProfiles` (per-profile set), `Toasts`.
- Produces: `OptionsMenu.build(Context, HomeGrid host, AppModel.Item it) -> View`, with entries Open, "Hide for <name>", "Hide for everyone" (owner only) and Cancel. The pop and its exit use `POP_MS`. After a hide, the toast reads "<App> hidden for <name> · Restore it in Settings" (or "for everyone"). Focus moves to the previous app, or to the first remaining one.

- [ ] **Step 1:** Implement it.
- [ ] **Step 2:** `./build.sh install`. Run `input keyevent --longpress KEYCODE_DPAD_CENTER` on Hulu. Expected: the menu appears, and Hulu does not launch.
- [ ] **Step 3:** Pick "Hide for Sinclair". Expected: the toast shows, Hulu is gone, and focus is on the previous app. Switch to Test: Hulu is visible there.
- [ ] **Step 4:** As Test, press MENU on YouTube. Expected: the menu has no "Hide for everyone".
- [ ] **Step 5:** Restore both from Settings. Expected: both apps are back.
- [ ] **Step 6: Everything hidden.** As Test, hide every TV app. Expected: there is no apps row and focus lands on the Inputs row with no crash. Restore them afterwards.
- [ ] **Step 7:** Commit.

---

### Task 11: Idle screen (replaces the Fire OS screensaver)

**Files:**
- Create: `src/com/homestead/launcher/IdleDream.java` (`DreamService`), `src/com/homestead/launcher/WaveView.java`, `src/com/homestead/launcher/Wave.java` (pure: the mockup's `yAt` formula and sheet constants)
- Modify: `AndroidManifest.xml` (the dream service with `android.permission.BIND_DREAM_SERVICE` and the `android.service.dreams.DreamService` intent filter), `src/com/homestead/launcher/Prefs.java` (the one-time screensaver switch from Task 4's `profiles(Context)` migration, done only when `WRITE_SECURE_SETTINGS` is granted), `src/com/homestead/launcher/HealthPanel.java` (the screensaver check accepts `com.homestead.launcher/.IdleDream` or `com.android.dreams.basic/.Colors`, flags anything else, and "Fix" sets Homestead's), `src/com/homestead/launcher/SetupPanel.java` ("Finish setup automatically" also sets the screensaver)
- Test: `tests/unit/WaveTest.java`

**Interfaces:**
- Consumes: `Palette`, `GlowBackground`, the last app's glow (persisted as the `last_glow` int in `Prefs` by `HomeGrid` whenever the focused app changes), `TopBar`'s clock format.
- Produces: `Wave.y(int sheet, float x, float u, float t, int height) -> float`, using the mockup's two sheets (base 0.56/0.60, a1 74/58, k1 .0041/.0035, s1 .32/-.26, a2 32/42, k2 .0093/.0071, s2 .21/.29, tw 52/34, k3 .0029/.0046, s3 .24/-.19, n 30/20, alpha .13/.09), with x in the mockup's 1280-wide units. `WaveView` draws it with `Canvas` on a hardware layer, at 60 fps through `postInvalidateOnAnimation`:
  - lines 1.1 px wide;
  - a blurred 80 px under-stroke (a pre-rendered soft stroke, since there is no `ctx.filter`);
  - 60 sparkles at alpha `(0.05 + 0.3·s³)·k`;
  - additive blending for light ink (`PorterDuff.Mode.ADD`), normal blending for Frost at k = 0.75;
  - a slight overall softening, done by drawing at ¾ resolution and scaling up.

  `IdleDream` shows a `GlowBackground` (profile palette and last glow), the `WaveView`, the clock at the bottom right and a slot for Task 12's forecast card. It is not interactive: any key wakes it.

- [ ] **Step 1: Write the failing tests:** `waveIsDeterministic` (the same inputs give the same output), `centreLineStaysOnScreen` (for t in 0..600 step 0.5 and x in 0..1280 step 40, `y(0, x, 0, t, 720)` is between 200 and 620), `twistSpreadsLines` (`|y(0,x,0.5,t) - y(0,x,-0.5,t)|` is at most 2 × 52 × 1.35 + 1).
- [ ] **Step 2:** Run the tests. Expected: compile failure.
- [ ] **Step 3:** Implement `Wave`, `WaveView`, `IdleDream`, the manifest entry, the TV Health change, the Setup change and the one-time switch.
- [ ] **Step 4:** Run the tests, then `./build.sh install`.
- [ ] **Step 5: Start it now.** `$ADB -s $ADB_TARGET shell settings get secure screensaver_components` should return `com.homestead.launcher/.IdleDream`. Then `$ADB -s $ADB_TARGET shell am start -n "com.android.systemui/.Somnambulator"`, or let it time out. Take a screenshot: the wave over the theme base and app glow, with the clock bottom right.
- [ ] **Step 6:** Check smoothness: `dumpsys gfxinfo com.homestead.launcher` after 30 s should show janky frames below 5%. A key press should wake to the home screen without opening anything.
- [ ] **Step 7:** Check TV Health: the screensaver row reads good, and switching to the basic screensaver works and also reads good.
- [ ] **Step 8:** Commit.

---

### Task 12: Weather

**Files:**
- Create: `src/com/homestead/launcher/Json.java` (pure, a minimal reader), `src/com/homestead/launcher/Weather.java` (pure: URLs, parsing, model), `src/com/homestead/launcher/WeatherStore.java` (fetch, cache and schedule), `src/com/homestead/launcher/WeatherPanel.java`, `src/com/homestead/launcher/CitySearchPanel.java`, `src/com/homestead/launcher/WeatherIcons.java` (sun, cloud, rain, partly cloudy, plus snow, fog and storm, drawn as stroked `Path`s at 2 px)
- Modify: `TopBar.java` (the chip), `IdleDream.java` (the frosted card: now plus 3 days), `SettingsPanel.java` (Weather location and Temperature units), `Prefs.java` (`wx_lat`, `wx_lon`, `wx_city`, `wx_units` = `f|c`), `SECURITY.md`, `README.md`
- Test: `tests/unit/JsonTest.java`, `tests/unit/WeatherTest.java`, with fixtures `tests/unit/fixtures/forecast.json` and `geocode.json`, captured once from the real API with `curl` and saved

**Interfaces:**
- Produces:
  - `Json.parse(String) -> Object`, returning `Map<String,Object>`, `List<Object>`, `String`, `Double`, `Boolean` or `null`. It throws `IllegalArgumentException` on malformed input.
  - `Weather.forecastUrl(double lat, double lon, boolean fahrenheit) -> String`, built from `https://api.open-meteo.com/v1/forecast` with:
    - `current=temperature_2m,weather_code`
    - `hourly=temperature_2m,weather_code`
    - `daily=weather_code,temperature_2m_max,temperature_2m_min`
    - `timezone=auto` and `forecast_days=5`
    - `temperature_unit=fahrenheit` when fahrenheit
  - `Weather.geocodeUrl(String name) -> String` (`https://geocoding-api.open-meteo.com/v1/search?count=8&name=…`, URL-encoded).
  - `Weather.parse(String json) -> Weather.Forecast`, with `now` (temp, kind, text), `hours[6]` starting from the current hour, and `days[5]` (label, lo, hi, kind).
  - `Weather.parsePlaces(String) -> List<Weather.Place>` (name, admin1, country, lat, lon).
  - `Weather.kind(int wmoCode) -> String`. The WMO mapping is: 0–1 sun, 2 partly, 3 cloud, 45/48 fog, 51–67 and 80–82 rain, 71–77 and 85–86 snow, 95–99 storm.
  - `WeatherStore.get(Context) -> Forecast` (cached, or null), `WeatherStore.refreshIfStale(Context, Runnable onNew)`. It fetches at most every 30 minutes, on a background thread, with a 10 s timeout, both while the home screen is in use and while the idle screen shows. Any failure keeps the cache or null and logs one line.

- [ ] **Step 1: Capture the fixtures.** `curl` one forecast and one geocode reply into `tests/unit/fixtures/`, using a city the owner names (ask), or "Seattle" if they don't.
- [ ] **Step 2: Write the failing tests:** `JsonTest.parsesNested`, `JsonTest.parseRejectsMalformed` (`{"a":`, `[1,,2]`, an empty string), `WeatherTest.parsesFixture` (5 days, 6 hours, `days[0].lo <= days[0].hi`), `WeatherTest.kindMapping` (0→sun, 3→cloud, 61→rain, 95→storm), `WeatherTest.urlHasUnits`, `WeatherTest.parseRejectsMalformed` (a reply missing `daily` throws `IllegalArgumentException`).
- [ ] **Step 3:** Run the tests. Expected: compile failure.
- [ ] **Step 4:** Implement the pure classes, then the store, the panels, the chip, the dream card and the settings rows. Update `SECURITY.md` and the README: what is sent (coordinates only), to whom, how often, and that it is off until a location is set.
- [ ] **Step 5:** Run the tests, then `./build.sh install`.
- [ ] **Step 6: Verify on the TV.** Settings → Weather location → search the city, then pick it. Expected: the chip appears. Then Up to the chip → Select. Expected: the panel slides in. Take screenshots of both.
- [ ] **Step 7:** Start the idle screen. Expected: the forecast card shows bottom left.
- [ ] **Step 8: No network.** Point the TV at a dead DNS (or block the app) — the easiest way is Settings → Network off. Press Home. Expected: Home is still around 170 ms, nothing waits on the network, the chip uses the cache or hides, and there is no `FATAL`. Turn the network back on.
- [ ] **Step 9:** Commit.

---

### Task 13: Finish — docs, version, release

**Files:**
- Modify: `README.md` (screenshots taken from the TV; the new features; the profiles and owner; Menu now opens options; the idle screen), `AndroidManifest.xml` (`versionName 2.0.0`), `SECURITY.md` (checked)

- [ ] **Step 1:** Run every unit test, then `./build.sh install`.
- [ ] **Step 2:** Do a full pass on the TV against the spec's Verification list, with screenshots saved into the repo's `screenshots/` for the README.
- [ ] **Step 3:** Ask the owner before publishing anything. With approval, push `redesign`, open a PR into `main` on the fork, and after merging create release `v2.0.0` on `theworstdrug/Homestead` with `homestead.apk` attached, so self-update works.
- [ ] **Step 4:** Commit.
