# JADX MCP rename log (session running log)

Every rename applied through the jadx MCP in this session is logged here,
newest batch last. The canonical alias tables live in
`AOD_9_ALIASES.md` / `DRESSROOM_9_ALIASES.md` / `SYSTEMUI_9_ALIASES.md`;
this file is the chronological record of *who renamed what, when, and why*
(including renames that were later reverted or skipped).

Reference APK currently loaded in jadx: `com.samsung.android.app.aodservice`
8.9.66.1 (versionCode 896601000), obfuscated classes under top-level `aod`.

Format per batch: date, target area, then a table of
`kind | old name | new name | evidence`.

---

## 2026-11-24  -  session start (continuation)

Picking up from the uncommitted "Clock ownership batch (2026-11-24)" and
"Widget tint follow-up (2026-11-24)" batches in `AOD_9_ALIASES.md`
(clock blur lifecycle/readiness classes `aod.pv2/xb0/ac0/bc0/zb0/mb0` and
clock ownership classes already aliased). Continuing with the remaining
obfuscated members of the clock blur apply pipeline.

## 2026-11-24  -  Batch 1: CombinedBlurStrategy members + debug overlay helpers (25 renames, all applied)

Context: continuing the clock-blur apply pipeline (`aod.tx0` → `aod.CombinedBlurStrategy`,
`aod.cq0` → `aod.ClockBlurManager`, `aod.dq0` → `aod.ClockBlurStateMachine`,
`aod.ec0` → `aod.ClockBlurStateInfo` were aliased in the previous session).

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| field | `aod.CombinedBlurStrategy.f29391a` | `logTag` | Constructor arg validated as `"tag"`, used as AODLogger tag |
| field | `aod.CombinedBlurStrategy.f29392b` | `clockHost` | `aod.p67` holder; `.clockView` cast to `AbsClockView` at every use |
| field | `aod.CombinedBlurStrategy.f29393c` | `blurDebugHelper` | `aod.w06` instance, only used behind `bj1.clockBlurDebugOverlayEnabled` |
| field | `aod.p67.f22785o` | `clockView` | `java.lang.Object` field cast to `AbsClockView` at all use sites |
| method | `aod.w06.m12636A` | `initializeDebugOverlay` | Log tag "ClockBlurDebugHelper", creates `aj1` overlay on root ViewGroup |
| method | `aod.w06.m12653R` | `updateDebugOverlayWithPath` | Log tag "ClockBlurDebugHelper", offsets path onto overlay |
| field | `aod.w06.f32096p` | `debugOverlayView` | Holds the `aj1` overlay view |
| field | `aod.w06.f32097q` | `debugRootView` | Root ViewGroup the overlay is added to |
| class | `aod.aj1` | `aod.ClockBlurDebugOverlayView` | View that draws a debug `Path` in green stroke |
| field | `aod.ClockBlurDebugOverlayView.f2474o` | `debugPath` | Path drawn in `onDraw` |
| method | `aod.gv8.m5413w` | `getViewOrigin` | Returns `PointF` from `getLocationOnScreen` or `getGlobalVisibleRect` |
| field | `aod.bj1.f3756v` | `clockBlurDebugOverlayEnabled` | Gates creation of the ClockBlurDebugHelper in `ClockBlurManager.<init>` |
| field | `aod.ClockBlurManager.f5366a` | `clockHost` | Same `aod.p67` holder as strategy's `clockHost` |
| field | `aod.ClockBlurManager.f5367b` | `logTag` | `"ClockBlurManager@..._parent@..."` logger tag |
| field | `aod.ClockBlurManager.f5369d` | `globalLayoutListener` | `aod.xp0` `OnGlobalLayoutListener` |
| field | `aod.ClockBlurManager.f5380o` | `blurDebugHelper` | `aod.w06` debug helper, created when debug flag on |
| field | `aod.ClockBlurManager.f5382q` | `unsupportedBlurClockDelegate` | `ClockBlurPropertyDelegate` index 0 → KProperty "unsupportedBlurClock" |
| field | `aod.ClockBlurManager.f5383r` | `blurEffectEnabledDelegate` | index 1 → "blurEffectEnabled" (read by `isBlurEffectEnabled`) |
| field | `aod.ClockBlurManager.f5385t` | `weatherInfoEnabledDelegate` | index 2 → "weatherInfoEnabled" |
| field | `aod.ClockBlurManager.f5386u` | `collectedViewsLogDelegate` | index 3 → "collectedTextViewLogMsg" |
| method | `aod.ClockBlurManager.m2925f` | `formatColorDebug` | Formats ARGB int as "Color R .. G .. B .. A .." |
| field | `aod.ClockBlurStateMachine.f7198b` | `logTag` | Constructor string tag |
| field | `aod.ClockBlurStateInfo.f8153c` | `timestampMillis` | `System.currentTimeMillis()`, printed as `timestamp=` in toString |
| field | `aod.sx0.f28030o` | `listenerMode` | Switch discriminator (0 = blur transition, 1 = ContentListView) |
| field | `aod.sx0.f28031p` | `targetAlpha` | Passed to `finishBlurTransitionAlpha` |
| field | `aod.sx0.f28032q` | `target` | Owning object (`CombinedBlurStrategy` / `ContentListView`) |

Note: `aod.p67`, `aod.w06`, `aod.gv8`, `aod.bj1` are Samsung mega utility/flag classes  - 
deliberately NOT renamed as classes (same convention as `aod.qn1`); only their
clock-blur-specific members were aliased. `aod.rx0` was already renamed to
`aod.CombinedBlurTransitionFrame` by the previous session.

## 2026-11-24  -  Batch 2: blur value objects + property delegate (11 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| class | `aod.l49` | `aod.WallpaperBrightnessInfo` | toString label `WallpaperBrightnessInfo(brightness=, saturation=)`; returned by `ClockBlurManager.resolveBlurColorMetrics` |
| field | `aod.WallpaperBrightnessInfo.f17619a` | `brightness` | double brightness, logged as `wallpaper brightness=` |
| field | `aod.WallpaperBrightnessInfo.f17620b` | `saturation` | float saturation, logged as `saturation=` |
| class | `aod.pb0` | `aod.ColorCurveParams` | toString label `ColorCurveParams(x1=, y1=, x2=, y2=, minY=, maxY=)`; returned by `ClockBlurColorCurvePolicy.selectColorCurve` |
| field | `aod.ColorCurveParams.f22930a..f22935f` | `x1, y1, x2, y2, minY, maxY` | toString labels |
| field | `aod.ClockBlurPropertyDelegate.f2673p` | `propertyIndex` | Switch over KProperty index 0..3 |
| field | `aod.ClockBlurPropertyDelegate.f2674q` | `ownerManager` | Owning `ClockBlurManager` |

Xref note: `ClockBlurManager.applyTextBlur` is called from
`AbsClockView.setClockTextBlur`, `AbsClockView.applyTextColorBatch`,
`aod.xp0.onGlobalLayout` (the `globalLayoutListener`), and `setWeatherData`.

## 2026-11-24  -  Batch 3: AbsClockView palette/shadow/blur surface (14 renames, all applied)

