# Samsung lockscreen clock pipeline

Reference builds are Dressroom 3.0.10.38 and AOD 8.9.66.1 on One UI 9.
The AOD call paths and aliases were checked through JADX MCP. The editor
path was checked with a local JADX subset while MCP stayed on AOD. The
version comparisons below use DEX declarations, selected calls and string
references. They are static findings.

## The objects that can look like “the clock”

| Object | Owner and purpose |
| --- | --- |
| Editor clock | Dressroom's `ClockPreviewItem` hosts the provider's view, tracks its measure requests and replaces it when the type changes. |
| Lockscreen and AOD clocks | `FaceWidgetClockPage` chooses a view for its page state. Runtime ownership includes the provider, the selected profile item and the display. |
| Measurement clock | `KeyguardClockProvider.measureClockSize(int)` has its own offscreen instance and cache. Its size feeds the visible clock's scale cap. |
| Clock with seconds | The page asks the provider for type 8 and configures a different view for the dashboard. |
| Standalone AOD clock & theme clock | `AODClockContainer` inflates its own `AbsClockView` (`View.inflate`) when `AODUIPolicy.mNeedClockContainer` is true; `KeyguardClockProvider.getAODThemeClockView` likewise inflates theme clocks directly via `ClockInfoManager`. |
| Nested module clock | `UfClockProvider.m16814b` wraps clock types with a `ModuleClockLayoutId` (`ha6.f12894u0`) inside `ModuleClockView` and recursively calls `m16814b(..., isNested = true)` to inflate a second inner `AbsClockView` (plus `sd0` brief-widget blur container). |
| Offscreen & capability clocks | `KeyguardClockProvider.measureClockSize`, `ClockPositionRepositoryImpl.m5900g` (`[stretch_ui]`), and `ClockStretchableRepositoryImpl.mo12560a` (`[stretch_ui]`) each inflate/cache separate `AbsClockView` instances just to measure bounds or query `isStretchable`. |
| Live-clock needle scraper | `AlwaysOnDisplayEx.m15094o` (`handleLiveClockImageProcessing`) inflates a temporary `AbsClockView` solely to extract analog hand drawables for `AnalogClockNeedleEncoder`. |
| Transition source and destination | `FaceWidgetTransition` matches named subviews. The outgoing view can still be present while its replacement is introduced. |
| Blur silhouette | Samsung combines glyph and weather outlines into a clip path and submits it to a separate blur host. That host can briefly show old geometry even if only one text view is visible. |

When a clock appears twice, first identify whether there are two attached
clock views, two transition owners, or one clock and a stale blur path.
The remedy depends on which object owns the second drawing. The code proves
that all three routes exist. A screenshot alone cannot identify the route
for a particular report.

The [clock layer handoff audit](CLOCK_LAYER_HANDOFFS.md) follows the actual
attach, pre-draw and cleanup paths for these views and blur layers.

## Dressroom preview, measurement and Apply

1. **Initial view and replacement.** `ClockPreviewItem` (`vo.x`) emits
   `ClockInitStarted` and asks its clock-manager wrapper for the selected
   type and category. Clock type 50002 takes a separate informative-module
   path with its own root and lifecycle. Replacement removes the old layout
   listener and unregisters its weather listener before wiring the new view.
   On its animated replacement path the outgoing view fades over 300 ms.
   The incoming view fades over 300 ms after a 160 ms delay. Both can exist
   during the overlap.
2. **Input versus measured geometry.** `mf.d0` maps init to `Init`,
   move/scale start to `Pause`, and move/scale finish or init finish to
   `ObserveSizeChange`. Type changes, cached-position application and
   multi-item stretch changes cause `ForceReset`. The last two states clear
   requested size, position info and scaled bounds so the provider can
   report them again. A separate coroutine coordinator (`mf.c0`) sees
   those states, preview gestures, wallpaper info and preview scroll state,
   then updates idle blur through `mf.h2`. A drag and a settled preview
   deliberately use different renderer inputs.
3. **Scene restore.** `mf.t1.c` chooses the focused wallpaper scene by
   ID and derives movable bounds from the device, screen size, orientation,
   preview scale and whether notifications sit below the clock. It skips
   scene-bundle restore for roaming dual clocks. A new editing session on
   its non-reedit reset path clears the native stretch suggestion and region
   cache. This can run after an early render. Restore module geometry where
   it survives that lifecycle and recalculate measurements when needed.
