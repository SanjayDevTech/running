# UI house rules

Short and binding. Every screen follows these; review a screen against them before calling it done.
The tokens live in `ui/theme/` and the shared pieces in `ui/components/`.

## 1. Always dark, OLED

- No light scheme, no dynamic colour. System bar icons are pinned light.
- **Pure black** (`Black`) where content sits directly on the canvas: screen backgrounds, the Record
  screen, top bars, map letterboxing.
- **Raised near-black** where pure black would make a shape vanish: `Raised1` list cards, `Raised2`
  bottom bar and chips, `Raised3` sheets/dialogs/menus, `Raised4` pressed or selected fills. Charts
  sit on black; their plot area is not boxed.
- Dividers are `Hairline`; chip and button borders are `Outline`.

## 2. The number is the loudest thing on screen

- Values use the `AppText.number*` styles (tabular figures): `hero` for the Record distance,
  `numberXL` for detail headline stats, `numberL` in stat tiles, `numberM` in dense grids and splits.
- Labels sit **above** the value in `AppText.eyebrow`, uppercase. Units trail the value in
  `bodyS` / `onSurfaceVariant`. Use `StatTile` / `StatRow` rather than rebuilding this.
- All numbers and dates go through `core/util/Format.kt`. Never format a pace or a distance inline.

## 3. Accent means action or achievement

`Accent` (orange) is for the record button, the primary button, the selected tab, links, personal
bests and the current-period bar in a chart. It is never decoration: no accent-filled chips, no
tinted rows, no accent headings. Selected chips are a `Raised4` fill with primary text.

Semantic colours: `Walk` for walking segments, `Stop` for stops, `Warning` for weak GPS, `Danger`
for stop/finish, delete and errors, `Success` for a good fix and route start, `PaceScale` (fast →
slow) for pace-coloured routes, `StepFilled` (dashed) for step-filled route parts.

## 4. Spacing and geometry

- Gaps come from `Spacing` only (`4 · 8 · 12 · 16 · 20 · 24 · 32`). Raw `.dp` is for sizes (icon
  diameters, chart heights), never gaps. The side gutter is `Spacing.gutter` (16 dp).
- Corners from `Radius`. Zero elevation everywhere, including pressed and focused states.

## 5. A control's size token is a minimum

`heightIn(min = Sizes.button)`, never `height(Sizes.button)`. Every screen must stay usable at font
scale 2.0: rows wrap or grow, numbers may drop a size, nothing clips.

## 6. No emoji

Anywhere. Icons come from `Icons.Rounded.*` / `Icons.Outlined.*` (material-icons-extended).

## 7. Insets

The nav host's `Scaffold` applies only the bottom-bar padding. Screens draw edge to edge and handle
the status bar themselves (`statusBarsPadding()` or `AppTopBar`, which already does). The Record
screen during a run is full-screen: the bottom bar is hidden.

## 8. Do not copy food-tracker's look

Its conventions (code style, build, CI) are shared; its visual design (mint accent, hairline-boxed
sections) is not.
