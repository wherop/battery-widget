# Handoff: Android Battery Level Homescreen Widget

> Imported verbatim from the Claude Design project
> "Battery level homescreen widget" (`design_handoff_battery_widget/README.md`).

## Overview
A homescreen widget that visualizes the device's current battery charge as a simplified battery-icon shape that fills up like a gauge. It supports every widget size from 1×1 up to full-screen, re-orienting itself (vertical vs. horizontal) based on the grid it's placed in.

## About the Design Files
The bundled file (`Battery Widget.dc.html`) is a **design reference built in HTML/CSS**, used to prototype the look, proportions, and sizing logic. It is not production code — Android home screen widgets cannot render arbitrary HTML/CSS. The task is to **recreate this design natively** using Android's widget APIs (see "Recommended Android Implementation" below), matching the shapes, colors, proportions, and behavior described in this document.

Open the HTML file in a browser to interact with it: drag the slider to preview any charge level, toggle "Charging" to preview the bolt icon, and see all six sample sizes update together.

## Fidelity
**High-fidelity** for visual design: exact colors, corner radii, proportions, and sizing math are final. Treat the interactive slider/toggle in the HTML file as a **preview tool only** — it exists to demonstrate the fill animation and threshold color changes, not as UI to replicate in the widget itself (a real widget has no slider; it reads live battery state).

## Widget Behavior

### Orientation logic
The battery shape is measured in the widget's grid span (columns × rows):
- **rows ≥ columns** (including square, e.g. 1×1, 2×2, 3×3): battery stands **vertical**, bump on top, fills bottom → top.
- **columns > rows** (wider than tall, e.g. 2×1, 3×1): battery rotates 90° clockwise to lie **horizontal**, bump on the right, fills left → right.

### Fill & color
- Fill height/width is proportional to current battery percentage (0–100%).
- Fill color by threshold:
  - **≥ 25%** → green `#1EDB6A`
  - **15–24%** → amber `#FFB020`
  - **< 15%** → red `#FF453A`
- Colors transition smoothly (0.5s ease) as the level changes, so the widget can animate rather than jump when Android posts an updated reading.

### Charging state
When the device is charging, a white lightning-bolt icon appears centered inside the battery, on top of the fill:
- **Vertical layout**: bolt above the percentage number (stacked, centered).
- **Horizontal layout**: bolt to the left of the percentage number (row, centered).
- When not charging, only the percentage number is shown, centered in the same spot.
- The percentage number always renders in white with a soft drop shadow so it reads over both the fill and the empty track, minimum 14px (never smaller, even at 1×1).
- The bolt's minimum size scales with the number's minimum size so the two stay proportional at small sizes.

## Visual Design Tokens

**Battery shape** (per instance, recomputed for the widget's current pixel size):
- Body corner radius: `22%` of the shape's shorter side (`min(width, height)`).
- Outline/track color: flat fill, `#C7C9CE`, with a very subtle 60°-angle highlight gradient from `#CFD1D6` to `#BEC0C6` (barely-there sheen, not a hard edge).
- The body has **no border/stroke** — the grey IS the background fill; the colored bar sits flush on top of it with no seam.
- "Bump" (terminal nub): thickness ≈ `14%` of the shape's shorter side; length ≈ `40%` of the body's width (vertical) or height (horizontal); rounded only on the outward-facing corners (rounded like a small pill cap); same grey/gradient as the body.
- Fill gradient: 60°, subtle white highlight (16% opacity) fading to a subtle dark shade (6% opacity) over the flat threshold color — gives a soft sheen, not a hard gloss.
- **Leading-edge bend**: the edge of the fill nearest the bump (the boundary between filled and empty) has a very slight convex bulge outward — implemented as a small pill/ellipse (height ≈ `6%` of the shape's shorter side) centered on that boundary, same color as the fill. Barely visible; reads as a soft edge rather than a hard cut line.
- Whole battery silhouette (bump + body together) gets one soft drop shadow: `0 3px 8px rgba(0,0,0,0.18)` (blurred, low-opacity, works on light or dark wallpaper alike).
- Widget content sizing: 8px of breathing room (padding) on all sides between the widget's outer bounds and the battery shape; the battery fills the rest of the available space, keeping a fixed aspect ratio (~1:2 width:height when vertical, ~2:1 when horizontal), centered.

**Typography**: Roboto, weight 700 (bold) for the percentage number.

**Sizing reference used for prototyping** (Android's typical 4×4 grid, ~96dp cell + 16dp gap — use your target launcher's actual cell metrics):

| Grid span | Orientation |
|---|---|
| 1×1 | vertical |
| 2×1 (wide) | horizontal |
| 1×2 (tall) | vertical |
| 2×2 | vertical |
| 3×1 (wide) | horizontal |
| 2×3 | vertical |

## Recommended Android Implementation
Standard `RemoteViews`-based widgets can't do custom flex layouts, gradients, or dynamic shape drawing — you'll need one of these approaches:

1. **Jetpack Glance** (recommended): build the widget UI in Compose-for-widgets. Draw the battery shape with a custom `Canvas`/`Box` composable using the token values above, so the shape, radii, and gradient can be computed at draw time from the widget's actual allotted size.
2. **Custom `View` + `RemoteViews.RemoteViewsService`**: draw the battery as a custom `View` overriding `onDraw()` with `Canvas` (rounded rects for body, small pill for bump, clipped rounded-rect fill), rendered to a `Bitmap` and set via `RemoteViews.setImageViewBitmap()`. Redraw on every battery-state update and every `onAppWidgetOptionsChanged()` (size change).

**Reading battery state**: register a `BroadcastReceiver` for `Intent.ACTION_BATTERY_CHANGED` (or use `BatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)` for level, and `BatteryManager.EXTRA_STATUS` / `isCharging` for charging state), then call `AppWidgetManager.updateAppWidget()`.

**Responding to size/orientation**: use `AppWidgetManager.getAppWidgetOptions()` in `onAppWidgetOptionsChanged()` to read the current `OPTION_APPWIDGET_MIN_WIDTH`/`MIN_HEIGHT` (in dp), derive the column/row span from your launcher's grid cell size, and pick vertical vs. horizontal layout using the "rows ≥ columns" rule above. For Android 12+ you can alternatively provide multiple exact/responsive layouts via `appwidget-provider`'s `targetCellWidth`/`targetCellHeight` and let the system pick, though you'll still need to compute orientation yourself since that API doesn't expose it directly.

## Assets
None — the entire shape is drawn with rounded rectangles/ellipses and CSS gradients; no bitmap assets. The charging bolt is a simple vector path (`M13 2L4 14h6l-1 8 9-12h-6l1-8z`, white fill) — recreate as an Android vector drawable (`ic_bolt.xml`) if drawing the bolt as an image rather than a canvas path.

## Files
- `Battery Widget.dc.html` — interactive HTML prototype. Open in any browser. Drag the slider to preview charge levels 0–100%; toggle "Charging" to preview the bolt state. All sizing math (radii, bump proportions, font/bolt minimums, shadow, bend) lives inline in this file's script and can be read directly for exact formulas.
- `screenshots/01-states.png` — 62% (green, not charging).
- `screenshots/02-states.png` — 20% (amber threshold).
- `screenshots/03-states.png` — 8% (red threshold).
- `screenshots/04-states.png` — 90%, scrolled to show the 3×1 (horizontal) and 2×3 (vertical) tiles.
- `screenshots/01-default-62pct.png` — 62% with charging bolt shown, across all six sample sizes.

*(Screenshots live in the Claude Design project, not in this repo.)*