Owner: `com.samsung.android.uniform.widget.clock.AbsClockView` (190 methods).

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| method | `AbsClockView.m16905G` | `getAdjustedAodPaletteItem` | Log label `getAdjustedAODColorItem`; AOD-adjusts palette colors via `m16903F` |
| method | `AbsClockView.mo16917M0` | `updatePaletteAndPrimaryColor` | Log label `updatePaletteAndPrimaryColor` |
| method | `AbsClockView.mo16921O0` | `updatePaletteOnAnimation` | Log label `updatePaletteOnAnimation` |
| method | `AbsClockView.mo1634d0` | `applyShadowStyle` | abstract `ShadowStyle` hook, called from `updateClockShadowStyle` |
| field | `f43076e1` | `hasFrame` | Set by `setHasFrame(boolean)` |
| field | `f43069b0` | `paletteStyle` | `EnumC0303fb` Preset/Adaptive/Custom, read by `getPaletteStyle` |
| field | `f43083i0` | `paletteIndex` | 0 on preset, -1 on adaptive/custom |
| field | `f43086l0` | `customColor` | Logged in `setCustomColorInternal`; copied into `lockscreenPalette` |
| field | `f43089o` | `logTag` | `getSimpleName() + category + @hash` logger tag |
| field | `f43075e0` | `presetPaletteItem` | Cloned into `lockscreenPalette` when preset selected |
| field | `f43081h0` | `paletteCode` | Set from `getPaletteCode()` |
| field | `f43110y0` | `externalShadowStyle` | Log `use ShadowStyle from external`; set by `setShadowStyle` |
| field | `f43112z0` | `currentShadowStyle` | Returned by `getClockShadowStyle()` |
| field | `f43072c1` | `weatherInfo` | `WeatherInfo` holder, default `new WeatherInfo(false, null, null, "")` |

## 2026-11-24  -  Batch 4: block-reason mapping + isShown tracking (2 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| field | `aod.PersistentBlurReasonMapping.f20671a` | `persistentReasonKindByOrdinal` | `int[]` over `ClockBlurBlockReason.values()`; 1 = BLUR_EFFECT_DISABLED, 2 = SGG_ACTIVE; read by `ClockBlurStateInfo.hasPersistentBlockReason` |
| field | `aod.ClockBlurManager.f5368c` | `lastKnownIsShown` | Mirrors `AbsClockView.isShown()`; on false→true change `aod.xp0.onGlobalLayout` calls `rebuildBlurTextViews()` + `applyTextBlur(...)` (window blur application) |

Left raw deliberately: `aod.ClockBlurManager.f5365v` (synthetic `KProperty[]` for the
delegated properties). Sweep after batches 1-4: `CombinedBlurStrategy`,
`ClockBlurManager`, `ClockBlurStateMachine`, `ClockBlurStateInfo`,
`ClockBlurParameters` have no remaining non-synthetic obfuscated members.

Session totals: 52 renames applied via jadx MCP (12 + 15 + 11 + 14... see batches:
Batch 1 = 25 incl. class renames, Batch 2 = 11, Batch 3 = 14, Batch 4 = 2), all verified.

## 2026-11-24  -  Batch 5: animation-state manager, sublauncher coroutine, layout listener (12 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| field | `aod.ClockAnimationStateManager.f15995o` | `containerMode` | ctor switch: 1=ArrayDeque, 2=rp4, default=CopyOnWriteArrayList |
| field | `...f15996p` | `stretchAnimationRunning` | written by `setStretchAnimationRunning` |
| field | `...f15997q` | `springAnimationRunning` | written by `setSpringAnimationRunning` |
| field | `...f15998r` | `lastNotifiedRunningState` | read/written by `notifyAnimationStateChanged`; reused as drain guard in `drainPendingRunnables` |
| field | `...f15999s` | `callbackContainer` | ArrayDeque of Runnables / rp4 / CopyOnWriteArrayList of `ClockBlurExtensionCallback` |
| method | `aod.ClockAnimationStateManager.m6679a` | `drainPendingRunnables` | polls queued Runnables while animating |
| method | `aod.ClockAnimationStateManager.m6682d` | `clearCallbackStore` | walks rp4 store then `m10603g()` (clear) |
| field | `aod.SubLauncherClockActions.f27645o` | `coroutineState` | Function2 state-machine switch index (cases 0..13) |
| field | `aod.SubLauncherClockActions.f27646p` | `ownerController` | `SubLauncherClockController` captured in ctor |
| field | `aod.xp0.f34554o` | `listenerMode` | `onGlobalLayout` switch discriminator (case 0 = blur re-apply) |
| field | `aod.xp0.f34555p` | `clockBlurManager` | cast to `ClockBlurManager` in case 0 |
| field | `aod.xp0.f34556q` | `clockViewFrame` | FrameLayout cast to `AbsClockView` for isShown |

Skipped: `aod.z76` (empty marker interface implemented by ClockAnimationStateManager,
no members to justify a name); `ComplicationWidgetHostViewContainer` fields are
already descriptive (`blurView`, `blurBGColor`, `darkAmount`, ...).

Session totals so far: 64 renames (25 + 11 + 14 + 2 + 12).

## 2026-11-24  -  Batch 6: stretch measure use case + Now Bar stack callbacks (21 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| field | `aod.RunStretchMeasureProcessUseCase.f9742a` | `clockPositionCalculator` | ctor param name (`ee5`) |
| field | `...f9743b` | `clockStretchDataRepository` | ctor param name (`st0`) |
| field | `...f9744c` | `clockDataRepository` | ctor param name (`gq0`) |
| field | `...f9745d` | `stretchMeasureProcessRunner` | ctor param name (`dn5`) |
| field | `...f9746e` | `logTag` | `"RunStretchMeasureProcessUseCase_<category>[clock_position][stretch_ui]"` |
| method | `...m4462b` | `computeStretchContentRect` | static; copies `rt0.f26697k` rect, shrinks bottom by `f26689c * f26692f` |
| field | `aod.SetNowBarMediaExpandStateUseCase.f4330a` / `f4331b` | `nowBarMediaExpandStateRepository` / `statusCallback` | ctor param names |
| method | `...m2426a` | `setMediaExpandedState` | writes repo flag, posts LiveData, calls `onMusicItemExpaned(z)` |
| method | `...m2427b` | `updateExpandStateLiveData` | posts to `repo.f30616c` LiveData only |
| field | `aod.ClockStretchPhysicsAnimator.f4736n` | `debugLogEnabled` | `= bj1.f3760z`, gates "StretchAnimator" debug logs |
| field | `aod.ClockStretchPhysicsAnimator.f4749m` | `profiler` | `p87` with `String[100]` + `long[100]` ring buffers |
| field | `aod.NowBarStackCardCallbacks.f2395o` / `f2396p` | `callbackMode` / `callbackTarget` | multi-interface dispatcher; mode switch in `mo1405N`, target cast per interface |
| method | `aod.NowBarStackCardCallbacks.m1389v0` | `loadCoverKeyguardImage` | decodes `content://com.android.systemui.keyguard.image/custom?type=cover...` |
| method | `...m1391A0` | `onExpandVIStarted` | `setExpandVIStarted` + same-name log |
| method | `...m1393B0` | `onExternalNowbarExpandCardAttached` | log label in catch block |
| method | `...m1454t0` | `createBottomMarginAnimator` | delegates to `NowBarCardViewModel.createBottomMarginAnimator` |
| method | `...m1460x0` | `notifyViewVisibilityChanged` | `AutofillManager.notifyViewVisibilityChanged` passthrough |
| method | `...m1462y0` | `onCollapsedTouchFromCard` | log label "onCollapsedTouchFromCard" |
| method | `...m1464z0` | `onExpandCardInflatedFromCardView` | log label "onExpandCardInflatedFromCardView" |

