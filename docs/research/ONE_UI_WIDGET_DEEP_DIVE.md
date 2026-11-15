# Samsung One UI 9 AOD & Lockscreen Widget / Complication Pipeline

Reference build: `com.samsung.android.app.aodservice` `8.9.66.1` (version code `896601000`), inspected and partially renamed through JADX MCP on `2026-11-12`. Note that `aod.cj1`, `aod.nn1`, `aod.tb9`, and `aod.C0663p1` are merged multi-purpose utility classes in R8/ProGuard output and keep their raw class names while individual unambiguous methods are aliased in [the AOD 9 alias table](AOD_9_ALIASES.md).

## 1. Host Domains, IDs, & Cross-Process Sharing

One UI 9 splits lockscreen, AOD, informative tiles, and cover-screen widgets across three `AppWidgetHost` IDs behind shared facade and manager abstractions:

| Surface | Host ID | Config & Options | Core Managers / Hosts / Providers |
| --- | --- | --- | --- |
| **Lockscreen & AOD Complications** | `2304` | `c64.f4601a`: `hl6(semHostType=2, sizes=[2x1(2), 1x1(1)], flags=2, hostId=2304, 64)`; `fy0.f10829a`: `{2, [2, 1, 8], 2, 0}`; `appWidgetCategory=8192`, `semWidgetStyle=2` | `ComplicationManager`, `ComplicationGridUIController`, `BriefWidgetManagerImpl`, `ComplicationWidgetHost` (`aod.f11`), `ComplicationAppWidgetHost` (`aod.tz0`), `ComplicationWidgetHostManagerImpl` (`aod.i11`), `ComplicationWidgetDataProvider` (`aod.e11`) |
| **Informative / AOD Tiles** | `2346` | `fy0.f10830b`: `semHostType=4`, sizes `[2, 1, 8]`, `hostId=2346`, `appWidgetCategory=8192`, `semWidgetStyle=2` *(note: `ad3.sendAppWidgetInitOption` sends `semHostType=2`, whereas `gc3.getHostViewByProvider` & `rc3` send `semHostType=4`)* | `InformativeHost` (`aod.rc3`), `InformativeBriefWidgetHostManager` (`aod.gc3`), `InformativeSharedWidgetFacade` (`aod.ad3`), `TileFacadeManager` (`aod.xl8`) |
| **Cover Screen** | `1911` | `fy0.f10831c`: `semHostType=4`, `hostId=1911`, `semWidgetStyle=1`, `appWidgetCategory=2` | `CoverAppWidgetHost` (`aod.pa1`), `CoverWidgetDataProviderImpl` (`aod.cd1`), `CoverComplicationWidgetManagerImpl`, `CoverGlanceAppWidgetHostView` (`aod.wa1`), `WidgetFacade` (`CoverWidgetDataControllerImpl`), `SubLauncherClockController` (`aod.hn7` + `aod.sl7`) |

### Creation, Rebinding, & Cross-Process Sync

- **Factory (`ComplicationWidgetFactory.createWidget`)**:
  1. Calls `enable(...)` when the widget ID is `Integer.MIN_VALUE`.
  2. Calls `reAllocateAndBindWidget(...)` when the URI contains a 3rd `;` segment.
  3. Resolves the host view via `getHostViewByProvider(...)` (`ComplicationWidgetHostView`, formerly `aod.l11`), sets `setResizeScale(WidgetSizeSpec)`, and applies `ComplicationWidgetBackgroundDecorator.applyBackground(...)` inside `ComplicationWidgetHostViewContainer`.
- **Host ID Rebinding & AppWidgetHost Listeners**:
  - `ComplicationWidgetHostManagerImpl.m6024l` invokes `semChangeHostIds` to re-bind widget IDs between host `2304` (`semHostType=2`) and host `2346` (`semHostType=4`).
  - `AppWidgetHostReflector.m10190B` (`aod.qz5`) attaches `AppWidgetHost.setListener(widgetId, hostView)`.
- **Observed URIs & Shared State**:
  - `SharedAppWidgetFacade` (`aod.qu6`), `InformativeSharedWidgetFacade` (`aod.ad3`), `LockAndAodSharedWidgetListManager` (`aod.i64`), `LockAndAodSharedWidgetDataManager` (`aod.h64`), and `LockscreenTilesDataController` (`aod.fa4`) observe:
    - `content://com.samsung.android.app.clockpack.provider/complication_widget_host_change`
    - `lock_settings//all_profiles_complication_enabled_list`
    - `lock_settings/complication_widget_bnr_called`
    - `lock_settings/complication_enabled_list`
