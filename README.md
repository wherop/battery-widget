# Battery Widget

An Android homescreen widget that shows the device's charge as a battery that fills up. One
provider covers every size: the shape is redrawn from the box the launcher hands out, so it
stands vertical in square or tall slots and lies horizontal in wide ones, with radii, bump,
type and bolt all scaling from the shape's shorter side.

Native implementation of the design in `design/` — see
[`design/HANDOFF.md`](design/HANDOFF.md) for the written spec and
[`design/Battery Widget.dc.html`](design/Battery%20Widget.dc.html) for the interactive
prototype (open it in a browser; the slider and charging toggle are preview controls, not
widget UI).

## Build

```bash
# Android Studio: open the project directory and let it sync.
# CLI (needs a JDK 17+ and the Android SDK, with sdk.dir set in local.properties or
# ANDROID_HOME exported):
./gradlew :app:installDebug   # install on a connected device
./gradlew :app:test           # geometry unit tests
```

The Gradle wrapper is checked in, so `./gradlew` works from a fresh clone. Toolchain:
Gradle 8.13, AGP 8.9.1, Kotlin 2.1.0, compileSdk/targetSdk 36, minSdk 24. A cold Gradle
cache needs network — `--offline` fails fetching `aapt2`.

Then long-press the homescreen → Widgets → **Battery Widget**, and resize it to see the
shape re-derive itself. `PreviewActivity` (the app's launcher entry) renders the same six
sample sizes as the prototype with a level slider, which is the fastest way to compare the
port against the HTML side by side.

## How it works

Widgets are `RemoteViews`, which cannot express flex layout, gradients, or a shape that
depends on its own measured size — so the widget is a single full-bleed `ImageView` and the
battery is drawn into a bitmap at the launcher's exact pixel size. That is approach 2 in the
handoff. Jetpack Glance was the handoff's first suggestion, but Glance has no canvas
primitive either: the shape would still come down to a hand-drawn bitmap, with a Compose
dependency on top. Nothing here needs anything outside the platform framework.

| File                                                                                            | Role                                                                         |
|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| [BatteryDesign.kt](app/src/main/java/dev/wherop/batterywidget/BatteryDesign.kt)                 | Every colour, ratio, minimum and duration from the prototype, in one place   |
| [BatteryGeometry.kt](app/src/main/java/dev/wherop/batterywidget/BatteryGeometry.kt)             | Pure sizing math: orientation, silhouette, body, bump, fill and bend boxes   |
| [BatteryRenderer.kt](app/src/main/java/dev/wherop/batterywidget/BatteryRenderer.kt)             | Canvas drawing — track, sheen, fill, leading-edge bend, bolt, percentage     |
| [BatteryWidgetUpdater.kt](app/src/main/java/dev/wherop/batterywidget/BatteryWidgetUpdater.kt)   | Renders into every placed widget, including the 0.5s fill/colour transition  |
| [BatteryWidgetProvider.kt](app/src/main/java/dev/wherop/batterywidget/BatteryWidgetProvider.kt) | Widget lifecycle: placement, periodic update, resize                         |
| [BatteryStatus.kt](app/src/main/java/dev/wherop/batterywidget/BatteryStatus.kt)                 | Level and charging state from the sticky `ACTION_BATTERY_CHANGED` broadcast  |
| [ChargingJobService.kt](app/src/main/java/dev/wherop/batterywidget/ChargingJobService.kt)       | Notices the charger going in and coming out, via a `JobScheduler` constraint |
| [BootReceiver.kt](app/src/main/java/dev/wherop/batterywidget/BootReceiver.kt)                   | Re-arms the alarm and the charging job after a reboot or an app update       |
| [UpdateScheduler.kt](app/src/main/java/dev/wherop/batterywidget/UpdateScheduler.kt)             | Inexact 15-minute polling alarm                                              |
| [WidgetSize.kt](app/src/main/java/dev/wherop/batterywidget/WidgetSize.kt)                       | The pixel box a widget instance currently occupies                           |
| [PreviewActivity.kt](app/src/main/java/dev/wherop/batterywidget/PreviewActivity.kt)             | Development harness mirroring the prototype's preview                        |

### Staying current

`ACTION_BATTERY_CHANGED` fires constantly and cannot be received from the manifest, and a
background process holding a runtime receiver would be killed anyway. So the widget polls: an
inexact non-wakeup alarm every **5 minutes** in release and every minute in debug builds, with
`updatePeriodMillis` (30 minutes) as a backstop. Each refresh reads the current value from the
sticky broadcast.

This alarm is the widget's primary mechanism, not a fallback — see below. Shortening it is
affordable because it is non-wakeup: it never wakes a sleeping phone, only piggybacking on
moments the device is already awake, which is when someone might be looking at the widget.

Plugging in and unplugging are the transitions that need to show up promptly, since the
charging bolt appears and disappears with them. The obvious mechanism — manifest receivers
for `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED` — **does not work.** Android's
implicit-broadcast restriction drops those, along with `BATTERY_LOW` and `BATTERY_OKAY`,
before they reach the app; verified on API 36:

```
skipped by policy at enqueue: Background execution not allowed:
receiving Intent { act=android.intent.action.ACTION_POWER_DISCONNECTED }
to dev.wherop.batterywidget/.PowerEventReceiver
```

(`PowerEventReceiver` was the receiver's name when that was captured; it is now
`BootReceiver`.) So the charger is watched with `JobScheduler` instead, which is the framework's sanctioned
replacement. One job with `setRequiresCharging(true)` gives both edges of the transition: the
constraint becoming true starts the job, and the constraint lapsing stops it, so
`onStartJob` means "plugged in" and `onStopJob` means "unplugged". The job stays nominally
running for as long as the device is on power — it holds no thread and does no work, and
exists only to be stopped.

That was the design. **On real hardware it did not pay off.** Measured on a Galaxy A53 (One UI
8, API 36): the charger went in at 22:31:27, the bolt appeared at ~22:37 because the *alarm*
fired, and the charging constraint was not satisfied until 22:46:41 — over fifteen minutes,
nine minutes behind the alarm. `dumpsys jobscheduler` reported `Power connected: true` and
`Battery charging: false` simultaneously throughout, and 52 pending jobs across the device
(Samsung's own included) were equally stuck, so this is platform behaviour rather than a bug
here.

Holding the job open also misfired badly. The job originally returned `true` from `onStartJob`
and never finished, so that `onStopJob` would signal the unplug; once the constraint finally
satisfied, that became a restart loop of six job instances a minute, each redrawing the widget
twice. The service now redraws once and calls `jobFinished()`, and `onStopJob` no longer
re-arms.

So `ChargingJobService` is an opportunistic fast path that should help on devices whose
platform reports charging promptly, and the alarm is what actually carries the widget — it is
the only thing that notices an unplug at all. Re-arming happens from `onEnabled`,
`BootReceiver` and every alarm tick.

`BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are exempt from the restriction and do arrive,
which is all [BootReceiver.kt](app/src/main/java/dev/wherop/batterywidget/BootReceiver.kt)
now listens for. `exported="false"` was never the cause. See `CLAUDE.md` for the full
diagnosis and for how to drive the job from `adb`.

### The 0.5s transition

The prototype animates the fill and its colour with `transition: 0.5s ease`. A widget has no
animator, so `BatteryWidgetUpdater` posts eight bitmaps across 500ms, eased with the same
`cubic-bezier(0.25, 0.1, 0.25, 1)` curve, holding the broadcast open with `goAsync()` while
they run. The percentage text and the charging bolt switch instantly, as they do in CSS.
Level changes of 0% skip the animation entirely, so the common case is a single push.

This path does run — every alarm tick requests an animated update, and the level moves between
ticks — but the frames have not been inspected closely, and `CssEase.kt` has no test of its
own.

## Deviations from the handoff

- **Orientation is read from the widget's box, not its grid span.** The spec's rule is
  `rows >= columns → vertical`. Android exposes a widget's size, never its span, and
  launcher cell metrics are not knowable, so this uses `width > height → horizontal`. For
  square-ish cells the two rules agree on every span; they can differ only on strongly
  non-square cells (e.g. a 4x3 span in cells that are much taller than wide), where the
  box-based answer is the one that actually fits.
- **The widget-picker preview is a static vector** ([battery_glyph.xml](app/src/main/res/drawable/battery_glyph.xml),
  the vertical shape at 62%) without the percentage text, since vector drawables cannot draw
  text. The live widget is unaffected.
- **No tap target.** The design does not specify one. Opening battery settings on tap would
  be a two-line addition in `BatteryWidgetUpdater.draw`.

## Status

Built and tested on an API 36 emulator, then run end to end on a Samsung Galaxy A53 5G (One UI
8, API 36, density 450) on 2026-08-17. It compiles clean, the eight geometry tests pass, and
the rendering matches the prototype at all six sample sizes across the green, amber and red
thresholds, charging and not. `BatteryGeometry.kt` was additionally checked line by line
against the prototype's `renderVals()`; the geometry stays pinned by
[BatteryGeometryTest.kt](app/src/test/java/dev/wherop/batterywidget/BatteryGeometryTest.kt).

What is verified on a real device, and what is not:

| Area                                       | State                                                      |
|--------------------------------------------|------------------------------------------------------------|
| Drawing, geometry, thresholds, bolt        | Verified against the prototype, emulator and phone         |
| Widget placement, sizing, resize           | Verified on a real homescreen                              |
| Reading battery level and charging state   | Verified                                                   |
| Reboot / reinstall refresh                 | Verified                                                   |
| Polling alarm                              | Verified firing; it drives every update below              |
| Charger in → bolt appears                  | Verified, via the alarm — ~6 min at the 15-minute interval |
| Charger out → bolt disappears              | Verified, via the alarm — 34s at the 1-minute interval     |
| `ChargingJobService` firing from a charger | Verified, but 15 min after plug-in, behind the alarm       |
| 0.5s fill animation and `CssEase.kt`       | Has now run; not inspected frame by frame                  |
| Non-Samsung hardware                       | Untested — the charging constraint may well be prompt      |
