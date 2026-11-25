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
- Dividers are `Hairline` (`AppDivider`); chip and button borders are `Outline`. List cards are
  `AppCard`, never a hand-built `clip + background(Raised1)` container.
- Text colours in screens come from the colour-scheme roles: `MaterialTheme.colorScheme.onSurface`
  for primary text and `onSurfaceVariant` for secondary text (Theme.kt maps the tokens onto them).
  `TextTertiary` stays a token because it has no M3 role. `ui/theme`, `ui/components` and `map/`
  may use the tokens directly.
- Tab roots (Record, Activities, Progress, Settings) start with `ScreenTitle`; pushed screens use
  `AppTopBar` with a back arrow.

## 2. The number is the loudest thing on screen

- Values use the `AppText.number*` styles (tabular figures): `hero` for the Record distance,
  `numberXL` for detail headline stats, `numberL` in stat tiles, `numberM` in dense grids and splits.
- Labels sit **above** the value in `AppText.eyebrow`, uppercase. Units trail the value in
  `bodyS` / `onSurfaceVariant`. Use `StatTile` / `StatRow` rather than rebuilding this.
- All numbers and dates go through `core/util/Format.kt`. Never format a pace or a distance inline.
- Distance decimals: a single run shows 2 decimals ("5.02 km"); a period total (a week, a month,
  "This week") shows 1 decimal ("23.4 km"); the all-time total shows whole kilometres.
- A run listed anywhere uses the Activities row model (`RunCardUi` from `Run.toCard`): moving time as
  `formatDuration`, relative day ("Today", "Mon", "12 Nov").
- Personal bests are an outlined `PbBadge` (Run detail header and efforts); the PB screen shows the
  record value in `Accent`.

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

## 9. Motion

One vocabulary, from gym-tracker; the tokens are in `ui/theme/Motion.kt`. Never hard-code a duration,
easing or spring in a screen.

| Event | Spec |
|---|---|
| generic state (show/hide, colour, size, crossfade) | `Motion.STATE_MS` 200 ms, `FastOutSlowIn` (`Motion.state()`) |
| numbers changed by a choice, meters, chart values | 450 ms, the value interpolates (`Motion.value()`); tabular figures keep digits still |
| selection / row tint | 150 ms (`Motion.tint()`): chips, selected split row |
| something joining a layout (row, warning, banner, section) | `Motion.appear` / `Motion.disappear` (fade + expand/shrink from the top) |
| badge, pill, dot | `Motion.badgeIn` (fade + scale 0.9 -> 1) / `Motion.badgeOut` |
| one value replacing another in place | `AnimatedContent` + `Motion.crossfade()` |
| one screen-sized state replacing another (loading -> content, record phases, onboarding steps) | `AnimatedContent` + `Motion.fadeThrough()` (90 ms out, then 210 ms in) |
| tab switch | fade-through; peers never slide |
| push / pop | 300 ms slide, the screen underneath moves 1/3 (parallax); predictive back uses the same pop |
| lazy list rows | `listItemMotion()` (fade in/out + placement, 200 ms); give the items stable keys |
| press feedback | ripple, plus `rememberAppHaptics()`: `light()` for small choices, `confirm()` for start/finish |
| dialogs, menus | Material default (standard motion scheme) |
| banned | spring overshoot, the expressive motion scheme, shape-morph, shared elements, slides between peers |

- Key `AnimatedContent` by the thing that changed meaning (a phase, a hint stage), not by text that
  ticks: live run stats and countdowns never animate.
- Animate colour with `animateColorAsState`, sizes with `animateContentSize(Motion.state())`, values
  with `animateFloatAsState(…, Motion.value())`.
- Reduced motion is handled by Compose (system animator duration scale); do not add a check.

