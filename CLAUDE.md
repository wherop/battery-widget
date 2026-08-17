# CLAUDE.md

Android homescreen widget that draws the device's battery charge as a battery that fills up.
Single-module Gradle project, package `dev.wherop.batterywidget`.

## Current state

Written in one pass from the design handoff on 2026-08-17, then built, tested and run on an
API 36 emulator the same day. It compiles clean, the 8 geometry tests pass, and the rendering
matches the prototype at all six sample sizes across the green/amber/red thresholds.

Charger detection was reworked onto `JobScheduler` the same day, replacing the manifest
broadcasts that Android silently drops. See "Battery state never arrives by broadcast" under
Gotchas for why, and for the one part of it the emulator cannot exercise.

## Build & run

```bash
./gradlew :app:test           # geometry unit tests (pure JVM, no device)
./gradlew :app:installDebug   # install on a connected device
adb shell am start -n dev.wherop.batterywidget/.PreviewActivity
```

Toolchain: Gradle 8.13, AGP 8.9.1, Kotlin 2.1.0, JDK 17+, compileSdk/targetSdk 36, minSdk 24.
The Gradle wrapper (scripts **and** JAR) is checked in, so `./gradlew` works from a fresh
clone. A cold Gradle cache needs network — `--offline` fails fetching `aapt2`.

### On this machine specifically

Nothing java-related is on `PATH`, so pass the JDK explicitly:

```bash
JAVA_HOME=$HOME/.jdks/jbr-21.0.11 ./gradlew :app:test
export PATH=$PATH:$HOME/Android/Sdk/platform-tools:$HOME/Android/Sdk/emulator
```

Use `~/.jdks/jbr-21.0.11` (JDK 21), **not** `/opt/android-studio/jbr` — that is JDK 25, which
AGP 8.9.1 rejects. Emulator AVD: `Medium_Phone_API_36.0` (1080x2400, density 2.625). Boot it
detached, then poll `getprop sys.boot_completed`.

### Exercising the widget

The widget reads live battery state, so drive it with the framework rather than editing code:

```bash
adb shell dumpsys battery set level 8    # red threshold (<15); 20 = amber, 62 = green
adb shell dumpsys battery set ac 1       # charging
adb shell dumpsys battery unplug
adb shell dumpsys battery reset          # hand control back to the real battery
```

**A level change alone does not repaint a placed widget** — nothing wakes the app for it, so
you are waiting on the 15-minute alarm. To see a change immediately, reinstall
(`MY_PACKAGE_REPLACED` does get through), drive `ChargingJobService` with `cmd jobscheduler`
(see Gotchas), or use `PreviewActivity`, which reads battery state on create and has its own
slider.

`PreviewActivity` (the launcher entry) renders the prototype's six sample spans with a level
slider and a charging toggle — the fastest way to compare against the HTML without touching a
homescreen. It is a development harness, not part of the widget.

To place the real widget on an emulator homescreen without touching the UI by hand: long-press
the wallpaper (`input swipe x y x y 1200`), tap **Widgets**, tap the app row, then
`input draganddrop` from the widget cell onto the homescreen. Verify with
`dumpsys appwidget`.

## Design is the source of truth

`design/` holds the imported design, and the visuals are **derived from it, not invented**:

- `design/Battery Widget.dc.html` — interactive prototype; all sizing math lives in its
  inline `renderVals()`. Open in a browser (it loads `design/support.js`).
- `design/HANDOFF.md` — the written spec: orientation rule, thresholds, tokens, behaviour.

Upstream is the Claude Design project `8096b452-8391-4e51-a989-4aa0505ec9f1`
("Battery level homescreen widget"), readable with the `claude_design` MCP (`DesignSync`:
`list_files`, `get_file`). It also holds reference screenshots under
`design_handoff_battery_widget/screenshots/` that were not copied into the repo.

If a visual changes, change `BatteryDesign.kt` to match the prototype — do not tune numbers
by eye in the renderer.

`BatteryGeometry.kt` was verified against `renderVals()` line by line on 2026-08-17: every
ratio, minimum and rounding matches, as does the bolt path and its `rgba(0,0,0,0.15)` outline.
Treat a divergence as a regression, not as licence to re-derive.

## Architecture

See the file-by-file table in [README.md](README.md). The invariants that matter:

- **All tokens live in `BatteryDesign.kt`.** Colours, ratios, minimums, durations. Nothing
  else should contain a literal colour or ratio.
- **`BatteryGeometry.kt` stays free of Android types** (it uses a plain `Box`, not `RectF`)
  so the sizing math is unit-testable on the JVM. `BatteryGeometryTest.kt` pins it against
  the prototype's own numbers at density 1. Keep it that way.
- **No third-party dependencies.** Framework only, by design — the APK stays tiny and the
  drawing code has no version drift. `junit` is test-only. `JobScheduler` is framework, so it
  is available if the charging fix needs it.
- Ratios are all fractions of the silhouette's *shorter side*, which is what makes a 1×1 and
  a 2×3 look like the same object.