4. **Serialization.** `yc.b0` converts between the editor model and
   `WallpaperClockBundle`. It carries gravity, horizontal/top/bottom
   margin ratios, offset, translation, scale and stretch support. It only
   emits font heights if the editor's stretch switch is enabled. A
   populated height list alone therefore does not prove the saved native
   bundle contains those heights. `setData(data, true)` calls LiveData
   `setValue` and `false` uses `postValue`. These are publication modes.
5. **Transaction boundary.** `ComponentMediator.beginWallpaperSave`
   first asks every component whether it can save and can return before
   launching work. The lockscreen component's save callback is one part of
   the transaction. `finishWallpaperSaveWhenReady` compares completed
   count with the number of components, then handles the entry-specific
   exit or follow-up work. Keep the mod's draft, native save start and final
   wallpaper completion distinct. Clock edits can render successfully while
   a different wallpaper component still blocks Apply.

Native weather support, user weather visibility, date order and actual date
visibility are separate. `DigitalClockBaseView.setDateVisibility` also
recomputes the row policy. Weather may supply both text and an icon outline.
Measure the surviving row when changing either element. Reusing the old
row's bounds explains why a one-line date or weather change can move the
digits when reopening or saving, even though the stretch values match.

## Runtime selection, geometry and cache boundaries

`UfClockProvider` looks up a `ClockInfo` from the type/category registry,
inflates its layout and applies provider defaults. `FaceWidgetClockPage`
chooses the considered type from the page state and gates including AOD,
theme/image clock, and secure/emergency fallback. Its content refresh has a
snapshot path that can deduplicate requests based on type, fold and LockStar
state, resource identity, font, scale, density, font scale, orientation and
locale. Explicit refresh reasons can bypass that shortcut.

`KeyguardClockProvider` initializes the view with font data, clock-area
wallpaper colors, gravity, maximum width, preview mode and scale. The
provider also handles special inputs such as world cities and the calendar
image. Runtime font data is rebuilt from the selected item's font preset,
path, weight and roundness. Saved stretch heights pass through a
`canApplyStretchFont` gate. Roaming dual-clock selection can substitute
the default weight.

There are separate fallbacks for the visible clock and the stretch
reference. `getLockClockItem` can substitute the lockscreen-only item
when the selected entry is AOD-only. `getStretchReferenceClockItem` can
choose LockStar's lockscreen-only entry. `getStretchReferenceDisplay`
checks that entry first, then the input item, the provider's current item
and finally the data manager's active item. One process can hold multiple
profile and display identities at the same time.

`KeyguardClockPositionManager.createPositionItemFromClockItem` converts
the item with `UiSpecConverter` and stores gravity, margin ratios,
available geometry, scale, font heights and measured size. Without a
converter it uses default position inputs. The manager converts the bottom
margin ratio into pixels relative to the guide height. Its horizontal
padding and maximum width use gravity and the horizontal margin ratio.
Treat screen-space translations as temporary geometry. Saved placement
depends on the reference rectangle, display and gravity.

The provider floors the requested scale at 0.5 before applying its
maximum. It derives that maximum from allowed width and height relative
to the offscreen clock. The measurement cache distinguishes type, font,
city count, weather visibility and temperature length. It can force-refresh
and measure the offscreen view, and the motion-clock branch releases its
GL thread. A change to the visible view alone can therefore leave the
provider's size limit or placement calculation using the old geometry.

## Stretch, font axes and the renderer families

Samsung stores **height ratios**, with `1.0` representing the unextended
shape. The font-specific converter computes:

```text
HEGT = round(minAxis + max(0, heightRatio - 1) * axisSpan / heightRatioSpan)
```

The converter's configuration comes from the selected font family. A slider
reading a raw HEGT value and one reading a saved height ratio need that
conversion. Font types 800–802 use the first stock stretch family and
803–805 the second. The bundled compact variants have no HEGT. The
downloaded/FlipFont branch uses type 700 and a fixed typeface; if it cannot
resolve the file, the horizontal and vertical renderers fall back to 801
and 802 respectively. The mod's imported-font axes belong on that
fixed-typeface path.