Skipped: `NowBarStackCardCallbacks.m1458w0` (calls `m442Q()` on an unresolved
fragment-manager-like field of `tm2` target  -  left raw pending identification) and
`m1456u0` (generic protobuf ByteString walker).

Session totals so far: 85 renames (25 + 11 + 14 + 2 + 12 + 21).

## 2026-11-24  -  Batch 7: ModuleClockView widget-host fields (17 renames, all applied)

Owner: `com.samsung.android.uniform.widget.clock.ModuleClockView` (85 KB source, the
module-facing clock view that hosts the brief-widget/face-widget containers).

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| field | `f43476l1` | `frameWidgetTypeProvider` | `aod.sn2`, `getFrameWidgetType(int)` |
| field | `f43477m1` | `settingsHelper` | `SettingsHelper` |
| field | `f43478n1` | `briefWidgetComponent` | `aod.jh0`, created in `createBriefWidgetComponent` |
| field | `f43479o1` | `briefWidgetManager` | `IBriefWidgetManager`: `initContents`, `setPreviewMode`, `setEditable`, `getWidgetContainerHeight` |
| field | `f43480p1` | `briefWidgetContainer` | `findViewById(hs5.brief_widget_container)` |
| field | `f43481q1` | `clockWidgetContainer` | `findViewById(hs5.basic_clock_widget_clock_container)`; chosen when `FrameWidgetType.LINEAR` |
| field | `f43482r1` | `clockView` | inner `AbsClockView` |
| field | `f43483s1` | `moduleInfo` | `setModuleData(String)` stores it; passed to `briefWidgetManager.initContents` |
| field | `f43484t1` | `currentFrameWidgetType` | compared against `getFrameWidgetType(...)` in `updateWidgetType` |
| field | `f43485u1` / `f43486v1` | `positionX` / `positionY` | int pair fed into `ek4` position data in `updatePositionData` |
| field | `f43487w1` | `clockScale` | float, default 1.0f, first arg of `ek4` |
| field | `f43488x1` / `f43489y1` | `previewMode` / `editable` | passed to `setPreviewMode` / `setEditable` |
| field | `f43490z1` | `frameDecorator` | `C0663p1` wrapping the active FrameLayout, receives `dozeAmount` |
| method | `m17024U0` | `hasWidgetManagers` | `briefWidgetManager != null && briefWidgetComponent != null` |
| method | `m17025V0` | `updatePositionData` | log label "updatePositionData", writes `dk4` (ModuleClockPositionController) |

Skipped: `f43474j1` / `f43475k1` (two public `TileFacadeManager`s  -  only nulled in
this class and read as a pair next to the two containers; assigned externally, left raw).

Session totals so far: 102 renames (25 + 11 + 14 + 2 + 12 + 21 + 17).

## 2026-11-24  -  Batch 8: AbsClockView remaining surface (52 renames, all applied)

Owner: `com.samsung.android.uniform.widget.clock.AbsClockView`.

Methods (30): `m16885A`→`dumpFontData`, `m16886C0`→`colorToHexString`,
`m16887D0`→`setTextViewsAlpha`, `m16888H`→`applyAlphaToColor`,
`m16889q0`→`setImageViewsSize`, `m16890r0`→`setClockTextViewsSize`,
`m16891v`→`copyDrawable`, `m16892w0`→`setTextSafely`, `m16893x`→`copyFontData`,
`m16894y0`→`setTransitionNameAll`, `m16895z0`→`setViewsVisibility`,
`m16896A0`→`isStretchFontTypeChange`, `m16897B`→`applyFixedFontToViews`,
`m16901E`→`forceRefreshSubDateTime`, `m16903F`→`adjustColorForAod`,
`m16906G0`→`updateTimeUpdateRegistration`, `m16909I0`→`updateDozeDateTime`,
`m16912K`→`getPositionInClockView`, `m16914L`→`applyScaleToSize`,
`m16915L0`→`updateIsWhiteWallpaper`, `m16918N`→`parseAttributes`,
`m16922P`→`isLegacyClockType`, `m16923P0`→`applyPresetPalette`,
`m16928S0`→`updateSubDateTime`, `m16930T0`→`setLogTagForCategory`,
`m16931U`→`isFixedTimeSet`, `m16937m0`→`refreshWeatherInfo`,
`m16938n0`→`syncImageViewMetrics`, `m16939o0`→`syncTextViewMetrics`,
`m16947v0`→`selectPresetPalette`.

Fields (22): `f43017i1`→`observedContentUris`, `f43068a1`→`stretchFrameDrawable`,
`f43078f1`→`contentObserver`, `f43084j0`→`widgetCount`, `f43085k0`→`paletteTransitionReady`,
`f43087m0`→`colorThemeColor`, `f43088n0`→`aodColorThemeColor`, `f43090o0`→`areaOnWallpaper`,
`f43091p`→`fixedTimeMillis`, `f43092p0`→`formatDate`, `f43093q`→`logDateFormat`,
`f43094q0`→`formatDateAbbreviated`, `f43095r`→`semWallpaperColors`, `f43096r0`→`dateType`,
`f43097s`→`wallpaperColorItem`, `f43099t`→`clockAreaWallpaperColor`, `f43102u0`→`seamlessAod`,
`f43103v`→`blurEffectEnabledOverride`, `f43104v0`→`isWhiteWallpaper`,
`f43106w0`→`whiteWallpaperFix`, `f43108x0`→`hasShadowStyle`, `f43111z`→`timeUpdatesEnabled`.

Skipped (evidence too weak): `m16920O` (boolean combo of flag+category+dressroom),
`f43067a0` (opaque boolean into `mi1.m8099j`), `f43070b1` (`x63` impl chosen by a
boolean  -  yo5(28) vs dn5(12)).

Session totals so far: 154 renames (25+11+14+2+12+21+17+52).

## 2026-11-24  -  Batch 9: clock view subclasses (24 renames, all applied)