- **Informative Tile Caveat (`ad3` vs `cm8`)**: `InformativeSharedWidgetFacade` returns `0` on `enable` for `ITileContent` and `null` on `getHostView`; built-in `"Informative*Tile"` views are instead produced via `getHostViewByProvider` + `aod.cm8.m2896g`, and `InformativeBriefWidgetHostManager.m5112n` (`gc3`) backs `ComplicationRealPreviewView` (`aod.p01`).
- **DressRoom / LockStar External Plugin**: `LockItemPluginService` bridges external plugin items alongside `aod.eg1`, `aod.C0663p1`, and `aod.r01`.
- **Owner User Unlock Gate (`IBriefWidgetHostManager.m6081e`, `aod.i93`)**: `userManager.isUserUnlocked(UserHandle.getUserHandleForUid(0))` blocks `forceStartListening` and `hidden_semStartListening` until the owner user unlocks.

### Feature Flags (`aod.ha6` / `Rune` & `aod.cj1`)

| Flag / Helper | Condition & Effect |
| --- | --- |
| `ha6.f12851Y` | `yi6.m13971d(0, "ro.build.version.oneui")` |
| `ha6.f12886q0`, `ha6.f12888r0` | `f12851Y >= 80000` (gates One UI 8+ complication grid & reorder flow) |
| `ha6.f12807B`, `ha6.f12809C` | `"tablet"`/`"desktop"` in `yi6.m13968a()`, and `SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_MULTI_FOLD` |
| `ha6.f12892t0` | `f12851Y >= 80000 && (f12809C || f12807B)` (vertical split complication layout on multi-fold / tablet) |
| `ha6.f12833O..S`, `f12845U`, `f12880n0` | Subdisplay policies (`COVER`, `WATCHFACE`, `LARGESCREEN`, `FULLSCREEN`, `TileType.ID_TILE_TYPE_LOCKSCREEN`) |
| `ha6.f12824J0`, `f12826K0` | `SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN` integer (`> 0`); drives `cj1.isFullScreenAODSupported` (`m2809S`: `== 1 -> true`; `== 2 -> unfolded && f5123a`; `== 3 -> unfolded && !f5123a`) |
| `ha6.f12836P0`, `f12838Q0` | `SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_3D_SURFACE_TRANSITION_FLAG` |

## 2. Blur (`SemBlurInfo`), Dim, Rounded Corners, & Adaptive Tinting

### Blur Type Selection & Curves (`WidgetBlurTypeKt`)

- **Clock & Wallpaper Classification**:
  - `isDarkClock = (76.0d >= rv2.m10714w(clockColor))`
  - Wallpaper brightness threshold: `(int) brightness > 70` (dark clock) or `>= 18` (white clock) chooses `*_WALLPAPER_BRIGHT` (`getPreset = 116`) vs `*_WALLPAPER_DARK` (`getPreset = 101`).
- **Custom Blur Attribute Arrays (`SemBlurInfo` via `BlurEffectManager` & `NativeSurfaceBlurUtils` / `aod.e70`)**:

| Curve Source | Variant | Attributes (`{...}`) & Dim |
| --- | --- | --- |
| `getLockCustomAttributes` | `*_WALLPAPER_DARK` | `{110f, 0.15f, 18f, 5f, 235f, 36.7f, 245.8f}` |
| `getLockCustomAttributes` | `*_WALLPAPER_BRIGHT` | `{110f, 0.1f, -15f, 0f, 255f, 13.3f, 235.1f}` |
| `getAodCustomAttributes` | `CLOCK_DARK_WALLPAPER_DARK` | `{110f, 0.15f, -5f, 0f, 255f, 10.7f, 70f}` (`maxY` crushed `245.8 -> 70.0`) |
| `getAodCustomAttributes` | `CLOCK_WHITE_WALLPAPER_DARK` | `{110f, 0.15f, 18f, 5f, 235f, 36.7f, 245.8f}` + `getDimColor = Color.argb((int)(darkAmount * 127.5f), 0, 0, 0)` (up to `50%` black scrim) |
| `getAodCustomAttributes` | `*_WALLPAPER_BRIGHT` | `{110f, 0.1f, -15f, 0f, 255f, 9.8f, 70f}` (`maxY` crushed `235.1 -> 70.0`) |

- Lock <-> AOD transitions interpolate linearly via `getCustomAttributesByDarkAmount(type, darkAmount)`.

### Corner Rounding & Layout Scaling

- `ComplicationWidgetHostViewContainer.setCellRoundLayoutParams` shrinks `blurView` and `previewDimView` by `BLUR_PIXEL_OFFSET = 1` per side (`width - 2*padding - 2`, `height - 2*padding - 2`) and clips via `ViewOutlineProvider.setRoundRect`.
- `ComplicationWidgetHostView.onLayout` calls `nn1.applyUniformWidgetCorners(this, radius / scaleX)` using `IWidgetSizeManager.radius$default(widgetSizeManager, 0, 1, null)` so scaled widgets preserve uniform screen-space radii.
- `ComplicationWidgetHostView.onMeasure` applies `resizeSpec.getScaleX()` / `getScaleY()` with an RTL-aware pivot and re-applies `nn1.applyWidgetContentColor(this, appliedContentColor)`.
- `nn1.resolveWidgetRoundTargetView` (`m8537N`) + `nn1.applyWidgetCornerRadii` + `CoverGlanceAppWidgetHostView` (`aod.wa1`) use `AppWidgetProviderInfoExt.m1867k(appWidgetInfo)` (checking `hidden_semColorfulAppWidgetInfo`, `hidden_semMonotoneAppWidgetInfo`, and `hidden_semLegacyAppWidgetInfo` `"widgetSize"`) to detect 3rd-party vs Samsung widgets; non-Samsung widgets inset from container bounds are rounded on inner `backgroundView.getChildAt(0)`.