| Renderer | What differs |
| --- | --- |
| Horizontal dynamic | The five slots are hour tens, hour ones, colon, minute tens and minute ones. Stretch-eligible layouts get a separate axes string for each. Other layouts apply a shared string to text runs. |
| Vertical dynamic | Two axes strings are shared across the two digit columns. A horizontal five-value profile is the wrong shape here. |
| Calendar | Inherits the horizontal path, but reports a different scalable content target and has an independent image layer. It can defer scale mode until attachment. |
| Analog | Updates its hands/face and applies its own doze/scale and position behavior. It has no five-glyph stretch contract. |
| World | Nested city views receive their own palette, doze and shadow updates. A hook on the outer root can miss the actual city content. |
| Module clock | The outer clock delegates font, doze and blur to an inner `AbsClockView`. It combines frame-module position entries with the inner clock's entries, and delegates GL lifecycle to a motion extension when present. |
| Theme image on AOD | The provider uses the AOD context and configures the `ThemeClockExtension` with text color, image gray level, image and animated GIF content, and content mode. If the theme source is absent it can fall back to the ordinary provider. |
| Good Lock ClockFace | `ClockFaceCustomView` can render local actions in a remote root or use a watch-face surface. It can request the face again on attachment and routes formatted time into individual time actions. The drawing surface need not be a stock digital glyph view. |
| Live wallpaper clock style | `LiveWallpaperClockView` has its own background palette and roaming second time row. This renderer is separate from the wallpaper transform used by the module's depth feature. |
| Motion and informative | Separate creation and lifecycle branches. The motion extension delegates surface availability, resize and GL release. |

Samsung's automatic stretch is a **fit inside a region**. The native scene
measurement uses minimum, maximum and default rectangles for every slot.
It first checks the maximum rectangle across the clock for a full-height
fit. Otherwise it intersects each maximum slot with the region and
computes a height capped to that slot. A simple intersection uses its
bounds, while a complex one uses the first rectangle returned by
`RegionIterator`. If any result cannot exceed the first minimum
rectangle's height, the branch
returns the default rectangles, with different result codes for partial
space versus no space. The resulting slot rectangles feed the font-ratio
calculation. When normalization changes those ratios, the scene runner
adjusts the reported rectangle heights as well. That is the native link
between the stretch shape and the clock's bounding box.

For automatic suggestions, Samsung rejects an axis spread below 150. The
vertical candidate also rejects a column height ratio above 1.6.
Separately, the native font setter calls a normalizer: it reduces the
taller vertical column to 1.6 times the shorter one, and sweeps the
horizontal list forward and backward to cap differences between neighbors
at 200 HEGT units in this build. The suggestion gate and renderer normalization have
different callers. Bypassing the suggestion gate alone will not produce
independent manual digit heights.

`setFontType(ClockFontData)` can return on equal data or before the
category is initialized. It also handles stretch spring state. The
transition path sends intermediate font data to the renderer. Its animator
selects usable weight and height axes, cancels an older run when replaced,
and treats null/empty/all-`1.0` heights as unstretched. The view's
dozing/media state can choose a direct update in place of a spring.
A saved target, an interpolated frame and the last-applied font object
can all differ. If a mod overwrites every font setter with the saved
target, the animation can snap even when the saved values are correct.

## Time, palette and the seconds view

- **Time source.** The time dispatcher rebuilds its calendar for the
  configured zone and passes a formatted payload to the renderer. A
  seconds-enabled instance ignores ordinary `TIME_TICK` and has a
  separate runnable. The doze and detach paths remove that runnable.
  The dispatcher holds the previous timestamp for backward jumps of up
  to ten seconds and accepts larger corrections. A synthetic minute tick
  can therefore bypass important lifecycle and formatting behavior.
- **Time and transient ownership.** Fixed-time preview instances ignore
  live ticks. `onTimeChanged` skips its routine palette reapply while the
  parent's transient-state counter is nonzero. It compares relevant
  formatted-time fields before starting a blur transition, recollects the
  renderable text views and then requests the current blur. Replacing a
  date or weather child can change the outline set without changing the
  time glyphs.
- **Color and doze.** The clock takes a wallpaper-area palette and other
  color inputs, then interpolates solid colors or gradient stops during a
  doze transition. `setDozeAmount(0f/1f)` invokes the endpoint refresh.
  Intermediate frames also update secondary text when the relevant color
  changes. The no-padding renderer can paint a glyph path independently of
  the Android `TextView` color returned to callers. Do not use that
  getter alone to infer the color currently visible on screen.