`ClockFaceView`: `m16976U0`→`requestClockFaceInfoOnFontChanged`,
`m16978W0`→`changeToDefaultClockType`, `m16979X0`→`captureViewBitmap`,
`m16981Z0`→`applyBatteryActions`, `m16982a1`→`applyMinSize`,
`m16983b1`→`getLinearGradientDrawable`, `m16984c1`→`createClockFaceRequest`,
`m16985d1`→`requestClockFaceInfo`.
`ClockFaceCustomView`: `m16955V0`→`changeToDefaultClockType`,
`m16957X0`→`applyBatteryActions`, `m16958Y0`→`applyColorFilter`,
`m16959Z0`→`getLinearGradientDrawable`, `m16960a1`→`applyHoneyFaceRule`,
`m16961b1`→`readStreamBytes`, `m16962c1`→`isMainScreenCategory`,
`m16963d1`→`loadWatchFaceSurface`.
`DigitalDualClockView`: `m16989W0`→`setImageViewsColorFilter`,
`m16991V0`→`setTimeAndSpaceVisibility`, `m16992X0`→`updateAreaVisibility`,
`m16994Z0`→`applyDateTimeTexts`.
`DigitalHorizontalClockView`: `m16996W0`→`onFontStyleUpdated`,
`m16997X0`→`showNormalTimeArea`.
`AnalogClockView`: `m16951V0`→`applyScalePivotToView`.
`CityClockView`: `m16953a1`→`setTextSizeScaled`.
Skipped (unclear bodies): `ClockFaceView.m16977V0/m16980Y0`,
`ClockFaceCustomView.m16954U0/m16956W0`, `DigitalDualClockView.m16990U0`,
`DigitalHorizontalClockView.m16998Y0/m16999Z0`, `AnalogClockView.m16950U0/m16952W0`.

## Area status (2026-11-24)

- `common.grid.rule` (32 classes): Samsung-named already; sampled 4 classes,
  only 1-2 synthetic raw methods each  -  nothing worth renaming.
- `common.provider.p003DB` (17 classes incl. DBManager/DBItem/AbsSettingsDBItem/
  ComplicationWidgetSettingsDBItem): fully named, 0 raw members in samples.
- `facewidget.complication` beyond the container (ComplicationWidgetHostView,
  WidgetBlurType, ComplicationSizeState): fully named, 0 raw members.
- Remaining obfuscation is concentrated in the `aod.*` mega-package.

Session totals: 178 renames (25+11+14+2+12+21+17+52+24).

## 2026-11-24  -  Batch 10: debug-flag wall (33 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| class | `aod.xi1` | `aod.BuildTypeFlags` | build-type checks |
| field | `aod.BuildTypeFlags.f34315a` / `f34316b` | `isNonShipBuild` / `isEngOrUserdebugBuild` | `!ro.product_ship` / `Build.TYPE` eng+userdebug |
| class | `aod.bj1` | `aod.AodDebugFlags` | all flags read from SemSystemProperties debug.* |
| field | `AodDebugFlags.f3735a..f3734C` (28 flags) | `debugTickInterval`, `debugStartDelayed`, `debugLayout`, `debugDelayLiveClockTime`, `debugEdgePosition`, `debugAllowClockProvider`, `debugRoaming`, `debugDisableSensorScenario`, `debugDisplayLocale`, `debugTapToShow`, `debugLiveClockSelfMoveInterval`, `debugLandscapeMode`, `debugHourMin`, `debugSubscreenActivityOnMain`, `debugOverlayOnWindow`, `debugBuild`, `debugCameraCoverOnSideCover`, `debugFaceWidgetTransitionTest`, `subdisplayTestMode`, `debugEdgePositionRange`, `debugClockMovableArea`, `clockBlurStateDebugEnabled`, `logModuleNonePaddingTextView`, `logModulePositionClock`, `logModulePositionSubLauncherClock`, `logModuleStretchAnimator`, `logModuleClockTransition`, `logModuleSecKeyguardClockContainer`, `debugPreviewSaveBitmap` | each maps 1:1 to its `debug.*`/`persist.debug.*` property name |

Note: `f3756v` was first aliased `clockBlurDebugOverlayEnabled` in batch 1, now
renamed to the accurate `clockBlurStateDebugEnabled` (`debug.aod.clock_blur_state`).

## 2026-11-24  -  Batch 11: settings-key infrastructure + Now Bar AOD gate (22 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| class | `aod.rt6` | `aod.SettingKeyEntry` | one registered Settings key |
| field | `SettingKeyEntry.f26714a/b/c/d/e/f/g/h/i/j` | `settingsTable` / `key` / `keyUri` / `dataType` / `defaultIntValue` / `userSuffix` / `valueCached` / `cachedIntValue` / `cachedStringValue` / `ownerHelper` | ctor validates table in {System,Secure,Global}, type in {Int,String}; `m10654c` builds `Settings$<table>.getUriFor(key)` |
| method | `SettingKeyEntry.m10652a` / `m10653b` / `m10654c` / `m10655d` | `getIntValue` / `getStringValue` / `buildKeyUri` / `loadValueFromResolver` | reflective Settings reads |
| method | `aod.z82.m14360M` / `m14362O` | `registerSettingEntry` / `getSettingEntry` | settings cache (mega class, only these two aliased) |
| field | `SettingsHelper.f39619e` | `settingsCache` | the z82 cache |
| method | `SettingsHelper.m15243x` | `isNowBarAodEnabled` | reads `now_bar_aod_enabled==1` + fullscreen-AOD + media-card-on-top check |
| method | `SettingsHelper.m15244y` / `m15245z` / `m15200B` | `isLockscreenWallpaperTransparent` / `isRemoveAnimationsEnabled` / `isCoverScreenShowNotificationEnabled` | key reads |

Hidden-feature findings written up in `docs/research/HIDDEN_FEATURES.md`
(227 registered Settings keys incl. `now_bar_aod_enabled`, backup key
`/AOD/ShowNowBarEnabled`, the eng/userdebug debug-flag wall).

Session totals: 233 renames (25+11+14+2+12+21+17+52+24+33+22).

## 2026-11-24  -  Batch 12: device feature wall (27 renames, all applied & verified)

- class `aod.ha6` → `aod.DeviceFeatureFlags` (first attempt was blocked by the
  approve filter; field renames against the new name silently no-opped, so the
  class rename was retried and then all field renames re-applied  -  verified via
  `get_fields_of_class` containing `aodFullscreenLevel`)
- 26 fields aliased with clinit evidence: `f12824J0`→`aodFullscreenLevel`
  (`SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN`), `f12845U`→`supportsLockscreenTile`,
  `f12847V`→`supportsAodTile`, `f12866g0`→`supportsVirtualDisplay`,
  `f12878m0`→`selfPokeDrawLockEnabled` (`persist.enable.self.poke.draw_lock`),
  `f12848V0`→`foldPerformanceDebug` (`debug.aod.fold_performance`),
  `f12865g`→`isChinaDevice`, `f12899x`→`isIranDevice`, `f12871j`→`isUsDevice`,
  `f12873k`→`isKoreaDevice`, `f12867h`→`isChinaCarrier`, `f12829M`→`isE5SeriesDevice`,
  `f12817G`→`isB4qE4qDevice`, `f12813E`→`nativeAiEnabled`, `f12828L0`→`nativeAiDisabled`,
  `f12808B0`→`defaultClockPackage`, `f12897w`→`hasClockPackVersionOverride`,
  `f12854a0`→`hasBatteryTimeToFull`, `f12856b0`→`hasLedPattern`,
  `f12858c0`→`isAmoledDisplay`, `f12900x0`→`aiConfigVersionGte20263`,
  `f12902y0`→`aiConfigVersionLt20263`, `f12898w0`→`aiBriefSupported`,
  `f12831N`→`supportsDisabledMenuN40`, `f12850X`→`frameworkVersionGte110500`,
  `f12876l0`→`semVersionGte50000`, `f12886q0`→`semVersionGte80000`

