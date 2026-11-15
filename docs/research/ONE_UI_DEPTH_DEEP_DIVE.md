# One UI 9 Depth & Wallpaper Region Deep Dive vs. Dressroom Advanced

**Reference APK:** `com.samsung.android.app.aodservice` `8.9.66.1` (version code `896601000`)  
**Scope:** Samsung One UI 9's native wallpaper subject/background region, erase-background, expand-offset, and AOD SelfMove pipelines compared against Dressroom Advanced's live subject depth pipeline, `SharedMemory` motion IPC, clock/widget/media occlusion hooks, and concrete improvement opportunities.  
**Aliases:** Newly renamed `aod.*` symbols referenced here are catalogued in [AOD_9_ALIASES.md](AOD_9_ALIASES.md#wallpaper-region-stretch-geometry-and-depth-adjacent-pass-2026-11-12).  
**Related depth docs:** [DEPTH_RESEARCH.md](DEPTH_RESEARCH.md), [DEPTH_UPGRADE_RESEARCH.md](DEPTH_UPGRADE_RESEARCH.md), [ONE_UI_CLOCK_DEEP_DIVE.md](ONE_UI_CLOCK_DEEP_DIVE.md), [ONE_UI_WIDGET_DEEP_DIVE.md](ONE_UI_WIDGET_DEEP_DIVE.md).

---

## 1. Native One UI 9 Wallpaper Region & Depth-Adjacent Systems

Samsung already computes wallpaper subject segmentation and background geometry inside One UI 9's framework/wallpaper stack, and exposes them to `aodservice` / Keyguard, **but never uses them for Z-axis clock/widget occlusion**. Instead, Samsung consumes them only for 2D `stretch_ui` clock layout avoidance, pan/crop direction offsets, and binary AOD subject cutout.

### 1.1 Background Region IPC (`semGetWallpaperBackgroundRegion`)

- **`aod.WallpaperManagerReflector`** (`aod.d26`): Reflects `android.app.WallpaperManager` with mode/display constants `SWP_TYPE_CUSTOM = 0`, `SWP_TYPE_PRELOAD = 1`, `SWP_TYPE_THEME = 2`, `FLAG_DISPLAY_PHONE = 4`, `FLAG_DISPLAY_SUB = 16`, and `FLAG_MODE_MASK = 60`. Method `m3126C` invokes `WallpaperManager.semGetWallpaperBackgroundRegion(int which, int userId, String sceneId, int rotation)`, returning a `Bundle`.
- **`aod.GetWallpaperSceneIdUseCase`** (`aod.vu2`): Invokes `WallpaperManager.semGetWallpaperSceneId(int, int)` to resolve scene-specific wallpaper IDs before querying regions.
- **`aod.w06.m12637B`** & **`aod.tc6.m11379j`**: Unpack `Region` from `bundle.getParcelable("background_region")`.
- **`aod.WallpaperRegionRepositoryImpl`** (`aod.g59`, log tag `"WallpaperRegionRepositoryImpl_" + category.name() + "[stretch_ui]"`): Caches/provides wallpaper regions by display/clock category.
- **`aod.WallpaperBgRegionProvider`** (`aod.k49`, log tag `"WallpaperBgRegionProvider[clock_position]"`, implements `aod.a02` `DumpManager`): Validates region bounds against display geometry (logging `REGION_SIZE_MISMATCH[...]` in `m6814d`) before downstream clock placement consumes it.

### 1.2 `stretch_ui` Clock Avoidance Instead of Z-Depth

Instead of rendering the clock *behind* a foreground subject mask, Samsung shrinks or stretches the clock inside the non-subject background hole:

- **`aod.GetClockMovableAreaRegionUseCase`** (`aod.bt2`): Builds the exclusive UI region (movable area, scaled widget container height when `kp0.mo7069e() && kp0.mo7070f()`, and inflated shortcut bounds) and subtracts it from the background region via `Region.Op.DIFFERENCE`.
- **`aod.RunStretchMeasureProcessUseCase`** (`aod.fa6`) & **`aod.StretchMeasureProcessRunner`** (`aod.o97`): Call `vi1.m12336E(region, rect)` (`getFirstIntersectionHeight`) to measure free vertical bands and classify the clock into `FULL_STRETCHED`, `PARTIALLY_STRETCHED`, `PARTIALLY_NO_SPACE_PARTIALLY_STRETCHED`, or `DEFAULT_NO_SPACE` (`s97`).
- **`aod.KeyguardClockDebugManager`** (`aod.jr3`) & **`aod.StretchDebugDrawable`** (`aod.yi1`): Behind debug flags `bj1.f3755u` / `bj1.f3756v`, attach `StretchDebugDrawable` onto `SecKeyguardStatusView.setBackground(...)`, drawing `background_region` as a green stroke outline (`Color.argb(100, 100, 230, 0)`), UI exclusion/movable areas in orange (`Color.argb(100, 255, 100, 0)`), and clock bounding rects. (Alongside this, `aod.w06.m12636A` / `m12653R` attaches `aod.aj1` `"ClockBlurDebugHelper"` for `CombinedBlurStrategy` `aod.tx0`.)

### 1.3 Wallpaper Properties, Live Thumbnails, Expand Offsets & Erase-Background

- **`aod.SemWallpaperPropertiesReflector`** (`aod.o16`): Reflects `com.samsung.android.wallpaper.utils.SemWallpaperProperties` (`KEY_CONTENT_TYPE`, `KEY_IMAGE_CATEGORY`, `KEY_IMAGE_FILTER_PARAMS`).
- **`aod.k54`** (`" LiveWallpaperProviderRepositoryImpl"`): Reflects `WallpaperManager.getWallpaperExtras(which, userId)` (`"serviceSettings"`, `"bg_color"`) and queries `content://com.samsung.android.wallpaper.live.provider.sepwallpaper` method `"get_thumbnail"` returning `"thumbnail_file_descriptor"`.
- **`aod.ui1` case 14** (`"GetWallpaperExpandInfoUseCase_<cat>[stretch_ui]"`), **`aod.t68`** (`SubLauncherWallpaperClockDataController`), and **`aod.vr3`**: Consume `"expand_offset_x"` (`< 0` Right, `> 0` Left) and `"expand_offset_y"` (`> 0` Top) to detect wallpaper pan/alignment hints.
- **`SettingsHelper` & `aod.C0336g7` toggles**:
  - `"aod_show_lockscreen_wallpaper"` (default `1`)
  - `"aod_show_cover_screen_wallpaper"` (default `1`)
  - `"aod_erase_background"` (default `0`; `prefsEraseBackground` disabled when `aod.l62.m7283b(context)` is true for Power Saving Mode)
  - `"lockscreen_wallpaper_transparent"`
- **AOD Burn-In SelfMove**: `com.samsung.android.uniform.manager.ViewPositionControllerManager`, `aod.va2` (`FaceWidgetPositionControllerImpl`), and `aod.x57` (`StarSelfMoveCommand`) shift the clock, NIO, battery, and bottom containers on SelfMove ticks within `mRange` (`q29`). Any SystemUI depth mask must therefore track live global view transforms continuously rather than caching screen coordinates once.

### 1.4 Why Samsung Has the Pieces But No Lockscreen Depth Clock

1. **Binary `Region` only**: `semGetWallpaperBackgroundRegion` returns a coarse integer `android.graphics.Region` (good enough for bounding-box fitting in `stretch_ui`, too jagged/unsmoothed for hairline edges overlapping high-contrast clock numerals).
2. **AOD `aod_erase_background` lives on the wallpaper side**: Erasing the non-subject background in AOD removes pixels behind the whole UI instead of compositing a separate foreground Z-layer in front of views while leaving blur, widgets, and background visible behind the clock.
3. **Multi-target occlusion complexity**: Samsung ships `SemBlurInfo` clock/widget blur, AOD SelfMove, lockscreen widgets, and media cards across separate view hierarchies; cutting a single clean hole through all of them consistently requires coordinated view-layer masking and contour subtraction.

---

## 2. Current Dressroom Advanced Depth Architecture

Unlike Samsung's 2D layout avoidance, Dressroom Advanced extracts true soft-alpha foreground subject masks and composites Z-occlusion across SystemUI clock ink, `SemBlurInfo` clock/widget blur, third-party widget `Outline` clips, and media transitions (`dev.denver.dressroomadvanced.*`).

### 2.1 Subject Capture, Alignment & Mask Generation

- **`depth/DepthSourcePolicy.java`**:
  - `visible(boolean interactive, boolean aodWallpaper)` returns `interactive || aodWallpaper`, unifying lockscreen visibility with AOD wallpaper visibility.
  - `still(int type)` restricts static extraction to still wallpaper types (`type <= 0 || type == 19 || type == 23 || type == 401 || type == 402 || type == 408`).
- **`depth/LiveSubject.java`** (lines 120–366):
  - `capture`: Downs/caps wallpaper capture at `4096` max dimension and `Math.sqrt(4_500_000d / (w * h))`.
  - `alignedOriginal`: Unwraps a `LayerDrawable` with `>= 2` layers if aspect-ratio difference is `<= .01`, avoiding baked-in filter/dimming overlays when possible.
  - `fingerprint`: Hashes a 64×64 sample via `DepthStorage.hash`.
  - `bake`: Caps editor-baked mask width at `Math.min(1440, view.getWidth())`.
- **`depth/ObjectCaptureBackend.java`**, **`depth/DepthAuto.java`**, **`depth/MaskAlpha.java`**, **`depth/DepthStorage.java`**, **`depth/DepthMetadata.java`**, **`depth/DepthSafety.java`**, **`compat/shared/DepthBinding.java`**, **`compat/shared/WallpaperSourceBinding.java`**, **`ScreenIdentity.java`**: Handle Samsung `ObjectCapture` segmentation, alpha matte refinement, fingerprint caching, and SystemUI binding.

### 2.2 Real-Time Wallpaper Motion & AOD Transition IPC

- **`depth/WallpaperMotionPublisher.java`** & **`compat/shared/WallpaperMotionBinding.java`**:
  - Hooks `com.samsung.android.wallpaper.live` (`drawRenderNodes: durDraw=`, `draw: drawable is null`, `SwitchableWallpaperService$DelegatorEngine.onCommand`) to stream live wallpaper scale and translation transforms during screen-on, lock/unlock, and AOD transitions.
- **`compat/shared/WallpaperMotionPacket.java`** (lines 9–55):
  - Lock-free `SharedMemory` region `"Dressroom-wallpaper-motion"` with `SLOTS = 16` and `STRIDE = 64`.
  - `valid()` enforces `(which & 3) == 2`, `scaleX`/`scaleY` in `[0.5, 2]`, and `|offsetX|`, `|offsetY| <= 2`.
  - `fits(id, w, h)` checks `wallpaper == id` and `Math.abs((double) width * h / ((double) height * w) - 1) < .01`.
- **`depth/WallpaperMotion.java`**:
  - Observes `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` / `WALLPAPER_CHANGED` plus `aod_show_lockscreen_wallpaper` and `aod_show_cover_screen_wallpaper`.
  - Runs a `250ms` `Choreographer` pump on `wake()` so depth transforms stay locked to wallpaper scale/offset animations.

### 2.3 Multi-Surface Occlusion in SystemUI

- **`depth/DepthEffects.java`**:
  - Uses `erase` `Paint` with `PorterDuff.Mode.DST_OUT` + `Paint.FILTER_BITMAP_FLAG` to punch out foreground subject pixels after views draw.
  - Hooks `dispatchDraw`, `View.draw`, and `TextView.onDraw` wrapped in `canvas.saveLayer(null, null)` + `drawMask` (`inverse * source`).
  - `subjectTransform` (lines 367–410) reads `WindowMetrics.getBounds()`, center-crops `entry.mask`, applies `ScaleToFit.FILL`, and chains `live.global()` or `WallpaperMotion.transform`.
  - Tracks view/AOD burn-in movement via `transformMatrixToGlobal` + `depth/DepthViewMotion.java`.
  - On `wallpaperMotionChanged` (lines 424–453), clears `localPaths` and `entry.paths`, swaps `entry.identity = new Object()`, invalidates the clock/widget trees, and calls `WidgetDepthBlur.mediaChanged` + `refreshNativeBlur` -> `setClockTextBlur()`.
- **Contour & Geometry Engine (`depth/MaskContour.java` & `depth/DepthGeometry.java`)**:
  - `DepthEffects.contour` (lines 1204–1225) extracts vector outlines via `MaskContour.extract(mask, 127.5f)` + `DepthGeometry.simplify(..., .2f)`, with a per-row `Region.op` fallback.
  - `DepthGeometry.subtract` (lines 30–64) early-outs when `!RectF.intersects(contentBounds, subjectBounds)`, looks up a 24-entry `cuts` LRU cache matching `(subject ref, fill, approximate(.1f))`, skips `Path.Op.DIFFERENCE` when `p.approximate(.5f).length > 768` (falling back to `subtractRegion`), and simplifies output with `simplify(visible, .3f)`.
- **Four Coordinated Occlusion Targets**:
  1. **Clock ink (`clock/RuntimeClockHooks.java`, `clock/ClockContentClip.java`)**: Raster `DST_OUT` masking of clock drawables/text.
  2. **Clock native blur (`depth/DepthBlurHooks.java`)**: `SemBlurInfo` blur geometry subtraction synchronized with `setClockTextBlur()`.
  3. **Widgets & widget blur (`widget/RuntimeWidgetHooks.java`, `widget/WidgetDepthClip.java`, `widget/WidgetContentClip.java`, `widget/WidgetDepthBlur.java`)**: Both `Outline` clipping for widget hosts and `SemBlurInfo` blur subtraction (`WidgetDepthBlur`).
  4. **Media card transitions (`media/MediaDepthTransition.java`)**: Keeps media surfaces consistent across depth transitions.

---

## 3. Architectural Bottlenecks & Ranked Improvement Opportunities

One UI 9 now has an applied-wallpaper fallback when Object Capture runs and
returns no subject. The module checks that Samsung's screenshot matches the
editor crop and that the wallpaper ID and scene stay the same across the reads.
It inverts the native background region and maps the result back to source
coordinates. Unmatched crops, nonzero expand offsets and subjects clipped by
the crop skip this fallback. The phone returned both portrait and landscape
regions, but this fallback still needs an end-to-end test in Dressroom.

The draw hooks now check the projected alpha bounds against the incoming canvas
clip before allocating a layer. The bounds include nonzero alpha and a margin
for bitmap filtering. Motion now invalidates only the affected screen and can
reuse screen paths when the transform repeats. Clock and widget blur still
refresh from the same transform as foreground content. Device timing remains
unverified.

Comparing One UI 9's native architecture against Dressroom Advanced reveals six verified bottlenecks and high-leverage improvements:

| Priority | Area / File | Verified Behavior & Bottleneck | Improvement Opportunity |
| --- | --- | --- | --- |
| **1** | `DepthEffects.java` (`dispatchDraw` / `View.draw` / `TextView.onDraw`) | Allocates `canvas.saveLayer(null, null)` unconditionally before computing whether the subject actually intersects the target view's screen-space bounds. | Add a cheap mapped `subjectBounds` vs view bounds intersection pre-check (mirroring `DepthGeometry.subtract`'s `!RectF.intersects(contentBounds, subjectBounds)` early-out) and skip `saveLayer` entirely when non-overlapping. |
| **2** | `DepthEffects.java` (`wallpaperMotionChanged`, lines 424–453) & `WallpaperMotion.java` | During `wake()`, the `250ms` `Choreographer` pump triggers `wallpaperMotionChanged`, which clears cached `localPaths`/`entry.paths`, swaps `entry.identity = new Object()`, and calls `refreshNativeBlur` -> `setClockTextBlur()` on every frame, spamming path rebuilds and `SemBlurInfo` IPC during wake/unlock animation. | Decouple fast matrix translation/scale invalidation for raster `DST_OUT` from quantized/debounced blur-region IPC updates, and avoid discarding untransformed `entry.paths` when only translation/uniform scale changed. |
| **3** | `DepthGeometry.java` (`subtract`, lines 30–64) | When `p.approximate(.5f).length > 768`, `DepthGeometry.subtract` abandons exact `Path.Op.DIFFERENCE` and falls back to `subtractRegion` (staircased integer `Region` ops), creating visibly blocky cutouts on intricate foreground silhouettes (`SemBlurInfo` / `WidgetContentClip`). | Raise or adaptively handle contour simplification / tiling for complex paths (or cache quantized high-detail boolean cuts in `cuts`) instead of abrupt downgrade to staircase `Region` geometry. |
| **4** | `InstalledWallpaperMask.java` & Samsung WallpaperManager | Samsung returns wallpaper background regions by scene and rotation. The editor can use an inverse region when its current crop matches the applied wallpaper. | Verify the fallback on more One UI 9 devices and check which 8.5 frameworks expose the same region and screenshot APIs. |
| **5** | `MaskContour.java` (`127.5f`) vs `DepthEffects.java` (`DST_OUT`) | Clock ink receives smooth soft-alpha masking via `MaskAlpha` + `PorterDuff.Mode.DST_OUT`, whereas `DepthEffects.contour` slices blur/`Outline` geometry at a single hard `127.5f` alpha threshold. Around hair/fur/semi-transparent rims, hard blur behind soft-masked text can still show slight edge halos. | Evaluate a slightly recessed or anti-aliased/eroded blur contour offset relative to the ink alpha edge so native `SemBlurInfo` does not peek past semi-transparent foreground rims. |
| **6** | `aod.C0336g7` (`aod_erase_background`) vs `DepthSourcePolicy.java` (`visible`) | On One UI 9, `aod_erase_background` is an independent `SettingsHelper` toggle (default `0`, suppressed by `aod.l62.m7283b` in Power Saving Mode) alongside `aod_show_lockscreen_wallpaper` (default `1`). | Verify SystemUI interaction when users enable both native `aod_erase_background = 1` and Dressroom depth in AOD, to ensure double-masking or wallpaper-layer absence never clips/blanks clock blur or SelfMove views unexpectedly. |