- **Dashboard/seconds.** `getClockViewForFullScreen` requests type 8,
  sets a separate font payload, chooses lock versus AOD HMS positioning,
  supplies date order and weather, and conditionally forces a two-digit
  hour. Its device scaling can be 1, 1.18 or 1.25. The page configures
  named hour, seconds and colon transitions. The lock/AOD weight helper
  resolves named font views on the source/destination and can animate
  font weight independently of the other page geometry. The return path
  should use the tracked destination rather than the last clock found by
  type or view class.
- **Expanded media.** The page keeps transition-specific font data and
  media expansion can suppress the stretch spring. Palette, glyph geometry
  and depth belong to the current transition owner. Replaying the saved
  pose into an outgoing clock can expose a compact silhouette or make the
  restored clock jump. The 8.5 fade and 9.0 artwork transition have their
  own media paths, as documented in [8.5 media](MEDIA_8.5.md) and
  [media return](MEDIA_RETURN_INVESTIGATION.md).

## Blur is a separate rendering pipeline

1. **Eligibility.** The provider rejects theme clocks, high contrast,
   reduced transparency, minimal battery use and unsupported clock types.
   The active item's blur flag is a separate input. Android's window-blur
   switch is also separate from the Samsung clock's capability.
2. **State.** The reference state machine distinguishes persistent blockers
   (`BLUR_EFFECT_DISABLED`, `SGG_ACTIVE`) from temporary ones. Temporary
   inputs include stretch, edit, alpha and screen animations, media
   expansion, landscape media, doze, dashboard, quick panel, hidden
   container, bouncer, profile changes, FaceWidget transitions, overlay
   and shortcut swipes, non-default page, pinch zoom, activity launch and
   unlock alpha. Multiple reasons can be active together. A continuity
   change can target one verified transition without clearing the others.
3. **Geometry.** `ClockTextView` and `ClockNonePaddingTextView` supply
   paths. The latter can depend on a cache populated by drawing.
   `CombinedBlurStrategy` translates paths into the host coordinate
   system and appends the weather icon path. It chooses the lockscreen,
   editor or fallback host by tag (`lock_clock_blur`,
   `lock_clock_blur_on_lockeditor`, `Clock_Blur_Container`). The
   strategy selects a native `SemBlurInfo` mode and color curve from
   the active context, brightness and saturation. A single font setter
   does not control that drawable.
4. **Submit and retry.** The manager can skip a normal update during its
   reveal animation. If the strategy fails because current paths are
   unavailable, it retries after 8 ms, with a limit of five retries. A
   successful submit updates the glyph/weather alpha. An older pending
   outline can outlive the state in which it was queued. The logical
   owner of the retry is the specific clock/host pair and its render
   generation. Reusing a valid host through a transition needs fresh
   geometry from that pair.
5. **Depth.** Samsung's stretch subject avoidance yields candidate clock
   geometry. The module's subject depth instead clips the final clock,
   widget or blur layer in the coordinate space where it draws. The
   foreground cut, native glyph path, and live wallpaper transform must
   agree for the current owner. Do not bind a wallpaper cut to a
   measurement or outgoing dashboard view. On wallpaper-less AOD, the
   foreground cut has no visible subject to represent.

The reference strategy submits radius 200 before any framework
processing. Additional verified limits and state-specific blur/doze rules
in AOD `8.9.66.1`:

- **Clock-type whitelist (`ClockInfoManager.isSupportTextBlurClock`):** returns
  `true` only for clock types `1, 2, 7, 9, 10, 11`; all other clock types are
  blocked before rendering.
- **21 blockers + asymmetric `BlurBlockDebouncer` delays:** entering any block
  flag (`DOZE_ACTIVE`, `DASHBOARD_ACTIVE`, `MEDIA_EXPAND_ANIMATING`, etc.) kills
  blur immediately, while un-blocking waits `500 ms` (`MediaExpand`), `300 ms`
  (`ShortcutSwiping`, `RequestStartActivity`, `EditMode`, `PrimaryBouncer`,
  `OverlaySwiping`), `100 ms` (`ContainerVisible`), `50 ms` (`AlphaChange`,
  `StretchAnimation`, `ProfileTransition`), or `10 ms` (`Doze`).