Findings from the sweep (providers, receivers, SCPM policies, Rubin feeds,
NHN Now Bar provider, Dressroom provider) are in `HIDDEN_FEATURES.md`.
"experimental" keyword hits (`aod.i57/je2/st8`) were false positives
(kotlin metadata / androidx annotations).

Session totals: 260 renames (…+33+22+27).

## 2026-11-24  -  Batch 13: SCPM policy manager + per-app Now Bar gates (6 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| class | `aod.df6` | `aod.ScpmAodPolicyManager` | log tag "ScpmManager_AOD"; registers with `com.samsung.android.scpm.policy` provider via ConfigurationApi/RegistrationApi (signature), stores policy XML at `Secure/scpm_aod_common_policy` |
| class | `aod.AbstractC0814t1` | `aod.AodCommonSettingsUtils` | log tag "AODCommonSettingsUtils" |
| method | `AodCommonSettingsUtils.m11184A` | `isNowBarCommutePolicyEnabled` | `policy.contains("nowbar_commute")` on `Secure/scpm_aod_common_policy` |
| method | `...m11185B` | `isGoogleMapsNowBarCommuteEnabled` | `!isChinaDevice && f12840R0 && commutePolicy && Settings.System key_now_bar_com_google_android_apps_maps_internal==1` |
| method | `...m11186C` | `isGoogleFinanceNowBarEnabled` | `key_now_bar_..._googlequicksearchbox_finance` |
| method | `...m11187D` | `isGoogleQuickSearchBoxNowBarEnabled` | `key_now_bar_..._googlequicksearchbox` |

Sweep-2 findings (SCPM flow, 13-key `key_now_bar_*` per-app family, `dls_state`
bitmask bits 16/64/8192, full 107-key settings registry, search-index-exposed
subset) are all in `HIDDEN_FEATURES.md` sections E-H.

Session totals: 266 renames (…+27+6).

## 2026-11-24  -  Batch 14: settings consumers identified in key-mapping sweep (4 renames, all applied)

| Kind | Old | New | Evidence |
| --- | --- | --- | --- |
| class | `aod.C0336g7` | `aod.AODSettingsMainPrefsFragment` | log tag "AODSettingsMainPrefsFragment"; writes `aod_erase_background`, updates preview background |
| class | `aod.iz6` | `aod.SleepStateManager` | log tag "SleepStateManager"; writes `aod_sleep_state` / `aod_context_fence_not_working` from Rubin fence results |
| class | `aod.C0409i6` | `aod.RunestoneManagerDump` | dumps "RunestoneManager:" incl. aodContextFenceNotWorking |
| method | `SettingsHelper.m15241v` | `isMultipleLockscreenEnabled` | read by `KeyguardComplicationObserver` when `kg_multiple_lockscreen` changes |

Key→feature consumer map (20 keys traced) appended to `HIDDEN_FEATURES.md` §"Key → feature consumer map".

Session totals: 270 renames (…+6+4).

## 2026-11-24  -  Batch 15: hidden test-mode UI (1 rename, applied)

- class `aod.ti8` → `aod.TestModeSettingFragment`  -  88 KB fragment hosted by
  `aod.setting.TestModeSettingActivity`; exposes 14 hidden test keys
  (`aod_brightness_test_mode`, `aod_stretch_anim_*`, `aod_star_*`,
  `aod_light_reveal_alpha`, `aod_bold_font_test_mode`, `cover_screen_demo_mode`,
  `aod_moving_range_width`) and reads `fix_aod_position` / `aod_clock_type` /
  `locale_index` / `calendar_index` intent extras.

Knox AI key explained (SemEnterpriseDeviceManager app restrictions for
`com.samsung.android.knox.galaxyai`), hidden activities list, and the test-key
inventory are in `HIDDEN_FEATURES.md` sections I-K.

Session totals: 271 renames (…+4+1).

## 2026-11-24  -  Batch 16: test-mode gate methods (2 renames, applied)

- `aod.wt6.m12972e` → `isAodTestModeEnabled` (`Settings.System "aod_test_mode_only"==1`)
- `aod.wt6.m12975h` → `isHijriTestModeEnabled` (also requires `aod_hijri_test_mode`)

Rubin/Runestone inventory (persona/context providers, RunestoneSleepApi fence
flow) documented in `HIDDEN_FEATURES.md` §Rubin; `aod_test_mode_only` +
`aod_hijri_test_mode` are hidden keys.

Session totals: 273 renames (…+1+2).

## 2026-11-24  -  Batch 17: log-tag-driven settings UI/helper class renames (7 renames, all applied)

| Old | New | Evidence (log tag / role) |
| --- | --- | --- |
| `aod.nb2` | `aod.FaceWidgetSettingsMainPrefFragment` | "FaceWidgetSettingsMainPrefFragment"; where_to_show switch |
| `aod.ga2` | `aod.FaceWidgetMusicSettingsPrefFragment` | tag + music transparency seekbar |
| `aod.tn1` | `aod.DimAnimationHelper` | "DimAnimationHelper"; writes facewidget_music_transparency |
| `aod.s66` | `aod.ResetUtils` | "ResetUtils"; resets custom_wallpaper_color_areas_* |
| `aod.la2` | `aod.ExternalFileReader` | "ExternalFileReader" |
| `aod.C0372h6` | `aod.SleepStateSettingObserver` | ContentObserver handler for aod_sleep_state / aod_context_fence_not_working |
| `aod.jz6` | `aod.SleepStateObserverRegistrar` | registers the observer on SleepStateManager |

Extra key mappings this pass: `aod_show_lockscreen_wallpaper` (AOD display switch in
`aod.C0114a5`), `buds_enable` (Buds state on music widget via FaceWidgetMusicViewModel),
`ai_flex_window` (Galaxy AI flex window toggle, aod.l38 + CVS106 analytics),
`sub_display_system_wallpaper_transparency==2` (wt6 "SettingsUtils"),
`lock_clock_adaptive_colors` (read via SettingsHelper constant, no literal consumers).

Final AOD exploration summary: `AOD_9_EXPLORATION_SUMMARY.md`.

SESSION TOTAL: 273 renames, 17 batches, all applied and documented.

## 2026-11-24  -  Batch 18: key-trace helpers (2 renames, applied)

- `aod.v10` → `aod.ScpmSubscreenPolicyHandler` (log "ScpmSubscr"; parses SCPM
  subscreen preload policy JSON `package_list` → `Secure/scpm_subscreen_preload_policy_payload`)
- `aod.rv2` → `aod.MultiFoldSubDisplayModeController` (writes `aod_multi_fold_sub_display_mode`)

16 more keys traced (section L of HIDDEN_FEATURES.md) + Rubin→Now Bar card wiring
(section M): package→card map in `aod.h05`, commute/finance/call card layouts in
`aod.ct5`, Knox `grayout` filter in `aod.b05`, item source `NowBarItemManager`.

SESSION TOTAL: 275 renames, 18 batches.