### Adaptive & Monochrome Content Colorization

- `ComplicationWidgetHostView.setWidgetContentColor(int)` masks out alpha (`i & 0x00FFFFFF`). If `adaptiveContentColor` is `true`, it routes through `refreshAdaptiveContentColor()`:
  - If `nh1.m8445Q(getContext(), null)` is true, it uses `complication_widget_text_color_light`. Otherwise it interpolates between `complication_widget_text_color_dark` and `complication_widget_text_color_light` by `adaptiveColorProgress`.
  - Masks `& 16777215` and calls `nn1.applyWidgetContentColor(this, appliedContentColor)`.
- `tb9.applyTraverseWidgetColor` + `nn1.applyWidgetContentColor`:
  - Only runs when `(hostInfo.f13501c | 2) == hostInfo.f13501c && color != -1`.
  - Skips views tagged `-369098752 == "true"` or `"keepColor"`, removes `android.R.id.background` (`16908288`), and tints `TextView`, `ProgressBar`, `ImageView` (including reflective `setPathColor` / `hidden_semSetPathColor`), and `ListView` (`semSetMonotoneColor`).

## 3. State Matrix & All Blur / Visual Nerfs

| State / Condition | Exact Native Behavior |
| --- | --- |
| **AOD without wallpaper** (`!showWallpaperOnAOD` / `aod_show_lockscreen_wallpaper == 0`) | `ComplicationWidgetHostViewContainer.refreshBlur` feeds `Color.argb((int)(255 * darkAmount), 43, 43, 43)` (`#2B2B2B`) to `SemBlurInfo.Builder.setBackgroundColor` instead of `WidgetBlurTypeKt.getDimColor`. |
| **AOD with wallpaper** | Curve interpolates toward `maxY = 70.0` (dark clock or bright wallpaper) or keeps Lock `maxY = 245.8` paired with `50%` black `getDimColor` (white clock + dark wallpaper). |
| **Reduce Transparency** (`accessibility_reduce_transparency`) | `applyReducedTransparencyBackground` clears compositor blur (`semSetBlurInfo(null)`), fills solid bg interpolated from `complication_widget_bg_color_blur_off_{light,dark}_wallpaper_color_{lock,aod}`, hides `dimView`, and sets `hostView.adaptiveContentColor = true` -> `refreshAdaptiveContentColor()`. |
| **NowBar Media Expand** | `WidgetObserverManager.mediaExpandStateObserver` calls `blurEffectManager.setMusicExpandState(z)` (`semSetBlurInfo(null)`) and `container.updateHostViewAlpha(z ? 0f : 255f)`; `MediaExpandStateObserver` calls `widgetStateManager.animateContents(z, dozing)`. |
| **Camera 2x2 Blacklist** | `ComplicationWidgetBackgroundDecorator.applyBackground` returns `null` (no blur/background) for `com.sec.android.app.camera/com.sec.android.app.camera.setting.launcherwidget.CameraWidget` (2x2). |
| **Picture Frame Widget** | `ComplicationWidgetBackgroundDecorator` overlays `ComplicationDimView` for `com.samsung.android.widget.pictureframe`: `#33000000` if `cj1.isFullScreenAODSupported()`, else `#8C000000`. |
| **Edit Mode / Non-`f12836P0` / Preview** | `WidgetColorManager.getPreviewDimColor()` thresholds wallpaper brightness `< 15.0d` (white clock, `rv2.m10675G(clockColor)`) / `> 80.0d` (dark clock). `SubLauncherClockController` / `aod.sl7` cases 11–12 invoke `setCoverClockEditMode` + `setWidgetBgBlurState`. `ComplicationRealPreviewView` (`aod.p01`) & `gc3.m5112n` use `#14000000`/`#FF010102` (light) vs `#1FFFFFFF`/`#FFFAFAFA` (dark). |
| **AOD Chronometer & Initial Hydration** | `ComplicationWidgetHostView` finds `"aod_disabled_chronometer"` (reflecting `Chronometer.mStarted`) to hold a 61s `PowerManager.WakeLock`, attaches `hidden_semSetOnContentAppliedListener` on window attach (`nn1.setSemOnContentAppliedListener`), and fades `alpha` `0.3f -> 1.0f` (`200ms`) on first real `RemoteViews` replacement of `defaultWidgetView`. |