- **`mIsDressRoom` (`AbsClockView.f43041L0`) mode split:**
  `CombinedBlurStrategy.applyBlur` uses `SemBlurInfo.Builder((!mIsDressRoom || z2) ? 0 : 2)`
  and targets `"lock_clock_blur"` (lockscreen), `"lock_clock_blur_on_lockeditor"`
  (lock editor), or `"Clock_Blur_Container"`. `ClockBlurManager.resolveBlurColorMetrics`
  samples `absClockView.f43099t` when `mIsDressRoom` is true, vs.
  `SemWallpaperColors` (`n16` from `f43097s`) on live Lockscreen. A separate
  `SubLauncherClockBlurController` (`[blur_sub]`) mirrors this pipeline for the
  cover screen.
- **CIE L\* brightness bands (`BlurThresholds(dark=30, mid=64, light=76)` &
  `rv2.calculateLuminanceLStar`):** `ClockBlurColorCurvePolicy.selectColorCurve`
  steps curve weights across `d < 30`, `d <= 64`, `64 < d <= 76` (further
  subdivided by saturation `f`), and clamps bright backgrounds (`d > 76`) to
  `(0.7f, -8.0f, 5.0f, 255.0f, 0.0f, 209.1f)`, with a negative `-8.0f` level and `209.1f`
  ceiling instead of `255.0f`.
- **Foreground text alpha compression:** `collectBlurTextViews` compresses text
  alpha into `[0.1 .. 0.8]` (`(alpha * 0.7f) + 0.1f`); `AbsClockView.updateBlurTextAlpha()`
  forces `0.35f` when lightness `> 76.0` (else `0.5f`) for non-custom clocks; and
  `applyTextBlurWithRetry` forces alpha `127` during its `5 x 8 ms` retry loop.
- **Doze & wallpaperless AOD (`aod_show_lockscreen_wallpaper`):**
  `SettingsHelper.isAODShowLockscreenWallpaper()` requires both the setting and
  `cj1.isFullScreenAODSupported()`. When entering Doze (`FaceWidgetClockPage.setDarkAmount(f)`),
  any `f > 0` activates `DOZE_ACTIVE`; `AbsClockView.setDozing(true)` hard-sets
  `mIsBlurActive = mIsBlurEffectEnabled && !z` (`false`) and forces
  `isWhiteWallpaper()` (`mo16932W`) to `false`. When wallpaperless AOD runs
  (`!isAODShowLockscreenWallpaper()` or theme clock `mIsAODThemeClock`),
  `FaceWidgetViewModel.setClipLayoutVisibility` takes the `" aod"` branch, and
  `DirectBoot` lock (`ly0.isDirectBootLocked`) forces `ClockInfo.EMPTY`.
  Separately, `AODSettingDataProvider` overrides the clock item from LockStar DB
  whenever `AODDataManager.f39498F` is true.
- **Complications (`BlurEffectManager`):** buckets contrast into four
  `WidgetBlurType` modes (`CLOCK_DARK_WALLPAPER_BRIGHT/DARK`,
  `CLOCK_WHITE_WALLPAPER_BRIGHT/DARK` using `L* > 76.0` and brightness `70`/`18`),
  clears blur via `semSetBlurInfo(null)` whenever `musicExpanded` is true, and
  records `isAODShowLockscreenWallpaper()` + `now_bar_aod_enabled` in `WidgetBlurInfo`.

The path-mode renderer aligns glyphs by their measured ink bounds,
supports a 90° drawing path, and can temporarily use the Arabic typeface
for drawing and outline extraction. It draws its own fill-and-stroke
glyph path and records the baseline/padding offsets. Any reuse of a
glyph outline across frames needs to account for the real glyph paint,
layout offset, script, orientation and axes. See
[clock rendering](CLOCK_RENDERING.md) for the downstream framework paths.

## Version comparison

The reference path was decompiled in detail. Other APKs in this table were
scanned for the stated contracts. Version numbers identify the actual APKs,
independently of their filenames.