## 2026-11-24  -  Batch 19: Dressroom 3.0.10.38 clock-ownership batch, finished (80 renames, all applied)

Loaded APK switched to `com.samsung.android.app.dressroom` 3.0.10.38 (301038000)
in jadx-gui; continued the previous session's pXXX clock-ownership batch.
Field names derived from constructor parameter names (exact match) unless noted.

- `p260mf.ClockBlurIdleStateCoordinator`: f16744a→`coroutineScope`,
  f16745b→`getObservableClockDataUseCase`, f16747d→`getObservablePreviewStateUseCase`,
  f16748e→`getObservableClockAssistDataUseCase`, f16749f→`getPreviewScrollUpHelperDataUseCase`,
  f16750g→`getObservableWallpaperInfoDataUseCase`, f16751h→`updateClockIdleUseCase`,
  f16756m→`lazyClockDataFlow` (Lazy fields m,n,o,p,q,s left raw  -  no evidence)
- `p035bp.ClockBlurCallback`: f1665a→`getObservableClockDataUseCase`, f1666b→`getObservablePreviewStateUseCase`
- `p260mf.ClockMeasureStateCoordinator`: f16792a→`clockDataObservable`
- `p347qo.BlurItemController`: f20475a→`context`, f20476b→`itemDataSet`, f20477c→`lifecycleOwner`,
  f20478d→`previewViewModel`, f20479e→`getPreviewScaleUseCase`, f20481g→`clockBlurLayoutViewModelProvider`,
  f20482h→`widgetBlurLayoutViewModelProvider`, f20483i→`shortcutBlurLayoutViewModel`,
  f20484j→`getObservableBlurConfigurationUseCase`
- `p347qo.BlurInfoFactory`: f20472a→`getObservableBlurConfigurationUseCase`
- `p260mf.C5456f2` → class `p260mf.SaveClockDataUseCase`; f16836b..f16845k →
  `clockStoreRepository`/`clockDataRepository`/`profileDataRepository`/`screenSizeRepository`/
  `deviceTypeRepository`/`clockAssistDataRepository`/`isThemeAppliedSettingRepository`/
  `clockMovableAreaRectRepository`/`clockMarginRepository`/`orientationRepository`,
  f16846l→`tag`, f16847m→`executionPhase` (EnumC3656g.End)
- `p260mf.ClockPositionIdleFlow` (coroutine lambda): f17079a→`label`, f17080b→`collector`,
  f17081c→`clockPositionRect`, f17082d→`ownerCoordinator`
- `p260mf.ClockIdleConditions` (Function6, result = a&&b&&c&&d&&!e): f17065a→`animationIdle`,
  f17066b→`layoutStable`, f17067c→`measureIdle`, f17068d→`positionIdle`, f17069e→`scrollUp`
- `p441vo.ClockPreviewItem`: 35 fields → ctor param names (lifecycleOwner, layoutChangeListener,
  getObservableClockData, clockView, clockPreviewViewModel, sidePanelView,
  clockPanelViewModelProvider, worldClockPreviewController, palettePanelViewModelProvider,
  weatherInfoListener, uiController, activityRef, clockManagerWrapper, clockPreviewContainer,
  previewViewModel, initialized, getObservableScreenSize, screenSizeLiveData, saLogging,
  coroutineScope, initClockSizeUseCase, changeClockSizeUseCase, initClockPositionInfoUseCase,
  getUIModeUseCase, getClockBackgroundBitmapUseCase, updateClockCustomDataUseCase,
  worldClockPreviewControllerProvider, updateWeatherInfoUpdateListenerUseCase, updateMovableAreaUseCase,
  getObservableLockStarItemUseCase, lockStarContentsController, clockDataBindingHelper,
  wallpaperColorsController, getThumbnailDataUseCase, saveMotionClockThumbnailBitmapUseCase)
  + m9664f→`isThumbnailSavePending`, m9665g→`unregisterWeatherListener`.
  10 pure `Lazy` fields (f24129j0..f24147s0) left raw; m9663e (huge duplicated body) left raw.

SESSION TOTAL: 355 renames (273 AOD + 80 Dressroom batch 19 + 2 earlier Dressroom-adjacent).

## 2026-11-24  -  Batch 20: Dressroom self-tagged class sweep (74 class renames, all applied)

Method: searched `getSubTag` implementors (Dressroom's logger interface
`p292oc.InterfaceC5793c`), extracted each class's own `getTag()` literal, and
renamed the class to that tag. 74 classes renamed across ad/ag/ca/cd/cg/dk/ee/eg/gg/gh/md/pf/ro/sd/so/td +
p013ah/p075d7/p080dc/p083dg/p103eh/p128fh/p156hd/p158hf/p159hg/p180ih/p192j6/p199jf/p205jn/p210k2/p260mf/
p283o3/p294of/p295og/p320pd/p348qp/p362rd/p364rh/p366rj/p414ud/p434v9/p436vd/p437vf/p477xc/p481xg/p501yc +
`com.samsung.android.app.dressroom.data.media.C2333b` (MultipleItemMediatorControllerImpl).
Full list = tag names like `SaveProfileDataUseCase`, `ClockStoreRepositoryImpl`,
`PreviewController`, `LockScreenSaveFinalizer`, `StretchMediatorControllerImpl`,
`ColorOverrideAreaCoordinator`, `WidgetStoreRepositoryImpl`, … (all in DRESSROOM_9_ALIASES.md).
Skipped: already-aliased classes, empty tags, and non-obfuscated packages
(com.google/com.samsung/comsamsung hits were misattributions).

SESSION TOTAL: 429 renames.

## Batch 21  -  Dressroom p260mf use-case repository fields (66 renames)
Ctor-param-named fields (`this.fX = param;`) on 10 p260mf use cases:
- `ApplyStretchResultUseCase`: context, clockDataRepository, clockManagerWrapper
- `InitClockPositionInfoUseCase`: clockDataRepository, clockPositionRepository, screenSizeRepository, deviceTypeRepository, previewScaleRepository, widgetAssistDataRepository, clockSizeConstraintRepository, clockMeasureStateCoordinator, clockAssistDataRepository
- `ApplyWallpaperClockBundleDataUseCase`: focusedWallpaperSceneRepository, wallpaperClockBundleRepository, cachedWallpaperClockBundleRepository, clockDataRepository, clockBundleConvertRepository, widgetAssistDataRepository, notificationAssistDataRepository, clockAlignPositionDataRepository, clockTypeRepository, deviceTypeDataRepository, screenSizeRepository, orientationRepository, wallpaperObjectDataRepository
- `ResetStretchDataUseCase`: clockDataRepository, regionCacheRepository
- `ChangeClockPositionInfoUseCase`: clockDataRepository, clockPositionRepository, clockAssistDataRepository, widgetAssistDataRepository, screenSizeRepository, deviceTypeRepository, previewScaleRepository, clockSizeConstraintRepository
- `ChangeClockSizeUseCase`: clockDataRepository, clockPositionRepository, clockAssistDataRepository, widgetAssistDataRepository, notificationAssistDataRepository, screenSizeRepository, deviceTypeRepository, previewScaleRepository, clockMeasureStateCoordinator, clockSizeConstraintRepository
- `EnableFontStretchUseCase`: clockDataRepository
- `InitClockSizeUseCase`: clockDataRepository, clockPositionRepository, widgetAssistDataRepository, notificationAssistDataRepository, screenSizeRepository, deviceTypeRepository, previewScaleRepository, clockAssistDataRepository, previewStateRepository, clockRectOptimizerRepository, clockDataBindingHelper, clockMeasureStateCoordinator, clockSizeConstraintRepository
- `InitClockStretchDataUseCase`: clockDataRepository, clockDataBindingHelper, clockPositionRepository, clockManagerWrapper, screenSizeRepository
- `GetExclusiveAreaUseCase`: clockDataRepository, screenSizeRepository
Verified via get_fields_of_class (all show new names). SESSION TOTAL: 495 renames.

