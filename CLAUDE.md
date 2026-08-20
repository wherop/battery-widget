# CLAUDE.md

Android homescreen widget that draws the device's battery charge as a battery that fills up.
Single-module Gradle project, package `dev.wherop.batterywidget`.

## Current state

Written in one pass from the design handoff on 2026-08-17, then built, tested and run on an
API 36 emulator the same day. It compiles clean, the 8 geometry tests pass, and the rendering
matches the prototype at all six sample sizes across the green/amber/red thresholds.

Charger detection was reworked onto `JobScheduler` the same day, replacing the manifest
broadcasts that Android silently drops, and then tested end to end on a Galaxy A53. **The
polling alarm, not the job, is what actually keeps the widget current** — read "Battery state
never arrives by broadcast" under Gotchas before touching either.

On 2026-08-20 the update path was simplified around that conclusion: the 0.5s fill animation is
gone (single bitmap push per update, `CssEase.kt` deleted, no more `goAsync()`), and the alarm
now polls once a minute while charging and every five minutes on battery (two in debug). Both
were then run on the A53 against a real charger — plug-in and unplug each landed on the first
tick after the event, and the armed period followed the charger in both directions. Both are compiled and
unit-tested; the charging switch has not yet been watched on hardware.

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

**A level change alone does not repaint a placed widget immediately** — nothing wakes the app
for it, so you are waiting on the alarm (two minutes in a debug build, one while charging). To force one, reinstall
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

`ChargingJobService` is the intended replacement — one job, `setRequiresCharging(true)`, fired
when charging starts. **On real hardware it did not earn its keep, and the polling alarm is
what actually works.** Measured on a Galaxy A53 (One UI 8, API 36) on 2026-08-17:

| Event | Time |
|---|---|
| Charger plugged in | 22:31:27 |
| Bolt appeared on the widget — via the **alarm** | ~22:37 |
| `JobScheduler` charging constraint finally satisfied | 22:46:41 |

**The alarm beat the job by nine minutes.** `dumpsys jobscheduler` reported `Power connected:
true` alongside `Battery charging: false` for over fifteen minutes of genuine AC charging, and
every other app on the device was equally stuck — Samsung's own `SystemFileBackupManager`,
Google's WorkManager jobs, 52 pending jobs with an unsatisfied CHARGING bit. Nothing to fix on
our side; just do not assume the constraint is prompt.

Three things to keep straight:

- **Never reintroduce the hold-open.** The first version returned `true` from `onStartJob` and
  never called `jobFinished()`, so `onStopJob` would fire on unplug and hand us the other edge
  for free. On the A53 that became a restart loop the moment the constraint satisfied: **six
  job instances a minute**, each redrawing the widget twice, presumably job quota stopping the
  held job and our own `onStopJob` re-arming it into a still-satisfied constraint. The service
  now redraws once, calls `jobFinished()`, and `onStopJob` does not re-arm.
- **Nothing catches an unplug except the alarm**, which is the price of the above and the
  reason the discharging interval is five minutes rather than fifteen — and the reason the
  alarm switches to one minute *while charging*, since that is the tick that has to notice it.
- `setRequiresBatteryNotLow(true)` was considered for the colour thresholds and **not** used.
  It only signals the recovery direction, and against the system's own low threshold, not the
  design's 25%/15%.

Re-arming the **job** is idempotent and happens from `onEnabled`, `BootReceiver` and every
alarm tick — deliberately **not** from `onStopJob`. It is also what brings the job back after
it fires, since a job is one-shot. Re-arming the **alarm** is a different matter: see below.

### Polling interval: five minutes on battery, one on a charger

`UpdateScheduler` polls every **5 min while discharging** and every **1 min while charging**. A
debug build shortens the discharging interval to **2 min**, because testing anything
charger-related against a five- or fifteen-minute alarm is unbearable — but not to one minute,
so that the switch between the two schedules stays observable in the build you are actually
testing with.

Five rather than `INTERVAL_FIFTEEN_MINUTES` because the alarm is the primary mechanism, not a
backstop. It is cheap to shorten because it is **non-wakeup** (`AlarmManager.ELAPSED`, not
`ELAPSED_WAKEUP`): it never wakes a sleeping phone, it fires when the device is already awake,
which is when somebody might be looking at the widget. It does give up the batching that the
`INTERVAL_*` constants get.

One while charging because the usual objection to frequent polling is battery cost, which does
not apply on mains, and because **nothing catches an unplug except the alarm** — polling fast
*while charging* is precisely what shortens unplug latency, while plug-in latency stays bounded
by the slow interval. A minute is also the floor: `AlarmManagerService` expands any repeating
period shorter than 60s ("Suspiciously short interval …; expanding to 60 seconds"). Chaining
one-shot alarms would evade the clamp, but it would also replace the repeating alarm the release
build runs on, so a test would no longer be testing the shipped mechanism. `adb shell dumpsys
alarm | grep -A2 batterywidget` shows which period is armed: 120000 or 60000 in debug, 300000 or
60000 in release.

