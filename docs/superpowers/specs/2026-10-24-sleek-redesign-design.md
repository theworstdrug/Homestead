# Homestead sleek redesign — design

Date: 2026-10-24
Mockup (clickable, arrow keys / Enter / Esc, hold Enter on an app):
https://claude.ai/artifact/LWMpAafU1qxX8szPfWU9fi — it is the visual reference for everything
below. Where this document and the mockup disagree, this document wins.

## Intent

Homestead today is deliberately plain: flat gradient backgrounds, section headers and a tile
grid. The owner wants it to feel as polished as Google TV, while staying minimal and ad-free.
The home screen should be built around the app you are on, not around a grid. The greeting and
date currently sit pinned over the grid and the tiles scroll underneath them; that has to stop.

It is a family TV. Different people use it, so it gains profiles. The owner sets the defaults
(what is hidden for everyone), and each family member can personalise on top of that.

Everything Homestead already does keeps working: the Home button takeover, the overlay that
shows on a Home press, inputs, Live TV, TV Health, Debloat, the on-device tunnel and setup,
updates, Simple mode, and the photo background.

## Goals

1. **A new home screen** (see "Home screen" below), with the soft background glow taking its
   colour from the focused app.
2. **Smooth, deliberate motion** everywhere: focus grows and glides, panels slide in on an
   iOS-style curve and slide back out on Back.