## Batch 22  -  Dressroom hidden debug-settings repos (14 renames)
- `be.C1510a`→`be.HighTextContrastSettingRepositoryImpl` (fields tag/settingType/settingKey; methods readRawValue/getSettingType/readBooleanValue)
- `be.C1514e`→`be.MinimalBatterySettingRepositoryImpl` (same member renames)
Keys found: `high_text_contrast_enabled`, `minimal_battery_use`. SESSION TOTAL: 509 renames.

## Batch 23  -  Dressroom self-tagged class renames via self-tag strings (89 renames)
Method: search code for `UseCase"`/`RepositoryImpl"`/`ControllerImpl"`/`ManagerImpl"`, extract the
self-tag string (`tag = "X"` / `return "X"` / first CamelCase literal), rename class to tag.
- ag: RefreshShortcutColorUseCase, RefreshShortcutUseCase, UpdateShortcutBackgroundBrightnessUseCase,
  UpdateShortcutDataUseCase, LoadRightShortcutDataUseCase, SaveInformativeClockUseCase
- p013ah.C0140g→PreviewImageFileStoreRepositoryImpl; p021ap.C1449a→SubClockPreviewFactory
- bg: UpdateFocusAreaUseCase, UpdateSubShortcutDataUseCase; p035bp.C1554q→WorldClockPreviewController
- cg: AddWidgetUseCase, BackupWidgetComponentDataListUseCase, CheckAvailableSpaceUseCase,
  EnableWidgetDataUseCase, LoadAvailableTileDataListUseCase, LoadWidgetDataUseCase,
  MakeLockStarWidgetItemUseCase, ManageLifeCycleWidgetDataUseCase, RefreshWidgetListUseCase,
  RefreshWidgetPositionUseCase, RestoreWidgetDataUseCase, SaveWidgetDataUseCase, SetWidgetIdleBlurUseCase,
  UpdateGridDataUseCase, UpdateWidgetAlignDataUseCase, UpdateWidgetBackgroundBrightnessUseCase,
  UpdateWidgetListStringUseCase
- p080dc: LockScreenBackupAndRestore, LockScreenObserverInitializer, UiControllerImpl,
  MediatorControllerImpl, MultipleItemMediatorControllerImpl, GetFocusedWallpaperSceneUseCase,
  SyncShortcutAppListUseCase
- ad: SuggestedClockStoreRepositoryImpl, SubWallpaperClockBundleMigrationRepositoryImpl,
  ClockMarginRepositoryImpl, SubClockMovableAreaRectRepositoryImpl, SubClockPositionRepositoryImpl,
  SubClockSizeConstraintRepositoryImpl, SubClockTypeRepositoryImpl, SubClockAlignPositionDataRepositoryImpl,
  SubClockItemListRepositoryImpl
- ae: SubProfileDataRepositoryImpl, SubProfileDataStoreRepositoryImpl; bd: SubWallpaperClockBundleStoreRepositoryImpl
- be: IsIconThemeApplied/IsSGGWallpaperApplied/IsThemeApplied/ReduceTransparency/SupportAODFullScreen SettingRepositoryImpl
- bh: PreloadProfileManageRepositoryImpl, SubProfileDataBuilderRepositoryImpl; cd: SceneIdsRepositoryImpl,
  WallpaperClockBundleRepositoryImpl
- p047ce: CoverAttachmentRepositoryImpl, DeviceTypeRepositoryImpl, LockThumbnailScreenSizeRepositoryImpl,
  OrientationRepositoryImpl, SystemPropertiesRepositoryImpl, ThumbnailOrientationRepositoryImpl, ScreenSizeRepositoryImpl
- de: HardwareBitmapRepositoryImpl, MotionClockThumbnailDataRepositoryImpl; ee: ColorOverrideAreaStoreRepositoryImpl,
  ColorOverrideAreaDataRepositoryImpl, WallpaperCustomColorRepositoryImpl
- p124fd: ContactInfoColorRepositoryImpl, ContactInfoPositionRepositoryImpl; gd: FrameStoreRepositoryImpl
- p156hd: InformativeImageDataRepositoryImpl; p198jd: MenuItemDataStoreRepositoryImpl; p221kd: SubMenuItemDataStoreRepositoryImpl
- p239ld: NioColorRepositoryImpl, NotificationAssistDataRepositoryImpl, NotificationPositionRepositoryImpl,
  NotificationStoreRepositoryImpl; md: SubNotificationAssistDataRepositoryImpl, SubNotificationColorRepositoryImpl
- p066cp: PreviewBlurViewModel, ClockColorPickerViewModel, UpdateEditModeLambda, GetObservableScreenSizeUseCase
- p035bp: WorldClockPreviewViewModel, CityViewHolder, WorldClockRecyclerViewAdapter
- kn.AbstractC4435a→CorpAppInfoStore; lb.C5184b→RestrictionManagerImpl
- …data.preferences.C2340b→ShortcutViewModelFactory; …provider.handler.bnr.C2383a→CancelCustomPackHandler
Skipped: SuspendLambda/Factory/Function synthetics (p007a6.C0033e, ad.C0093f/C0094g, p031b9.C1498c).
SESSION TOTAL: 598 renames.

## Batch 24  -  Dressroom prefs/debug-menu classes (3 renames)
- `com.samsung.android.app.dressroom.data.preferences.C2339a`→`LocalPreferenceDataSourceImpl`
- `p453wb.C7513c`→`p453wb.DebugMenuVisibilityChecker` (m9844p = debug-menu gate)
- (batch 23 tail fixes counted there). SESSION TOTAL: 600 renames.

## Batch 25  -  Dressroom self-tag sweep, page 2 (29 renames)
- p075d7.C3017z→SaveImageMaker (AOD image saver: masked.jpg, port/land/square XML v1.0.3)
- p080dc.C3088p→LockScreenComponent
- p083dg: RequestExtraScaleModeUseCase, UpdateEditModeUseCase, UpdateEditorVisibilityUseCase,
  UpdateIdleStateUseCase, UpdateIgnoreTouchViewUseCase, UpdateMovingDataUseCase,
  UpdatePendingSelectedDataUseCase, UpdatePreviewScrollUpHelperDataUseCase, UpdateResizingDataUseCase,
  UpdateSavableUseCase, UpdateSelectedDataUseCase, UpdateSelectedMenuItemDataUseCase,
  UpdateTouchedDataUseCase, UpdateWallpaperColorOverrideAreasUseCase, DefinedPanelSizeUseCase