## Gotchas already paid for

### Battery state never arrives by broadcast

A manifest receiver for `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED`, `BATTERY_LOW`
or `BATTERY_OKAY` **never fires.** Android drops all four — the Android 8+ implicit-broadcast
restriction, verified on API 36 via `dumpsys activity broadcasts`:

```
skipped by policy at enqueue: Background execution not allowed:
receiving Intent { act=android.intent.action.ACTION_POWER_DISCONNECTED }
to dev.wherop.batterywidget/.PowerEventReceiver
```

(That capture predates the rename — `PowerEventReceiver` is today's `BootReceiver`, minus the
four actions.) `android.os.action.CHARGING` / `DISCHARGING` do not get through either. `BOOT_COMPLETED` and
`MY_PACKAGE_REPLACED` **do** — they are on the exemption list, which is all `BootReceiver`
still listens for. Do not re-add the four dead actions, and note that `exported="false"` is
**not** the cause: `MY_PACKAGE_REPLACED` reaches the same receiver with the same flag, so do
not "fix" anything here by exporting the receivers.

`ChargingJobService` replaces them. One job, `setRequiresCharging(true)`, gives both edges of
the transition: the constraint becoming true starts it (`onStartJob` = plugged in), and the
constraint lapsing stops it (`onStopJob` = unplugged). It deliberately returns `true` from
`onStartJob` and never calls `jobFinished()` — the job stays nominally running for as long as
the device is on power, holding no thread, existing only to be stopped. Things to keep
straight about it:

- **`onStopJob` cannot distinguish an unplug from the execution time limit**, and does not
  need to: both redraw and re-arm. Expect a redraw cycle every few minutes while charging.
  The limit was ~5 minutes on the API 36 emulator, not the 10 the docs imply.
- **Re-arming is idempotent and happens from four places** — `onStopJob`, `onEnabled`,
  `BootReceiver`, and every 15-minute alarm tick. The alarm one is the important one: if the
  process is killed while the job is running, nothing ever reaches `onStopJob`, and without a
  periodic re-arm the watcher would stay dead until the next reboot.
- `setRequiresBatteryNotLow(true)` was considered for the colour thresholds and **not** used.
  It only signals the recovery direction, and against the system's own low threshold, not the
  design's 25%/15%. The 15-minute alarm covers level drift honestly; a second job would not.
- The 15-minute alarm in `UpdateScheduler` still exists and is still the only thing tracking
  the level falling. Confirmed scheduled via `dumpsys alarm`.

**The emulator cannot trigger the charging constraint naturally.** `dumpsys battery set ac 1`
plus `set status 2` makes `BatteryStatus.read()` report charging correctly, but JobScheduler's
own tracker ignores the override — `dumpsys jobscheduler` keeps saying `Battery charging:
false`, and `android.os.action.CHARGING` is never broadcast. Drive the job directly instead:

```bash
adb shell cmd jobscheduler run -f dev.wherop.batterywidget 1   # fires onStartJob
adb shell cmd jobscheduler stop -u 0 dev.wherop.batterywidget 1 # fires onStopJob
adb shell dumpsys jobscheduler | grep -A3 "JOB #u0a.*ChargingJobService"
```

Both callbacks were verified this way end to end: the bolt appears on run, disappears on
stop, and the job re-arms itself with `unsatisfied:0x1` (waiting on CHARGING). What is **not**
verified is the constraint firing from a real charger — that is platform plumbing, but it has
not been seen on hardware.

`README.md` documents the same mechanism under "Staying current". Keep the two in step.

### Everything else

- `BatteryStatus.read()` needs an **application context**. A `BroadcastReceiver` context
  cannot register receivers, not even the null-receiver sticky read it uses.
- `ACTION_BATTERY_CHANGED` can't be manifest-registered at all, which is why the state is
  read on demand from the sticky broadcast rather than pushed.
- The 0.5s fill transition posts 8 bitmaps from a `Handler`; the broadcast is held open with
  `goAsync()` so the process survives the frames. Skipped when the level didn't change.
  **This path has never actually executed** — the only two things that trigger it are the
  dead broadcasts and the 15-minute alarm. `CssEase.kt` is likewise untested and unrun.
- `adb shell am broadcast` cannot reach the receivers (`exported="false"`), so use
  `dumpsys battery` to move state and reinstall to force a repaint.
- Orientation is derived from the widget's box (`width > height`), not its grid span, because
  Android never exposes spans. See the deviations section in README.md before "fixing" it.
- `PreviewActivity` must apply window insets itself. `targetSdk 36` lays it out edge to edge,
  and without `applySystemBarInsets()` the readout and slider render underneath the status and
  action bars — invisible and untappable. It is a plain `Activity`, so there is no AndroidX
  helper; it reads `WindowInsets` directly.
- Android Studio rewrites the Gradle wrapper on import (it pinned `9.0-milestone-1` once).
  The project is on stable **8.13**; if the wrapper properties change unasked, that was Studio.