3. **Four themes**, chosen per profile from Settings, previewed live behind the panel.
4. **Profiles** with a "Who's watching?" picker and an on-TV creation flow.
5. **Hidden apps in two layers**: hidden for everyone (owner's defaults), plus hidden per profile.
6. **Hold Select (and Menu) opens an options menu** for the focused app.
7. **An idle screen that replaces Fire OS's screensaver**: a PS3-style light wave over the
   current theme and app colour, with the clock and a forecast card.
8. **Weather**: a temperature chip on the home screen opening a forecast panel, plus the card
   on the idle screen.

## Non-goals (this project)

- **Smart-home control.** Agreed direction: Matter, by adding Homestead as an extra controller
  to devices already in Google Home (Matter multi-admin), with Home Assistant as an option
  later. It is a separate project with its own design. The home button in the mockup's top
  bar is **not** shipped in this project.
- A free colour picker. Custom colour is a short preset selection only (see Themes).
- Per-profile app order. Usage is counted per TV, as now.
- PINs or locks on profiles.
- Changing what Debloat, TV Health checks, setup or the tunnel do (only how they look).

## Anti-goals (would make this a failure even if it works)

- Ads, recommendations, sponsored content or anything that phones home beyond the weather
  request. No analytics.
- Slow Home. Homestead's Home press shows the overlay in about 170 ms today; the redesign must
  not make it noticeably slower, and no animation may delay input.
- Stutter. Focus moves and the idle wave must stay smooth on the slowest verified device
  (Fire TV Stick, `AFTSSS`).
- Losing a capability that exists today, or making the setup path harder.
- An exaggerated, bouncy feel. Springs are iOS-like: settle with at most a hair of overshoot.

## Constraints

- Plain Java Android views, no XML layouts, no Gradle and no libraries (aapt2/javac/d8 only;
  `build.sh` / `build.ps1` kept in step, checked by `tools/test-builds.sh`).
- minSdk 22, targetSdk 28. Fire OS 8 is Android 11 (API 30), so **`RenderEffect` blur (API 31)
  is not available**. Blur must be faked: pre-rendered soft gradients for the glow, and
  translucent fills (optionally over a downscaled copy of Homestead's own background) for the
  frosted panels.
- The home screen is built twice (the accessibility overlay and `MainActivity`) over one
  shared model, with focus, scroll and open panels handed over between them. The redesign
  keeps that handover working.
- Network: Homestead currently talks only to GitHub (updates) and localhost (the tunnel). The
  weather request is new outbound traffic and must be documented in `SECURITY.md` and the
  README, off until a location is set.
- No Google Play Services on Fire TV.
- **Signing and updates (decided by the owner):** this fork is built and signed with a new key
  of its own, not the upstream release key. The first install on the owner's TV therefore
  replaces the upstream app by uninstalling it, and its settings reset; that is accepted. The
  update checker points at this fork's releases (`theworstdrug/Homestead`) instead of
  `Sarg338/Homestead`, so the self-update keeps working with the new key.
- The build machine needs a JDK (with `javac`) and the Android SDK (build-tools 36.1.0,
  platform android-34) installed; neither is present yet.

## Home screen

From top to bottom (1080p TV; the mockup is the same layout at 1280×720):

- **Top bar** (fixed). Left: the greeting, "Good evening, <profile name>", with a random line
  of flavour text under it. Right: the weather chip (icon and temperature, only when a
  location is set), the date, the clock, and the profile avatar (a coloured circle with the
  initial). Up from the apps row moves focus into the top bar; Left/Right move along it.
- **The greeting hides itself**: it fades and slides up as soon as focus leaves the apps row
  (or the top bar) and comes back when focus returns. The rows scroll up under where it was,
  so nothing ever overlaps it.
- **Your apps** (one horizontal row): every TV app, in usage order. The focused app grows to a
  large tile (about 2× the others, 16:9) with its name and a short line under the row ("Your
  most-used app", "#3 in your apps", plus "Hold Select for options"). The other tiles dim. The
  row glides so the focused tile sits second from the left. Large outlined rank numbers sit
  beside each tile (Netflix Top 10 style), on by default per profile; they are hidden whenever
  the sort is not by usage.
- **Inputs**: Live TV and the HDMI/Composite inputs as quiet pills.
- **More**: non-TV apps, Amazon Home, Settings and TV Health as smaller tiles.
- **Background**: the theme's base colour with two very large, very soft glows in the focused
  app's colour, cross-fading as focus moves. When focus is on Inputs or More, the glow keeps the
  last app's colour (the mockup uses a fixed blue there; prefer keeping the app's colour).
  "Glow follows the app" off uses the theme's own neutral colour. A gentle gradient darkens
  the bottom of the screen so lower rows stay readable.
- **Photo background** stays available as a background choice under the theme. With a photo,
  the glows are drawn over the dimmed photo.
- **Live TV first**: Live TV becomes the first tile in the apps row (no rank number) and Home
  lands on it.
- **App colour**: each app's colour is taken from its banner (the dominant saturated colour),
  computed once when the banner is decoded and cached with it.

## Motion

- Focus: the focused tile grows (width, height and corner radius) and dims the rest over
  about 300 ms on a smooth ease-out; the row glides horizontally over about 400 ms. The
  greeting fades in about 380 ms; the rows scroll vertically in about 480 ms.
- Panels (Settings, Theme, Weather): slide in from the right on an iOS-style curve
  (`cubic-bezier(.32,.72,0,1)` or a critically damped spring) in about 550 ms with no more than
  about 0.5% overshoot; slide back out the same way in about 300 ms on Back. Sub-pages inside a
  panel cross-fade with a short slide.
- The options menu and profile picker scale and fade in (from about 94%) and back out.
- Every animation is interruptible: a new key press acts immediately and redirects the
  animation rather than waiting for it.
- Respect the system animation scale (the owner's TV now runs at 1×; if a user turns
  animations off, motion is instant).

## Themes

Midnight (deep navy), OLED (true black, faint glow), Frost (light, dark text) and Ember (warm
dark). Each theme is a set of named values: base colour, text, secondary text, glow strength,
neutral glow colour, chip and focused-chip colours, panel fill, ring colour and wave ink. The
exact values are in the mockup source (`THEMES`). Themes replace today's background presets.
The chosen theme is per profile.

**Accent colour (decided by the owner: a simple selection, not in depth).** Under the theme
cards, a row of swatches: "Theme default" plus about eight preset colours (for example blue,
violet, pink, red, orange, gold, green, teal). Chosen per profile. The accent replaces the
theme's own colour for the focus ring, the focused pill and settings row highlight, and the
neutral glow (used when "Glow follows the app" is off, and behind Inputs/More). Text colours and
the theme's base stay as they are, so every combination stays readable. Moving across swatches
previews live, like the theme cards. No free colour picker.

Settings → Theme shows four preview cards; moving between them previews that theme live on the
whole screen behind the panel. Select keeps it; Back restores the previous one.

## Profiles

- Up to six. Each has a name (up to 12 characters), an avatar colour (8 choices) and its own
  settings: theme, glow follows app, rank numbers, greeting, and its hidden apps.
- **Existing installs** get one profile ("Me" if no name is set; the first-run flow may ask)
  that inherits the current settings. The current hidden set becomes **hidden for everyone**.
- **Who's watching?** opens from the avatar or Settings → Profiles. It does not appear on every
  Home press; Homestead remembers the last profile.
- **Creating a profile**, three steps with progress dots: name on an on-screen keyboard (A–Z,
  Space, Del, Next; Next disabled until there is a name), avatar colour, theme (live preview).
  Back steps back. The new profile becomes current.
- Editing and deleting a profile: from Settings → Profiles → a profile (rename, colour,
  delete). Not in the mockup; keep it in the same panel style.
- Simple mode: the avatar and switching stay; "Add" is hidden.
- **Owner profile (decided by the owner):** one profile is the owner, "Sinclair" on the owner's
  TV (the migrated existing profile on other installs). Only while the owner profile is current
  can anyone see or use: **Hide for everyone** in the options menu, the whole **Everyone**
  section of Settings (weather location, units, hidden for everyone, profiles: add, edit,
  delete), and the System pages (Simple mode, Setup, TV Health, Debloat, Commands, updates).
  Other profiles see only "For <profile>" settings and "Hide for <profile>". "Add" on the
  profile picker shows only for the owner. The owner profile cannot be deleted. No PIN in this
  project; it is a convenience lock, not security.

## Hiding apps

- Hold Select (about 550 ms, with a thin bar filling along the bottom of the tile) or press
  Menu on an app: a frosted menu with **Open**, **Hide for <profile>**, **Hide for everyone**,
  **Cancel**. A toast confirms and says where to restore it.
- Settings shows each layer with its count and a restore page.
- In Simple mode, nothing can be hidden (as today), and Menu ×5 still opens Settings.
- Settings and TV Health can never be hidden (as today).

## The design is app-wide (decided by the owner)

Every screen Homestead draws uses this design, not only the home screen: Settings and its
sub-pages, TV Health, Debloat, Setup, the first-run flow, Commands, the update page, the photo
picker, the HDMI screen, toasts and dialogs. They share the theme of the current profile, the
frosted panel style, the focus treatment and the motion rules above. `Panel.java` (the shared
page builder) is the natural place to apply it once.

## Settings

The existing Settings sheet is restyled to match the mockup and reorganised:

- **For <profile>**: Theme, Glow follows the app, Rank numbers, Greeting, Hidden apps.
- **Everyone**: Weather location, Temperature units (°F/°C), Hidden for everyone, Profiles.
- **Home screen**: the existing options that still apply (sort, clock, date format, sections,
  Live TV first, Fire TV icons). Grid columns and wide non-TV tiles no longer apply and go.
- **System**: Simple mode, Setup, TV Health, Debloat, Commands, Check for updates (as today).

## Idle screen (replaces the Fire OS screensaver)

- Homestead ships a `DreamService`. Setup and TV Health set `screensaver_components` to it
  (Homestead already holds `WRITE_SECURE_SETTINGS`, and TV Health already manages this
  setting; on the owner's TV it is currently `com.android.dreams.basic/.Colors`). It starts on
  the system's own timeout (`screen_off_timeout`, 5 minutes on the owner's TV). TV Health
  offers switching back to the basic screensaver.
- Look: the current profile's theme base with the two glows in the last app's colour (the same
  blend as the home screen). Over it, a PS3-style wave: two translucent ribbons of 20–30 fine
  lines that roll and slowly twist, a soft glow under each, and faint twinkling sparkles
  drifting along the wave, all slightly softened. Light ink on the dark themes, dark ink on
  Frost. The clock sits bottom-right, and a frosted forecast card (now plus three days)
  bottom-left when weather is set.
- Drawing: on the GPU, kept smooth on the slowest verified device. The exact parameters are in
  the mockup's `drawWave`.
- Any key wakes it and does nothing else.

## Weather

- Provider: Open-Meteo (free, no API key or account). Location: chosen in Settings → Everyone
  by searching a city with the on-screen keyboard (Open-Meteo's geocoding). There is no
  automatic location. Off until a location is set.
- The forecast is fetched at most every 30 minutes while the screen is in use, and cached so
  Home never waits on the network. It fails quietly: no chip, no card.
- Weather panel: the city, current conditions (icon, temperature, description, high/low),
  the next six hours, and five days with a low-to-high bar.

## What changes for the person using it

- Menu on a tile now opens the options menu instead of hiding the tile straight away.
- Today's hidden tiles become "hidden for everyone".
- The background setting becomes Theme (photo remains available).

## Moving the owner's current settings across (decided by the owner: migrate, don't redo)

The installed upstream app sets `allowBackup="false"` and is signed with a different key, so
neither `adb backup`/`restore` nor an in-place upgrade can carry its settings, and its private
storage cannot be read without root. Instead:

1. **Before uninstalling**, record over ADB what the old app has done or shows:
   - the packages it switched off (`pm list packages -d`, matched against Debloat's list), so
     the new app's "Undo everything this app switched off" still covers them;
   - its home-screen settings and hidden tiles, read from its Settings pages (screenshots while
     stepping through them with `input keyevent`);
   - the system settings it changed (accessibility service, animation scales, screensaver, ad
     settings), which survive the uninstall or are re-applied by Setup.
2. **The new app accepts a one-time import** through a content provider, the same way the photo
   background arrives today: `adb shell content write --uri content://com.homestead.launcher.migrate/prefs`
   with a small key=value file built from step 1. Hidden tiles land in "hidden for everyone";
   the rest become the owner profile's ("Sinclair") settings or the per-TV settings.
3. **So this never happens again**, the same provider can also export the settings (`content
   read`), and the README's "Removing Homestead" section mentions it.
4. After install, Setup's "Finish setup automatically" (or the ADB commands) switches the Home
   button back on and re-grants permissions; the tunnel's ADB key has to be approved once on
   the TV, since it is a new app.

## Calls made during review (not discussed with the owner; flag if wrong)

- **Home screen settings are per TV**, not per profile: sort, clock, date format, sections,
  Live TV first, Fire TV icons. The **photo** is one image per TV (the provider and setup
  commands are unchanged); whether a profile uses it is per profile, as a "Photo" card on the
  Theme page. Rank numbers stay per profile and are simply not drawn when the sort is not by
  usage.
- **Live TV first stays optional** (off by default, tuner TVs only). Off: Live TV is the first
  Inputs pill and Home lands on the most-used app. On: Live TV is the first tile of the apps row
  (no rank number) and Home lands on it. The first tile sits at the left edge; "second from the
  left" applies once the row has scrolled.
- **Idle screen on upgrade**: on the first start of the new version, if Homestead holds
  `WRITE_SECURE_SETTINGS`, it sets `screensaver_components` to its own screensaver once. If it
  does not, Setup's "Finish setup automatically" does it. The TV Health screensaver row becomes
  a choice: Homestead's screensaver or the basic one both count as good; Amazon's ad
  screensaver is flagged, as today.
- **Weather refreshes while the idle screen shows**, at the same at-most-every-30-minutes rate,
  so the forecast card is never stale. `SECURITY.md` and the README say so.

## Left to the builder

- Class structure, file split, and how panels and the home screen share the theme.
- The exact pixel metrics at 1080p (scale the mockup's 1280×720 by 1.5), and how the banner
  colour is extracted.
- How the frosted panel fill is produced without `RenderEffect`.
- How the wave is drawn (GL, or a hardware-accelerated canvas if it is smooth enough).
- Preference keys and their migration.
- Order of delivery. Suggested: (1) theme engine and the new home screen with motion,
  (2) panels and Settings restyle, (3) profiles and two-layer hiding, (4) idle screen,
  (5) weather. Each should leave a working app.

## Verification

- Build with `build.sh`; `tools/test-builds.sh` still passes.
- Install on the owner's TV (`AFTDEC012E`, Fire OS 8.1.8.5) over ADB and check: Home button
  and overlay timing, handover between the overlay and the activity, every row and panel with
  the D-pad, hold Select and Menu, Simple mode, profile creation, theme preview, idle screen
  start and wake, and weather with and without network.
- Screenshots (`adb exec-out screencap -p`) compared against the mockup.