- eg: SaveMotionClockThumbnailBitmapUseCase, UpdateMotionClockThumbnailDataUseCase
- p108ep: InformativeClockPreviewItem, InformativeClockPreviewViewModel
- p127fg: IsIconThemeAppliedUseCase, IsThemeAppliedUseCase, IsThemeUseDarkUseCase
- p128fh.C3500e→UpdateBnRSubFrameDataSolution; p129fl.C3535a→ViewHolderItem; p130fp.C3548a→LockStarItemViewModel
- gp: CustomClockItemViewModel, CustomClockRecyclerViewAdapter
- p158hf.C3830a→ConvertCachedWallpaperColorUseCase; p164hp: NotificationPreviewItem, NotificationPreviewViewModel
- p088dp.C3170d→ContactInfoEditPanel
Skipped: p123fc.C3469c… mega DI aggregate (125 KB, no tag) + Factory synthetics.
SESSION TOTAL: 629 renames.

## Batch 26  -  ViewModel/Controller/Store sweep + dup fixes (21 renames)
- p035bp.getObservableClockDataUseCase(dup)→WorldClockCityListViewModel
- p045c9: GenAiWallpaperCategoryViewModel, GenAiDialogFragment, TextViewAddAdapter,
  GenAiWallpaperPromptViewModel, OptionAdapter
- p043c7: ImagePreviewController (C1623b0), DownloadableImagePreviewController (was dup C1645v),
  ImageViewerController, UneditableImagePreviewController
- p014aj.C0144c→ModuleClockPreviewController; p029b7.C1492a→GraphicalPreviewController,
  p029b7.LOCK→LockScreenGraphicalPreviewController; p038c2.C1573d→ResponsivePaneController
- p102eb.C3297j→ImageInfo; fi.C3506f→LockScreenDataNotifier
SESSION TOTAL: 650 renames.

## Batch 27  -  Notifier/Mediator/Receiver/Behavior sweep (21 renames)
- fi: LockScreenDataStoreImpl (C3505e), LockScreenRemoteItemStoreImpl (C3517q)
- p161hi.C3849a→PackageBroadcastReceiver; p091e0.C3178a→FloatingPaneViewModel
- p113f2.C3393a→ResponsivePaneCallbackNotifier; p516z1.C7977a→FloatingPaneCallbackNotifier
- p467x1: BottomBehavior, FloatingBehavior, SideBehavior (+AbstractC7600b base skipped)
- ad.C0109v→EditorMultiItemMediator; p043c7: EditorProfilePagerEventMediator,
  EditorZoomButtonContainerMediator; p045c9.C1672h→EditBaseActivity(helper)
- p098e7: DesignPreviewController, EditorWallpaperContentMediator
- ga: FavoriteListManager, ProfileActionMediator; p151h7: StylePreviewController(+Base dup fix),
  EditorPreviewVisibilityMediator; p154ha.C3812b→ProfileActionProviderHandler
- io.C3993a→EditorProgressMediator; p174i7.RunnableC3918e→ViewHolderItem
- p173i6.C3904a→WallpaperBackupFileHandler (backup image/multipack/gif/video/live/theme handlers)
SESSION TOTAL: 671 renames.

## Batch 28  -  library data sources + editor panels (17 renames, all verified by decl/strings)
- p072d4 (wallpaper library DataSources, extend AbstractC2962i): C2958e→DataSourceThemeStudio,
  AbstractC2963j→CategoryDataSourceBase, AbstractC2964k→DataSourceAnimals, C2966m→DataSourceDownloaded,
  C2967n→DataSourceTiltClockWallpaper (flip-cover emoji), C2974u→DataSourceGallery,
  C2965l→DataSourceColorWallpapers, C2969p→DataSourceFlipCoverInformative
- p121fa.C3465a→WallpaperProfileActionHandler; p102eb.C3291d→BitmapUtils
- p098e7: AbstractC3245p→EditorPanelBase, C3215d→CoverDesignPanel, C3223f1→MainDesignPanel,
  C3227h→GraphicalDesignPanel
- p333q7.C6294d0→MultiplePreviewPanelController; p043c7.C1642s→ImageFilterPanel; p114f3.C3395b→GeneratedWallpaperHandler
SESSION TOTAL: 688 renames.

## Verification note (batches 21–28)
All ~140 class renames from batches 21–28 were re-fetched and checked: every new name either appears
as a real self-tag in the class source (`field = "Name"`, `return "Name"`, or its own log tag) or is a
deliberate semantic pick made after reading the class declaration/strings (VMs, panels, factories).
Wrong auto-picks caught & fixed during the run: getFocusedWallpaperSceneUseCase→GetFocusedWallpaperSceneUseCase,
updateResumeUseCase→SyncShortcutAppListUseCase, coroutineScope→SubClockItemListRepositoryImpl,
orientationRepository→LockThumbnailScreenSizeRepositoryImpl, LOCK→LockScreenGraphicalPreviewController,
ImagePreviewController dup→DownloadableImagePreviewController, p072d4 first-literal junk
(Category/Animals/BucketDataCache/TiltClockWallpaperService)→proper DataSource* names.

## Batch 29  -  Dressroom final sweep (7 renames)
- p260mf: C5433a2→ResetClockPositionAndApplyScaleUseCase, C5448d2→RestoreClockDataUseCase,
  C5519v1→MoveClockPositionUseCase, C5528x2→UpdateScaledClockBoundaryUseCase
- p323po.C6209y→ItemEditorController; p348qp.C6403e→ClockPositionSuggestionController
- p289o9.C5767g→DownloadCoordinator
- p434v9/p435vb/p316p8 checked: only gesture-listener/synthetic classes + already-named
  (LoadPresetDataUseCase, ComponentMediator)  -  no self-tags, skipped per convention.
DRESSROOM SWEEP COMPLETE. SESSION TOTAL: 695 renames.

## Batch 30  -  SystemUI 16.4006 AOD pass (0 class renames needed; findings-only)
SystemUI AOD code is AOSP-named (doze.*, dreams.*, keyguardimage.*, plugins.aod.*); only inner
lambda classes are C-named (skipped per convention). No renames applied. Findings logged in
HIDDEN_FEATURES.md §SystemUI: AODIntentService/KeyguardImageProvider exports, low-light dream,
Knox LSO overlay, SettingsHelper AOD keys. SESSION TOTAL: 695 renames.

## 2026-11-24 - Clock animation contract pass (Codex continuation)

Recorded 62 successful alias operations for the animation pass, including the
58 earlier animation aliases and four new palette callback aliases after the
limit reset. Three failed synthetic-capture field attempts are excluded.
The [canonical table](AOD_9_ALIASES.md#animation-engine-contract-pass-2026-11-24)
records every successful operation. This is separate from the session total
above; no combined unique-symbol count has been inferred.

Verified source, callback xrefs and targeted smali for font-axis timing,
editor physics, palette finalization, blur-alpha writers and page completion.
The [engine audit](CLOCK_ANIMATION_ENGINES.md) records which conclusions are
confirmed, version-specific or still awaiting proof. Existing Mistral aliases
and research notes are retained. No production behavior changes in this pass.