| APK | Observed contract |
| --- | --- |
| AOD 8.9.66.1, SDK 37 | `ClockFontData` font setter, HEGT spring and axis builder, type-8 seconds view, doze palette setter, provider maximum-scale path. |
| AOD 8.9.36.23, SDK 36 | Same font setter, HEGT spring and seconds-view entry. The spring owner and axis builder have different DEX identifiers. |
| AOD 8.9.28.18, SDK 36 | Same font setter, HEGT spring and seconds-view entry, with another set of DEX identifiers. |
| Clock Style 8.9.35.60, SDK 36 | Same font setter, HEGT spring and seconds-view entry. Its provider/package is Clock Style and geometry still needs checking per device form factor. |
| Clock Style 8.9.65.92, SDK 37 | Same font setter, HEGT spring and seconds-view entry. The shared signature does not establish that its date/weather or widget measurement branches match AOD. |
| AOD 8.8.40.20, SDK 35 | Doze palette setter, maximum-scale entry and seconds-view creation are present. The seconds view calls `setFontType(int, String, String)`. The scan found no exact HEGT anchor. |
| Dressroom 3.0.10.38 and 2.9.60.50 | Both have `ClockInitStarted` and a `save: onSaveCompleted` anchor, but their containing callbacks have different shapes. One is a synthetic dispatch body and the other a coroutine body. |
| Dressroom 2.8.10.24, SDK 35 | Neither exact lifecycle anchor matched. The older editor's equivalents remain unmapped. |

The five 8.9.x providers all connect
`AbsClockView.startSpringAnimation(ClockFontData)` to a method taking
`(ClockFontData, List)`. They also construct the seconds clock through
`KeyguardClockProvider.getClockView(int, boolean)` and apply the
`ClockFontData` overload. These are better signature anchors than the
names of the spring owner or its synthetic listeners. Confirm a candidate
with the typed calls and both its entry and downstream side effects.
For older versions, the measured-size, color and seconds contracts can be
ported independently of HEGT.

| Provider version | Target of the typed spring call in DEX | Separate axis-string candidate with an exact HEGT literal |
| --- | --- | --- |
| AOD 8.9.66.1 | `aod.nr0.e` | `aod.qn1.D` |
| AOD 8.9.36.23 | `aod.As.b` | `aod.VO.A` |
| AOD 8.9.28.18 | `aod.ys.b` | `aod.vU.L` |
| Clock Style 8.9.35.60 | `aod.Tn.b` | `aod.QT.D` |
| Clock Style 8.9.65.92 | `aod.lf0.e` | `aod.uq6.H` |

These are research coordinates for the sampled APKs. The axis builder was
read on the reference APK. The other candidate bodies still need verifying
before using them for a binding.

| Porting surface | Signature evidence to require |
| --- | --- |
| Editor preview | A provider creation call with clock type/category, publication of the active view, and matching attach/release behavior. |
| Editor geometry and save | The measure-state reset path, scene/bundle conversion and final component-count completion. The `save: onSaveCompleted` anchor in this comparison is for the lockscreen component callback. |
| Runtime font and stretch | The typed font setter, height-list data flow into the renderer, and the HEGT string in the animator/axis builder. Check the compact fallback and horizontal/vertical slot counts. |
| Runtime geometry | Find offscreen measure, provider scale cap and selected item/display. Compare weather, widget and orientation inputs before reusing a position correction. |
| Palette and blur | Identify the renderer's actual color sink and the native strategy's glyph/weather path, host lookup and `semSetBlurInfo` submit. Verify the blocker and retry ownership in that build. |
| Transitions and depth | Follow the outgoing and incoming views separately. Verify transition names, the completion callback, and the active wallpaper transform before applying live geometry. |

The remaining gaps are the older Dressroom save/measurement equivalents,
provider-specific blur state and retry bodies across the comparison builds,
and the contents of the delegated motion, remote ClockFace and informative
surfaces. The static evidence
does not identify a specific on-device duplicate or establish device
support. A future visual check can isolate the first affected owner with a
minute change, a seconds-view return, a media return and a cancelled unlock
swipe. No module code or APK changed in this pass.

[One UI 9 AOD aliases](AOD_9_ALIASES.md) ·
[One UI 9 Dressroom aliases](DRESSROOM_9_ALIASES.md) ·
[Clock pipeline audit](CLOCK_PIPELINE_AUDIT.md) ·
[APK feature audit](../compatibility/APK_FEATURE_AUDIT.md)