**The switch lives in `BatteryWidgetUpdater`, not in the scheduler**, because that is the one
place that compares the state it just read against `WidgetState` and so can see the charging bit
flip. Re-arm only on that transition: `setInexactRepeating` restarts the period, so re-arming on
every tick would keep pushing the next tick away.

Debug-ness comes from `ApplicationInfo.FLAG_DEBUGGABLE`, **not** `BuildConfig.DEBUG`. The
`buildFeatures { buildConfig = true }` block existed only for that reference and is gone with
it, so nothing in the app depends on a generated class any more. (Studio was reporting
"Unresolved reference 'BuildConfig'" while `./gradlew` compiled the same source clean — a sync
artefact, but the flag makes it moot. Re-add the block before referencing `BuildConfig` again.)

`updatePeriodMillis` cannot follow the interval down — the system clamps that to 30 minutes.

**The emulator cannot trigger the charging constraint naturally.** `dumpsys battery set ac 1`
plus `set status 2` makes `BatteryStatus.read()` report charging correctly, but JobScheduler's
own tracker ignores the override — `dumpsys jobscheduler` keeps saying `Battery charging:
false`, and `android.os.action.CHARGING` is never broadcast. Drive the job directly instead:

```bash
adb shell cmd jobscheduler run -f dev.wherop.batterywidget 1   # fires onStartJob
adb shell cmd jobscheduler stop -u 0 dev.wherop.batterywidget 1 # fires onStopJob
adb shell dumpsys jobscheduler | grep -A3 "JOB #u0a.*ChargingJobService"
```

The constraint firing from a real charger **has** now been seen — on the A53, 15 minutes after
plugging in (see the table above).

### A fresh install sits in standby bucket NEVER, and the alarm is deferred a year

Measured on the A53 on 2026-08-20, immediately after installing and placing the widget without
ever opening the app:

```
$ adb shell am get-standby-bucket dev.wherop.batterywidget
50                                          # NEVER
policyWhenElapsed: requester=-1m47s app_standby=+364d23h56m12s
whenElapsed=+364d23h56m12s maxWhenElapsed=+364d23h56m12s
```

The alarm is armed correctly and then held by app standby for **a year** — so the widget's only
update mechanism never runs. Launching `PreviewActivity` once moved the app to bucket 10
(ACTIVE) and the same alarm went live (`whenElapsed=+1m44s`).

This is reachable by a real user: placing a widget does not require ever opening the app. AOSP's
`AppStandbyController` does treat a package with a bound widget as active, so the bucket may
well promote itself on the next evaluation — that was not waited out, and it is worth retesting
(fresh install, place the widget, leave the app unopened, check the bucket an hour later) before
concluding anything. Until then, take a widget that never updates on a fresh install as this,
not as a scheduling bug: check the bucket first.

### Testing on the Galaxy A53 over wireless debugging

USB debugging charges the phone, so the plug-in transition cannot be observed over a USB cable.
Use wireless debugging: pair with `adb pair HOST:PORT CODE` (the code must be an argument — the
interactive prompt has no stdin here), then `adb connect HOST:PORT` with the *different* port
from the Wireless debugging screen.

Two things will bite:

- **Samsung's Sleep Mode / power saving kills the connection.** It turns off wireless and
  suspends apps; `adb` hangs while still listing the device as `device`. Turn it off for the
  duration, keep the screen awake, and expect to `adb disconnect` and reconnect on a new port
  afterwards. The alarm and the job both survived it, for what it is worth.
- **On a fresh install nothing is scheduled until a widget is placed.** `onEnabled` fires on
  first placement, and `MY_PACKAGE_REPLACED` only fires on an update, never a first install.

`README.md` documents the same mechanism under "Staying current". Keep the two in step.

### Everything else

- `BatteryStatus.read()` needs an **application context**. A `BroadcastReceiver` context
  cannot register receivers, not even the null-receiver sticky read it uses.
- `ACTION_BATTERY_CHANGED` can't be manifest-registered at all, which is why the state is
  read on demand from the sticky broadcast rather than pushed.
- **The 0.5s fill transition is deliberately not implemented**, though the design specifies it.
  It was: 8 bitmaps posted from a `Handler`, eased by `CssEase.kt`, with `goAsync()` holding the
  broadcast open for the frames. Watched on a phone it earned nothing — the widget repaints on a
  timer, so consecutive draws are ~1% apart and the easing smoothed a change too small to see.
  Removed along with `CssEase.kt` and the `goAsync()`. Do not restore it without also giving the
  widget a real per-percent event to animate from.
- `WidgetState` survived that removal with a new job: a timer tick redraws nothing when neither
  the level nor the charging bit has moved, and the charging comparison is what re-arms the
  alarm. Pushes that must happen regardless — placement, resize, reboot — pass `force = true`.
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
