# One UI 9 AOD research aliases

Inspected and renamed through JADX MCP on 2026-11-10. Reference APK:
`com.samsung.android.app.aodservice` 8.9.66.1, version code 896601000.

These are analysis aliases, not runtime hook names. The APK and module hooks were
not changed. Original identifiers below apply only to this reference build.
Keep signature-based resolution in the module.

## Classes

| Original | Alias | Evidence |
| --- | --- | --- |
| `aod.tx0` | `aod.CombinedBlurStrategy` | Native log label; combines text/weather paths and submits SemBlurInfo |
| `aod.cq0` | `aod.ClockBlurManager` | Constructor log label; owns text alpha, weather data and blur animators |
| `aod.qb0` | `aod.ClockBlurColorCurvePolicy` | Selects and applies the six color-curve parameters |
| `aod.dq0` | `aod.ClockBlurStateMachine` | Native log label; computes blockers, dispatches state and retains history |
| `aod.ec0` | `aod.ClockBlurStateInfo` | Native toString label; visibility, blocker set and timestamp |

`aod.qn1` also contains Android navigation and other unrelated helpers. Only its
font methods were renamed; a font-specific class alias would be misleading.

## Methods

The middle column is the name the MCP accepted before renaming. Raw names are
included because JADX's generated names can change with its configuration.

| Original owner | Raw / displayed method | Alias |
| --- | --- | --- |
| tx0 | b / m11664b | applyBlur |
| tx0 | c / m11665c | clearBlur |
| tx0 | d / m11666d | resolveBlurHost |
| tx0 | e / m11667e | getBlurHostOrigin |
| tx0 | f / m11668f | getTextOrigin |
| tx0 | g / m11669g | buildWeatherBlurPath |
| tx0 | h / m11670h | animateBlurTransition |
| cq0 | a / m2926a | applyAnimatedTextAlpha |
| cq0 | b / m2927b | applyTextBlur |
| cq0 | c / m2928c | applyTextBlurWithRetry |
| cq0 | d / m2929d | collectBlurTextViews |
| cq0 | e / m2930e | isBlurEffectEnabled |
| cq0 | g / m2931g | isUnsupportedBlurClock |
| cq0 | h / m2932h | resolveBlurColorMetrics |
| cq0 | i / m2933i | removeClockShownListener |
| cq0 | j / m2934j | setWeatherData |
| cq0 | k / m2935k | startBlurAnimation |
| cq0 | l / m2936l | startDashboardBlurAnimation |
| qb0 | a / m9760a | applyColorCurve |
| qb0 | b / m9761b | selectColorCurve |
| qn1 | C / m9916C | buildClockFontAxes |
| qn1 | D / m9917D | buildDigitFontAxes |
| qn1 | E / m9918E | getClockHeightAxisValues |
| qn1 | J / m9923J | resolveClockCornerAxis |
| qn1 | K / m9924K | resolveClockWeightAxis |
| qn1 | s / m9970s | formatFontAxisSettings |
| dq0 | a / m3665a | collectBlockReasons |
| dq0 | b / m3666b | dumpBlurState |
| dq0 | c / m3667c | setStretchAnimating |
| dq0 | d / m3668d | updateBlurState |
| ec0 | a / m3922a | hasPersistentBlockReason |
| ec0 | b / m3923b | describeBlurState |
| ClockBlurController | a / m15786a | observeStretchAnimation |
| ClockBlurController | b / m15787b | applyBlurState |
| ClockBlurController | c / m15788c | refreshClockBlur |
| ClockBlurController | d / m15789d | setFaceWidgetTransitioning |
| ClockBlurController | e / m15790e | setSggActive |
| ClockBlurController | f / m15791f | unregisterClockCallback |

The controller's full package is
`com.samsung.android.app.facewidget.pages.clock.blur`.

## Now Bar stacking, input, and clock-page aliases

These aliases were verified on the reference AOD in JADX. The stack methods
separate item identity, slot geometry, touch tracking, and drawable opacity.
`FaceWidgetClockPage.setClockDozing` forwards doze to the live clock and refreshes
its calendar image.

| DEX owner | DEX / JADX name before alias | Kind | Alias |
| --- | --- | --- | --- |
| NowBarCardStackView | `z` / `m16064z` | method | `updateCardVisibilityByExpandState` |
| NowBarCardStackView | `m` / `m16041m` | method | `isSameNowBarItem` |
| NowBarCardStackView | `y` / `m16042y` | method | `updateCardBlurAlpha` |
| NowBarCardStackView | `d` / `m16044d` | method | `dismissTopCardBySwipe` |
| NowBarCardStackView | `e` / `m16045e` | method | `resolveCardStackDepth` |
| NowBarCardStackView | `f` / `m16046f` | method | `getInitialCardScale` |
| NowBarCardStackView | `g` / `m16047g` | method | `getStackSlotBottomOffset` |
| NowBarCardStackView | `i` / `m16049i` | method | `getItemAt` |
| NowBarCardStackView | `j` / `m16050j` | method | `getTrackedPointerPosition` |
| NowBarCardStackView | `n` / `m16053n` | method | `isTopCardSettingsOpen` |
| NowBarCardStackView | `o` / `m16054o` | method | `performCardHapticFeedback` |
| NowBarCardStackView | `p` / `m16055p` | method | `startCardDrawAnimations` |
| NowBarCardStackView | `q` / `m16056q` | method | `refreshAllCards` |
| NowBarCardStackView | `r` / `m16057r` | method | `refreshStackDimensions` |
| NowBarCardStackView | `s` / `m16058s` | method | `resetCardPositions` |
| NowBarCardStackView | `u` / `m16060u` | method | `dispatchNowBarVisibilityChange` |
| NowBarCardStackView | `v` / `m16061v` | method | `showStackCardContents` |
| NowBarCardStackView | `w` / `m16062w` | method | `beginStackTouchTracking` |
| NowBarCardStackView | `x` / `m16063x` | method | `stopCardDrawAnimations` |
| FaceWidgetClockPage | `D` / `m15770D` | method | `onMediaExpandClockScaleUpdated` |
| FaceWidgetClockPage | `G` / `m15773G` | method | `updateClockMaxWidth` |
| FaceWidgetClockPage | `K` / `m15777K` | method | `setClockDozing` |
| NowBarCardStackView | `M` / `f41328M` | field | `currentViewMode` |
| NowBarCardStackView | `P` / `f41331P` | field | `sleeping` |
| NowBarCardStackView | `D` / `f41319D` | field | `trackedPointerId` |
| NowBarCardStackView | `d0` / `f41342d0` | field | `screenWidthPx` |
| NowBarCardStackView | `e0` / `f41343e0` | field | `screenHeightPx` |
| NowBarCardStackView | `w` / `f41368w` | field | `swipeVelocityTracker` |

## State-machine fields

All belong to raw `aod.dq0`. Meanings are established by its blocker collection
and native dump labels. SGG is left as Samsung's acronym, without guessing what
it expands to.

| Raw / displayed field | Alias |
| --- | --- |
| c / f7199c | stretchAnimating |
| d / f7200d | editModeAnimating |
| e / f7201e | alphaAnimating |
| f / f7202f | screenOnOffAnimating |
| g / f7203g | mediaExpandedInLandscape |
| h / f7204h | mediaExpandAnimating |
| i / f7205i | dozeActive |
| j / f7206j | dashboardActive |
| k / f7207k | quickPanelExpanded |
| l / f7208l | blurEffectEnabled |
| m / f7209m | containerVisible |
| n / f7210n | primaryBouncerVisible |
| o / f7211o | profileTransitioning |
| p / f7212p | sggActive |
| q / f7213q | faceWidgetTransitioning |
| r / f7214r | overlaySwiping |
| s / f7215s | shortcutPreviewSwiping |
| t / f7216t | defaultPage |
| u / f7217u | pinchZoomActive |
| v / f7218v | requestStartActivity |
| w / f7219w | unlockAlphaActive |

## Findings and follow-up candidates

- Blur has 21 independent blocker inputs, not one animation flag. Stretch,
  screen transitions, media expansion, gestures and profile changes can each
  keep it hidden. `BLUR_EFFECT_DISABLED` and `SGG_ACTIVE` are the persistent
  blockers recognized by StateInfo; the controller switches the underlying blur
  effect off for these. Other blockers remove the current blur representation.
  A forced refresh must respect these distinctions.
- Custom clock colors use inverted lightness and the color's saturation as the
  curve-selection inputs. Ordinary runtime clocks obtain wallpaper metrics;
  editor auto-color uses the clock's selected color. Radius alone cannot explain
  the apparent strength across those cases.
- Failed glyph/weather readiness retries five times, 8 ms apart. This branch
  sets foreground alpha to 127 and does not explicitly clear the previous blur
  host when retries run out. The posted retry has no cancellation or generation
  token of its own. Stale output after font replacement or detach is a candidate
  to investigate, not a reproduced new bug.
- `animateBlurTransition` sets a 1000 ms duration even though its log says
  300 ms. The manager has separate 300 ms ordinary and 500 ms dashboard
  animations. Use the executable duration, not that log message, when tracing.
- The text paths and weather icon are combined on a separate root blur host.
  Editor origins use location-on-screen; runtime origins use global visible
  rectangles. Gesture clipping can therefore affect origin calculations. Check
  this before changing depth geometry or replacing coordinates globally.
- The native font helpers resolve -1 weight/corner values to font defaults.
  Per-digit height values are converted through the font-type-specific config
  before being emitted as HEGT. An imported font's axis support still needs to
  come from its own fvar table.

64 aliases were applied: five classes, 38 methods and 21 fields. Representative
strategy and state-machine sources were fetched again to verify the aliases are
visible to MCP. This document records the mapping independently of the running
JADX session; it is not an exported JADX project or a runtime validation report.

## Shared retry guard (1.1.13)

`ClockBlurRetryBinding` follows `setClockTextBlur` to its manager, verifies the
retry call, and follows the task constructor back to `run`. The manager/strategy,
glyph collection and holder-to-clock fields are resolved from types and DEX
reads. No reference-build obfuscated names were added to the binding.

Each fresh blur submission starts a request generation. Queued native retries
and deferred post-draw submissions must still belong to that generation. New
glyph collection, blur removal/disable and detach retire it. Detached, hidden or
replaced managers cannot resume a retry. Current retries retain Samsung's timing
and eligibility checks; the guard does not clear the blur host or force it on.

The retry Runnable is R8-merged with a NestedScrollView callback. Only instances
from the verified clock-manager constructor receive tickets. Other constructors
and their run branches are untouched.

Static call-site checks and actual Android binding resolution passed against:

- One UI 9 AOD 8.9.66.1 (reference and installed provider).
- The supplied One UI 9 Clock Style (`ClockPack_v80.apk`).
- The P613 One UI 8.5 Clock Style.
- One UI 8.5 AOD 8.9.36.23 and 8.9.32.95.

The JVM generation test covers replacement, independent managers, cancellation,
reattach and retired owners. Binding success does not establish visual behavior
on every provider/device combination. The native retry-exhaustion fallback is
unchanged; changing that requires evidence about the desired host/foreground
state rather than clearing a shared blur surface speculatively.

## Glyph readiness pass (1.1.14)

23 more aliases were applied while tracing the path getter and measurement code:

| Original owner | Raw / displayed member | Alias |
| --- | --- | --- |
| aod.ek8 | class | BlurPathSource |
| aod.lv4 | class | GlyphMeasureInfo |
| lv4 | a / f18735a | measurementPaint |
| lv4 | b / f18736b | originalBounds |
| lv4 | c / f18737c | trimmedBounds |
| lv4 | d / f18738d | textView |
| NonePaddingTextView | R / f42895R | glyphDrawReady |
| NonePaddingTextView | S / f42896S | blurPathMode |
| NonePaddingTextView | U / f42898U | drawnGlyphPath |
| NonePaddingTextView | T / f42897T | drawnGlyphText |
| NonePaddingTextView | O / f42892O | glyphDrawOffsetX |
| NonePaddingTextView | P / f42893P | glyphDrawOffsetY |
| NonePaddingTextView | Q / f42894Q | pathTextColor |
| NonePaddingTextView | J / f42887J | alignmentSampleReady |
| NonePaddingTextView | K / f42888K | alignmentSampleText |
| NonePaddingTextView | L / f42889L | alignmentSampleLength |
| NonePaddingTextView | I / f42886I | preciseDigitMeasurement |
| NonePaddingTextView | H / f42885H | preciseMeasureFontType |
| NonePaddingTextView | M / f42890M | baseTypeface |
| NonePaddingTextView | q / f42901q | cachedDrawBounds |
| NonePaddingTextView | u / f42905u | currentGlyphMeasureInfo |
| NonePaddingTextView | v / f42906v | digitAlignmentMeasureInfo |
| NonePaddingTextView | w / f42907w | liveClockMeasureInfo |
| NonePaddingTextView | a / m16849a | calculateTextBounds |
| NonePaddingTextView | d / m16852d | measureDigitAlignmentBounds |
| AbsClockView | C / m16899C | applyFixedTypeface |
| ClockNonePaddingTextView | h / m16830h | setFixedClockFont |
| ClockNonePaddingTextView | i / m16831i | updateClockTypeface |

Both text-view classes are in `com.samsung.android.uniform.widget`.

`GlyphOutlineBinding` replaces readiness reads by hardcoded R/S field names.
It identifies cached-path mode through the native color setter, then identifies
draw readiness through the path getter's boolean reads, excluding drawing modes
also read by onDraw. Resolved Field objects are reused during submission.

The getter's first-draw gate applies to live paths as well as cached paths. Live
paths also need current layout offsets; only cached outlines need rebuilding for
ordinary dirty state. The readiness check now makes these distinctions.

AOD 8.9.32.95 inherits TextView's color setter and has no cached-path flag. Its
getter still checks a draw-ready field before computing the live outline. This
variant uses the same shared binding with cached mode absent. Unknown layouts
fall back to native readiness without disabling the retry-generation guard.

The reference build's precise digit measurement has a separate defect confirmed
in smali: it creates an all-digit outline but measures the earlier zero outline
again when selecting its alignment sample. This makes the comparison select zero
regardless of the other digits' heights. No measurement hook was added: changing
those bounds needs visual checks with fonts whose digit extents differ.

## Surface blur appearance

| Original | Alias |
| --- | --- |
| presentation.a / AbstractC1280a | presentation.BaseNowBarCardView |
| aod.e70 | aod.NativeSurfaceBlurUtils |
| e70.a / m3873a | setBlurClipPath |
| e70.c / m3875c | applyCustomWidgetBlur |
| e70.d / m3876d | applyPresetBlur |
| e70.e / m3877e | setBlurGradient |
| aod.ui6 | aod.SamsungFeatureReader |
| ui6.a / m11945a | isFeatureEnabled |

The shared framework submission is `View.semSetBlurInfo(SemBlurInfo)`. Widget
custom attributes and Now Bar presets reach this same method. Appearance edits
copy the native info and color curve, keeping its clip, corners, gradient and
backend. Null submissions still clear blur. The wallpaper-wide status host is
excluded so it cannot blur fullscreen artwork again.

Extras stores appearance overrides per screen. Editor previews stay in process;
Apply uses the existing Extras transaction. Widget and pill refreshes resubmit
native inputs through depth and occlusion handling. Clock inputs are never
replayed: their paths hold old screen coordinates. Clock appearance changes
instead request a fresh native update after drawing. Unchanged settings
notifications do not refresh anything, and disabled customization leaves the
native info object intact.

Samsung's native gate reads the boolean floating feature
`SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_3D_SURFACE_TRANSITION_FLAG`. On the tested
S26 Ultra it supports native blur while Android's standard window blur flag is
false. Availability accepts either backend, while respecting explicit
`disable_window_blurs` and Reduce transparency. Canvas blur is only exposed on
restricted devices after an actual canvas submission.

Clock blur hosts must stay measured when cleared: use INVISIBLE, not GONE.
The native strategy resolves the host and immediately samples its visible
coordinates; removing it from layout can leave a displaced matte until the
next interaction. ClockBlurAlignment tracks each native clock's geometry and
requests a post-draw rebuild when it changes, respecting that clock manager's
blur enable state. A translated clock is valid; a global "any clock active"
check cannot decide whether another clock's host should be visible.

The manager also skips blur submissions while its blur-on animator runs. The
alignment watch alone cannot handle this: it has already consumed the geometry
change when native code ignores the rebuild. ClockBlurRetryBinding identifies
the ValueAnimator read by the native submit body. One completion listener
requests a fresh post-draw rebuild; repeated skips reuse that listener, and
clock removal unregisters it. No timer or continuous redraw loop is used.

ClockBlurContinuity completes native effect handoffs immediately, preserving
their final alpha, weather-icon updates and completion listeners. The manager's
300 ms reveal is found from ValueAnimator.ofFloat plus Drawable.setAlpha; the
separate strategy transition matches void(int, ImageView) and its ValueAnimator
field. The latter resets glyph and background alpha over one second on clock
changes, independently of the manager reveal. Completing both replaces the
earlier existing-drawable-only fade suppression.
It retains the native blur through a stretch-only blocker and updates the
matte after drawing each stretch frame. Mixed reasons, screen/AOD transitions,
new drawable ownership and normal clear operations retain native behavior.

Media return cancels pending compact glyph work without explicitly discarding
the live outline. The post-draw path replaces it with current geometry. This
removes the module's own extra clear before the native return transaction.

Motion continuity follow-up:
- AbsClockView.s(float, boolean): applyClockBlurAtScale.
- AbsClockView.t(boolean): collectAndEnableClockBlur.
- ClockBlurController.g(): updateDozeBlurBlocker.
- ClockBlurController.A: clockBlurScale.
- ClockBlurManager.j/k/l/f: dashboardReturnAnimator, pendingBlurReveal,
  dashboardReturnPending, targetTextAlpha.
- ClockBlurStateInfo.a/b: visibilityState, activeBlockReasons.
- ClockBlurStateMachine.y: stateHistory.

The blur-enabled Boolean is read from removeBlur, not its setter. The DEX setter
can reuse the incoming argument instead of reading the field shown in decompiled
Java. Moving blur activation follows the controller's actual (float, boolean)
call. No aliases above are used as production bindings.

Seconds-view return uses the FaceWidgetTransition destination. The aliases are
FaceWidgetClockPage.t (configureClockPageTransition), qb2.i (attachTransitionTarget)
and qb2.h (finishClockPageTransition). On the reference phone, DASHBOARD_ACTIVE
cleared 26 ms before the end of a 500 ms return. The exception applies only to
its tracked lockscreen destination while that transition owns the view.

## Font animation and position

| Original DEX owner/member | JADX alias | Evidence |
| --- | --- | --- |
| `aod.nr0` | `ClockFontAxisAnimator` | Owns the animation set and its changing font-axis payload. |
| `nr0.d` | `canAnimateFontAxes` | Checks the animator and view conditions before a run. |
| `nr0.e` | `startAxisAnimation` | Builds and starts the animation for the requested axes. |
| `nr0.f` | `cancelAxisAnimation` | Cancels the current animation set. |
| `nr0.g` | `selectAnimatedAxes` | Selects weight and HEGT from the current font data and gates. |
| `aod.as1` | `DigitalClockBaseView` | Shared digital view, including date/weather, position reporting and blur. |
| `AbsClockView.K0` | `updateFontAxesForTransition` | Chooses the animated or direct font update in provider and page transitions. |
| `aod.vo1.E` | `heightRatioToFontAxis` | Converts the height multiplier using the font's axis configuration. |
| `aod.qn1.S` | `isAutoStretchShapeEligible` | Rejects small axis spread and excessive vertical column imbalance. |
| `aod.qn1.X` | `normalizeStretchHeightRatios` | Caps column imbalance or adjacent horizontal axes before rendering. |
| `KeyguardClockPositionManager.a` | `createPositionItemFromClockItem` | Reads position ratios, reference rectangle, scale and font heights. |
| `KeyguardClockPositionManager.c` | `getClockHorizontalPadding` | Combines gravity, reference width and horizontal margin ratio. |
| `KeyguardClockPositionManager.d` | `getClockMaxWidth` | Calculates width available to the clock. |
| `aod.fa6.a` | `measureStretchInScene` | Fits scene rectangles, converts to ratios, normalizes and adjusts the resulting bounds. |
| `aod.o97.a` | `fitStretchSlotsInRegion` | Chooses full, partial or default rectangles from the available region. |
| `aod.vi1.E` | `getFirstIntersectionHeight` | Returns the height of the first rectangle in the intersection. |
| `aod.mi1.f` | `filterBackwardTimeJump` | Holds the previous timestamp for small backward changes. |
| `aod.mi1.g` | `dispatchCurrentTime` | Updates the calendar, formats time and dispatches it to the view. |
| `aod.mi1.k` | `detachTimeUpdates` | Removes the receiver and seconds runnable. |

## Clock layer handoffs

| Original DEX owner/member | JADX alias | Evidence |
| --- | --- | --- |
| `aod.qb2.g` | `cancelClockPageTransition` | Removes the pre-draw listener and ends or cleans up the current transition. |
| `aod.qb2.j` | `removeTransitionPreDrawListener` | Unregisters the listener from the live holder observer. |
| `aod.qb2.k` | `captureNamedTransitionViews` | Recurses over the real hierarchy and saves views and geometry by transition name. |
| `FaceWidgetClockPage.A` | `computeFontCollapseFraction` | Clamps the maximum of doze and the effective media fraction. |
| `FaceWidgetClockPage.L` | `updateFontDozeAndMediaFraction` | Computes the current font transition fraction and passes it to the bound clock. |
| `aod.h9.i` | `getMainAodClockContainer` | Returns the AOD clock container from its view model. |

## AOD policy, blur debouncer, and page transition pass (2026-11-12)

21 classes and 45 methods and fields (66 aliases total) were verified and renamed in JADX MCP while
tracing clock creation, `mIsDressRoom` split behavior, wallpaperless DOZE, and
wake/doze transitions on the same reference build (`8.9.66.1`):

### Classes

| Original | Alias | Evidence |
| --- | --- | --- |
| `aod.qr0` | `aod.ClockInfoManager` | Registry lookup for `ClockInfo`, category layouts, and `isSupportTextBlurClock` |
| `aod.AbstractC0115a6` | `aod.AODPolicyFactory` | `"AODPolicyFactory"` log tag; builds `AODPolicy` and `AODUIPolicy` |
| `aod.C1042z5` | `aod.AODPolicy` | `"AODPolicy : ..."` `toString()` naming all 12 policy fields |
| `aod.C0191c9` | `aod.AODUIPolicy` | UI policy consumed by `AODUiController` and `AODClockViewModel` |
| `aod.C0155b9` | `aod.AODUIPolicyBuilder` | Builder creating `AODUIPolicy` in `makeUIPolicy` |
| `aod.C0700q1` | `aod.AODClockViewModel` | `"AODClockViewModel"` log tag; owns `AODClockContainer` and AOD clock timer/live clock state |
| `aod.C0330g1` | `aod.AODClockContainer` | Inflates and hosts standalone AOD `AbsClockView` when `mNeedClockContainer` is true |
| `aod.C0743r6` | `aod.AODSettingDataProvider` | `"AODSettingDataProvider"` log tag; resolves AOD/Lockscreen/LockStar clock items |
| `aod.jn1` | `aod.BlurBlockDebouncer` | `Handler.postDelayed` debouncer used by main and sub-launcher blur controllers |
| `aod.gl7` | `aod.SubLauncherClockBlurController` | `"SubLauncherClockBlurController[blur_sub]"` cover-screen blur controller |
| `aod.dc0` | `aod.BlurVisibilityState` | Enum `{ VISIBLE, HIDDEN }` used in `ClockBlurStateInfo` |
| `aod.ob0` | `aod.BlurThresholds` | `"BlurThresholds(dark=30, mid=64, light=76)"` |
| `aod.cc0` | `aod.ClockBlurEnabledObservable` | Holds `MutableLiveData<Boolean>` updated by `ClockBlurController` |
| `aod.vp0` | `aod.ClockBlurExtensionCallbackImpl` | Implements `ClockBlurExtensionCallback` for lockscreen and sub-launcher controllers |
| `aod.sp0` | `aod.ClockBlurControllerCallback` | `Function1` observers attached by `ClockBlurController` (`dashboardActive`, `screenOnOffAnimating`, bouncer, quick panel, etc.) |
| `aod.wp0` | `aod.ClockBlurStateCollector` | Coroutine collector syncing `blurEffectEnabled` and `profileTransitioning` |
| `aod.xt0` | `aod.ClockStretchableRepositoryImpl` | `"ClockStretchableRepositoryImpl[stretch_ui]"` |
| `aod.ht0` | `aod.ClockPositionRepositoryImpl` | `"ClockPositionRepositoryImpl[stretch_ui]"` |
| `aod.ts0` | `aod.ClockPackSettingsUtils` | `"ClockPackSettingsUtils"` log tag; roaming/dual-clock helpers |
| `aod.f82` | `aod.FaceWidgetClockPageObserver` | Lifecycle `Observer` lambdas on `FaceWidgetClockPage` |
| `aod.C0559m8` | `aod.AodToLockFontWeightTransitionHelper` | Compares AOD vs. lockscreen font weight and hides matching `fontTransitionNames` during morph |

### Methods and fields

| Original owner | Raw / displayed member | Kind | Alias |
| --- | --- | --- | --- |
| `AbsClockView` | `L0` / `f43041L0` | field | `mIsDressRoom` |
| `AbsClockView` | `s0` / `f43098s0` | field | `mIsDozing` |
| `AbsClockView` | `u` / `f43101u` | field | `mIsBlurEffectEnabled` |
| `AbsClockView` | `y` / `f43109y` | field | `mIsBlurActive` |
| `AbsClockView` | `E0` / `m16902E0` | method | `updateBlurTextAlpha` |
| `qr0` (`ClockInfoManager`) | `j` / `m10034j` | method | `getInstance` |
| `qr0` (`ClockInfoManager`) | `d` / `m10044d` | method | `getClockInfo` |
| `qr0` (`ClockInfoManager`) | `r` / `m10040r` | method | `isSupportTextBlurClock` |
| `AbstractC0115a6` (`AODPolicyFactory`) | `d` / `m1155d` | method | `makeAODPolicy` |
| `AbstractC0115a6` (`AODPolicyFactory`) | `f` / `m1157f` | method | `makeUIPolicy` |
| `C0191c9` / `C0155b9` | `C` (`f4700C`) / `F` (`f3236F`) | field | `mNeedClockContainer` |
| `C1042z5` (`AODPolicy`) | `a..l` / `f36610a..f36621l` | fields | `mClockInfo`, `mPaletteColorIndex`, `mCustomColor`, `mIsAdaptiveColorMode`, `mIsSeamlessEnabled`, `mIsWonderLandAmbientWallpaperEnabled`, `mIsLiveClockEnabled`, `mIsSelfMoveEnabled`, `mIsDelayLiveClockEnabled`, `mIsTapToShowEnabled`, `mIsShowForNewNotiEnabled`, `mIsSleepStateEnabled` |
| `jn1` (`BlurBlockDebouncer`) | `a` (`m6660a`) / `b` (`m6661b`) | methods | `cancel` / `schedule` |
| `ly0` | `t` / `m7731t` | method | `isDirectBootLocked` |
| `cj1` | `S` / `m2809S` | method | `isFullScreenAODSupported` |
| `SettingsHelper` | `j` / `m15229j` | method | `isAODShowLockscreenWallpaper` |
| `rv2` | `w` / `m10714w` | method | `calculateLuminanceLStar` |
| `rv2` | `G` / `m10675G` | method | `isLightColorLStar` |
| `rv2` | `H` / `m10676H` | method | `isSingleThemeImageType` |
| `rv2` | `I` / `m10677I` | method | `isAODThemeClockSelected` |
| `rv2` | `s` / `m10710s` | method | `getAODSelectedThemeClock` |
| `rv2` | `y` / `m10716y` | method | `getSingleThemeContentType` |
| `ts0` (`ClockPackSettingsUtils`) | `j` / `m11642j` | method | `isRoamingState` |
| `FaceWidgetClockRoamManager` | `e` / `m15785e` | method | `shouldShowDualClock` |
| `FaceWidgetController` | `m` / `m15460m` | method | `isLockAmbientEnabled` |
| `FaceWidgetController` | `o` / `m15462o` | method | `requestAODState` |
| `FaceWidgetViewModel` | `t1` / `f40188t1` | field | `mIsAODThemeClock` |
| `FaceWidgetViewModel` | `u` / `m15491u` | method | `isLockAmbientEnabled` |
| `FaceWidgetViewModel` | `M` / `m15475M` | method | `updatePageState` |
| `FaceWidgetViewModel` | `v` / `m15492v` | method | `onClockTransitionEnded` |
| `FaceWidgetViewModel` | `y` / `m15495y` | method | `setClipLayoutVisibility` |
| `FaceWidgetViewModel` | `L` / `m15474L` | method | `updateLockClockScale` |

See [clock layer handoffs](CLOCK_LAYER_HANDOFFS.md) for the source and target
lifecycles and the matching DEX anchors from the other sampled providers.

## Widget, complication host, and cover-screen pass (2026-11-12)

25 single-purpose classes and 8 unambiguous methods on multi-purpose utility classes
(33 aliases total; `aod.cj1.m2809S` was already renamed to `isFullScreenAODSupported`
above) were verified and renamed in JADX MCP on the same reference build
(`8.9.66.1`). Note: `aod.tb9`, `aod.nn1`, and `aod.cj1` are merged multi-purpose
utility classes and retain their raw class names.

### Classes

| Original | Alias | Evidence |
| --- | --- | --- |
| `aod.i11` | `aod.ComplicationWidgetHostManagerImpl` | Manages complication host listeners and calls `semChangeHostIds` between `2304` and `2346` |
| `aod.qu6` | `aod.SharedAppWidgetFacade` | Shared widget facade observing `complication_widget_host_change` and lockscreen enabled lists |
| `aod.ad3` | `aod.InformativeSharedWidgetFacade` | Informative-tile shared facade (`getHostViewByProvider`, `sendAppWidgetInitOption`) |
| `aod.gc3` | `aod.InformativeBriefWidgetHostManager` | Host manager for informative widgets (`2346`, `semHostType=4`) and preview scaling |
| `aod.e11` | `aod.ComplicationWidgetDataProvider` | Provides complication widget data backed by `ComplicationWidgetHost` (`2304`) |
| `aod.fa4` | `aod.LockscreenTilesDataController` | Controls lockscreen tile/complication data binding |
| `aod.cd1` | `aod.CoverWidgetDataProviderImpl` | Cover-screen widget data provider backed by host `1911` |
| `aod.uz0` | `aod.ComplicationAppWidgetManager` | Wraps `AppWidgetManager` operations for complication widgets |
| `aod.f11` | `aod.ComplicationWidgetHost` | Complication `AppWidgetHost` bound to `2304` (`c64.f4601a`) |
| `aod.tz0` | `aod.ComplicationAppWidgetHost` | Alternate complication host config using `fy0.f10829a` (`2304`) |
| `aod.rc3` | `aod.InformativeHost` | Informative `AppWidgetHost` (`2346`, `semHostType=4`) |
| `aod.pa1` | `aod.CoverAppWidgetHost` | Cover-screen `AppWidgetHost` (`1911`, `semWidgetStyle=1`) |
| `aod.gl6` | `aod.AbsTrackedAppWidgetHost` | Base tracked `AppWidgetHost` handling view inflation and listener tracking |
| `aod.xl8` | `aod.TileFacadeManager` | Coordinates tile facades (`SharedAppWidgetFacade` / `InformativeSharedWidgetFacade`) |
| `aod.p01` | `aod.ComplicationRealPreviewView` | Live scaled preview renderer (`WidgetSizeSpec`, light/dark fallback colors) |
| `aod.wa1` | `aod.CoverGlanceAppWidgetHostView` | Cover Glance `AppWidgetHostView` applying inner 3rd-party rounding |
| `aod.i64` | `aod.LockAndAodSharedWidgetListManager` | Manages shared Lockscreen/AOD enabled widget list |
| `aod.h64` | `aod.LockAndAodSharedWidgetDataManager` | Manages shared Lockscreen/AOD widget metadata |
| `aod.i93` | `aod.IBriefWidgetHostManager` | Base host-manager interface checking owner unlock (`UserHandle(0)`) before listening |
| `aod.sd0` | `aod.IBriefWidgetManager` | Base brief-widget manager interface implemented by `BriefWidgetManagerImpl` / cover manager |
| `aod.ae0` | `aod.IBriefWidgetViewProvider` | Interface providing brief-widget views |
| `aod.qz5` | `aod.AppWidgetHostReflector` | Reflection helper calling `AppWidgetHost.setListener(widgetId, hostView)` |
| `aod.rz5` | `aod.AppWidgetHostViewReflector` | Reflection helper for `AppWidgetHostView` |
| `aod.b20` | `aod.AppWidgetProviderInfoExt` | `"GWT:ProviderInfo"` helpers inspecting `sem*AppWidgetInfo` `"widgetSize"` |
| `aod.hn7` | `aod.SubLauncherClockController` | Sub-launcher / cover clock + widget coordinator |

### Methods

| Original owner | Raw / displayed member | Kind | Alias |
| --- | --- | --- | --- |
| `aod.tb9` | `m11364l` | method | `applyTraverseWidgetColor` |
| `aod.tb9` | `m11354a` | method | `applyMaxFontScale` |
| `aod.nn1` | `m8537N` | method | `resolveWidgetRoundTargetView` |
| `aod.nn1` | `m8564h0` | method | `setSemOnContentAppliedListener` |
| `aod.nn1` | `m8540Q` | method | `getMultipleTileSize` |
| `aod.cj1` | `m2800J` | method | `hasCoverScreen` |
| `aod.cj1` | `m2805O` | method | `isMultiFoldMainDisplay` |
| `aod.cj1` | `m2803M` | method | `isMainDisplayWithoutCover` |

See [the One UI widget deep dive](ONE_UI_WIDGET_DEEP_DIVE.md) for the full
widget/complication creation, multi-host sharing, `SemBlurInfo` curve tables,
corner-rounding rules, and AOD/edit/accessibility overrides.

## Wallpaper region, stretch geometry, and depth-adjacent pass (2026-11-12)

10 single-purpose classes were verified and renamed in JADX MCP on the same
reference build (`8.9.66.1`). Note: `fa6.a -> measureStretchInScene` and
`o97.a -> fitStretchSlotsInRegion` (plus `vi1.E -> getFirstIntersectionHeight`)
were already listed in the `Methods` / stretch tables above; this pass completes
the class-level renames for `aod.fa6` and `aod.o97` alongside the wallpaper
region, debug overlay, and property reflectors. Unrenamed R8-merged / companion
owners (`aod.w06`, `aod.tc6`, `aod.ui1`, `aod.k54`) retain their raw names.

### Classes

| Original | Alias | Evidence |
| --- | --- | --- |
| `aod.d26` | `aod.WallpaperManagerReflector` | Reflects `android.app.WallpaperManager`; `m3126C` calls `semGetWallpaperBackgroundRegion(which, userId, sceneId, rotation)`; `SWP_TYPE_*`, `FLAG_DISPLAY_*`, `FLAG_MODE_MASK` |
| `aod.g59` | `aod.WallpaperRegionRepositoryImpl` | Region repository (`"WallpaperRegionRepositoryImpl_" + category.name() + "[stretch_ui]"`) |
| `aod.k49` | `aod.WallpaperBgRegionProvider` | `"WallpaperBgRegionProvider[clock_position]"`; implements `aod.a02` (`DumpManager`); logs `REGION_SIZE_MISMATCH[...]` in `m6814d` |
| `aod.vu2` | `aod.GetWallpaperSceneIdUseCase` | Calls `WallpaperManager.semGetWallpaperSceneId(int, int)` |
| `aod.bt2` | `aod.GetClockMovableAreaRegionUseCase` | Subtracts movable area, scaled widget container (`kp0.mo7069e() && kp0.mo7070f()`), and shortcut bounds via `Region.Op.DIFFERENCE` |
| `aod.fa6` | `aod.RunStretchMeasureProcessUseCase` | Coordinates `measureStretchInScene` (`fa6.a`) against background region |
| `aod.o97` | `aod.StretchMeasureProcessRunner` | Runs `fitStretchSlotsInRegion` (`o97.a`) via `vi1.m12336E` (`FULL_STRETCHED` / `PARTIALLY_*` / `DEFAULT_NO_SPACE`) |
| `aod.jr3` | `aod.KeyguardClockDebugManager` | Behind `bj1.f3755u` / `bj1.f3756v`, attaches `StretchDebugDrawable` to `SecKeyguardStatusView.setBackground(...)` |
| `aod.yi1` | `aod.StretchDebugDrawable` | Renders `background_region` green outline (`argb(100,100,230,0)`), orange exclusion (`argb(100,255,100,0)`), and clock rects |
| `aod.o16` | `aod.SemWallpaperPropertiesReflector` | Reflects `com.samsung.android.wallpaper.utils.SemWallpaperProperties` (`KEY_CONTENT_TYPE`, `KEY_IMAGE_CATEGORY`, `KEY_IMAGE_FILTER_PARAMS`) |

See [the One UI depth deep dive](ONE_UI_DEPTH_DEEP_DIVE.md) for the full
comparison between Samsung's `semGetWallpaperBackgroundRegion` / `stretch_ui` /
`aod_erase_background` / SelfMove stack and Dressroom Advanced's live subject
depth, `SharedMemory` motion IPC, clock/widget/media occlusion hooks, and
ranked improvement opportunities.

## Widget tint follow-up (2026-11-24)

The loaded reference remains AOD 8.9.66.1. Added method aliases while following
`ComplicationWidgetHostViewContainer.refreshBlur` and the preview background path:

| Original method | Alias | Evidence |
| --- | --- | --- |
| `aod.AbstractC0537ln.m7538o` | `resolveWidgetBlurType` | Selects the clock-color/wallpaper-brightness `WidgetBlurType` used by `addBgViews`. |
| `aod.AbstractC0537ln.m7537n` | `resolveWidgetPreviewBackgroundColor` | Returns the resource passed to preview/dim background creation. |

These aliases are analysis aids. Production code continues to use validated named
native contracts and signature discovery, without the original obfuscated names.

Additional handoff aliases: `ModuleClockView.m17026W0` → `initializeWidgetManagers`,
`aod.sl7` → `aod.SubLauncherClockActions`, and `aod.w02` →
`aod.LockscreenEditTransitionState`. The manager initializer forwards seamless-AOD
visibility; the sublauncher coroutine distinguishes cover editing from AOD.

### Blur lifecycle / readiness batch

| Original | Alias | Evidence |
| --- | --- | --- |
| `aod.pv2` | `ClockBlurApplyResult` | Native render result base. |
| `aod.xb0` | `ClockBlurParameters` | Constructor and toString name brightness, scale, window backend and saturation. |
| `aod.ac0` | `ClockBlurNotReady` | Holds glyph/weather readiness and path count. |
| `aod.bc0` | `ClockBlurSuccess` | Successful path count. |
| `aod.zb0` | `ClockBlurFailure` | Exception result. |
| `aod.mb0` | `ClockBlurBlockReason` | State machine's named blocker constants. |

Parameter fields are now `wallpaperBrightness`, `blurScale`, `useWindowBlur`,
`saturation`; result fields are `glyphPathsReady`, `weatherPathReady`, `pathCount`,
`exception`. ClockBlurManager's `f5372g/f5373h/f5378m/f5384s/f5370e` are now
`weatherIconView/weatherInfo/useWindowBlur/blurScale/currentClockColor`.
The alpha-change listener's smali identifies controller field `D` as the last
status-view alpha (already aliased `keyguardAlpha`). It marks ALPHA_ANIMATING whenever alpha
leaves 1 and clears it through the native 50ms debouncer on returning to 1.

`AbsClockView.m16926R0(int)` → `updateClockShadowStyle`: selects the external or
wallpaper-derived shadow only when glass blur is disabled; it is not a blur submit.

## Clock ownership batch (2026-11-24)

These are JADX display aliases verified in this pass. The previous-name
column can contain JADX-generated identifiers; compare raw DEX descriptors
and call/field contracts before using any result in a runtime shim.

| Kind | JADX owner / previous name | Alias |
| --- | --- | --- |
| class | `com.samsung.android.uniform.widget.text.AbstractC1525a` | `com.samsung.android.uniform.widget.text.StateAnimatedClockTextView` |
| class | `aod.i67` | `aod.ClockTextState` |
| class | `aod.k67` | `aod.ClockTextTransitionSpec` |
| class | `aod.g67` | `aod.ClockTextAnimatedValue` |
| field | `aod.ClockFontAxisAnimator.f21175a` | `context` |
| field | `aod.ClockFontAxisAnimator.f21176b` | `useSpringAnimation` |
| field | `aod.ClockFontAxisAnimator.f21177c` | `ownerClock` |
| field | `aod.ClockFontAxisAnimator.f21178d` | `isDozing` |
| field | `aod.ClockFontAxisAnimator.f21179e` | `baseFontData` |
| field | `aod.ClockFontAxisAnimator.f21180f` | `clockScale` |
| field | `aod.ClockFontAxisAnimator.f21181g` | `density` |
| field | `aod.ClockFontAxisAnimator.f21182h` | `isMediaExpanded` |
| field | `aod.ClockFontAxisAnimator.f21183i` | `axisAnimatorSet` |
| field | `aod.ClockFontAxisAnimator.f21184j` | `animatedAxisNames` |
| field | `aod.ClockFontAxisAnimator.f21186l` | `springTransitionStarted` |
| field | `aod.PaletteItemTransitionController.f9600a` | `defaultPalette` |
| field | `aod.PaletteItemTransitionController.f9601b` | `oldPalette` |
| field | `aod.PaletteItemTransitionController.f9602c` | `currentPalette` |
| field | `aod.PaletteItemTransitionController.f9603d` | `newPalette` |
| field | `aod.PaletteItemTransitionController.f9604e` | `transitionAnimator` |
| field | `aod.PaletteItemTransitionController.f9605f` | `ownerClock` |
| field | `aod.PaletteItemTransitionController.f9607h` | `colorStops` |
| field | `aod.PaletteItemTransitionController.f9611l` | `paletteLock` |
| field | `com.samsung.android.uniform.widget.clock.AbsClockView.f43107x` | `blurTextAlpha` |
| field | `com.samsung.android.uniform.widget.clock.AbsClockView.f43100t0` | `dozeAmount` |
| field | `com.samsung.android.uniform.widget.clock.AbsClockView.f43077f0` | `lockscreenPalette` |
| field | `com.samsung.android.uniform.widget.clock.AbsClockView.f43079g0` | `aodPalette` |
| method | `aod.ClockAnimationStateManager.m6681c` | `notifyAnimationStateChanged` |
| method | `aod.ClockAnimationStateManager.m6683e` | `setSpringAnimationRunning` |
| method | `aod.ClockAnimationStateManager.m6684f` | `setStretchAnimationRunning` |
| method | `aod.PaletteItemTransitionController.m4403a` | `setPaletteItem` |
| method | `aod.PaletteItemTransitionController.m4404b` | `ensureEndpointColors` |
| method | `aod.PaletteItemTransitionController.m4405c` | `compactColorWindow` |
| method | `com.samsung.android.uniform.widget.clock.AbsClockView.mo1631Z` | `onDozingChanged` |
| method | `com.samsung.android.uniform.widget.clock.AbsClockView.mo1732w` | `rebuildBlurTextViews` |
| method | `com.samsung.android.uniform.widget.ClockTextView.m16832a` | `fixFontType` |
| method | `com.samsung.android.uniform.widget.ClockTextView.m16833b` | `updateTypeface` |
| method | `com.samsung.android.uniform.widget.text.AbstractC1525a.m17106l` | `getStateTextColor` |
| method | `com.samsung.android.uniform.widget.text.AbstractC1525a.m17107m` | `requestTextPreprocessing` |
| method | `com.samsung.android.uniform.widget.text.AbstractC1525a.m17108n` | `setStateTextColor` |
| method | `com.samsung.android.uniform.widget.text.AbstractC1525a.m17109o` | `updateStateColor` |
| method | `com.samsung.android.uniform.widget.text.AbstractC1525a.m17110p` | `startStateTransition` |
| method | `aod.g67.m4968a` | `setAnimationTarget` |
| method | `aod.g67.m4969b` | `sampleFraction` |
| method | `com.samsung.android.uniform.widget.clock.AbsClockView.m16908I` | `blendPaletteColor` |
| method | `com.samsung.android.uniform.widget.clock.AbsClockView.m16910J` | `blendPaletteColors` |
| method | `aod.cj1.m2849r0` | `applyGradientToTextView` |

## Blur pipeline continuation (2026-11-24, later pass)

Continuation of the clock-blur apply pipeline in AOD 8.9.66.1. Full
chronological detail with evidence is in `JADX_RENAME_LOG.md`; the entries
below are the new analysis aliases. As before, original names apply only to
this reference build and the module keeps signature-based resolution.

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| field | `aod.CombinedBlurStrategy.f29391a` | `logTag` | ctor `"tag"` arg, AODLogger tag |
| field | `aod.CombinedBlurStrategy.f29392b` | `clockHost` | `aod.p67` holder exposing `clockView` |
| field | `aod.CombinedBlurStrategy.f29393c` | `blurDebugHelper` | debug-only `aod.w06` helper |
| field | `aod.p67.f22785o` | `clockView` | cast to `AbsClockView` at all use sites |
| method | `aod.w06.m12636A` | `initializeDebugOverlay` | "ClockBlurDebugHelper" overlay setup |
| method | `aod.w06.m12653R` | `updateDebugOverlayWithPath` | "ClockBlurDebugHelper" path update |
| field | `aod.w06.f32096p` / `f32097q` | `debugOverlayView` / `debugRootView` | overlay view / host root |
| class | `aod.aj1` | `aod.ClockBlurDebugOverlayView` | View drawing debug path; `f2474o` → `debugPath` |
| method | `aod.gv8.m5413w` | `getViewOrigin` | screen vs window origin `PointF` |
| field | `aod.bj1.f3756v` | `clockBlurDebugOverlayEnabled` | gates debug helper creation |
| field | `aod.ClockBlurManager.f5366a` / `f5367b` | `clockHost` / `logTag` | p67 holder / logger tag |
| field | `aod.ClockBlurManager.f5368c` | `lastKnownIsShown` | isShown mirror; false→true triggers blur apply |
| field | `aod.ClockBlurManager.f5369d` | `globalLayoutListener` | `aod.xp0` OnGlobalLayoutListener |
| field | `aod.ClockBlurManager.f5380o` | `blurDebugHelper` | debug helper instance |
| field | `aod.ClockBlurManager.f5382q` | `unsupportedBlurClockDelegate` | KProperty idx 0 "unsupportedBlurClock" |
| field | `aod.ClockBlurManager.f5383r` | `blurEffectEnabledDelegate` | idx 1 "blurEffectEnabled" |
| field | `aod.ClockBlurManager.f5385t` | `weatherInfoEnabledDelegate` | idx 2 "weatherInfoEnabled" |
| field | `aod.ClockBlurManager.f5386u` | `collectedViewsLogDelegate` | idx 3 "collectedTextViewLogMsg" |
| method | `aod.ClockBlurManager.m2925f` | `formatColorDebug` | ARGB → "Color R.. G.. B.. A.." |
| field | `aod.ClockBlurStateMachine.f7198b` | `logTag` | ctor tag |
| field | `aod.ClockBlurStateInfo.f8153c` | `timestampMillis` | currentTimeMillis, printed as timestamp |
| field | `aod.sx0.f28030o` / `f28031p` / `f28032q` | `listenerMode` / `targetAlpha` / `target` | animator listener switch/args |
| class | `aod.l49` | `aod.WallpaperBrightnessInfo` | toString label; `f17619a`/`f17620b` → `brightness`/`saturation` |
| class | `aod.pb0` | `aod.ColorCurveParams` | toString label; fields → `x1,y1,x2,y2,minY,maxY` |
| field | `aod.ClockBlurPropertyDelegate.f2673p` / `f2674q` | `propertyIndex` / `ownerManager` | delegate index / owning manager |
| method | `AbsClockView.m16905G` | `getAdjustedAodPaletteItem` | "getAdjustedAODColorItem" log |
| method | `AbsClockView.mo16917M0` | `updatePaletteAndPrimaryColor` | log label |
| method | `AbsClockView.mo16921O0` | `updatePaletteOnAnimation` | log label |
| method | `AbsClockView.mo1634d0` | `applyShadowStyle` | abstract ShadowStyle hook |
| field | `AbsClockView.f43076e1` / `f43069b0` / `f43083i0` | `hasFrame` / `paletteStyle` / `paletteIndex` | setHasFrame / EnumC0303fb / preset index |
| field | `AbsClockView.f43086l0` / `f43089o` | `customColor` / `logTag` | setCustomColorInternal / logger tag |
| field | `AbsClockView.f43075e0` / `f43081h0` | `presetPaletteItem` / `paletteCode` | preset clone source / getPaletteCode |
| field | `AbsClockView.f43110y0` / `f43112z0` | `externalShadowStyle` / `currentShadowStyle` | external vs effective shadow |
| field | `AbsClockView.f43072c1` | `weatherInfo` | WeatherInfo holder |
| field | `aod.PersistentBlurReasonMapping.f20671a` | `persistentReasonKindByOrdinal` | ordinal→kind table for hasPersistentBlockReason |

## Animation-state / sublauncher / layout-listener pass (2026-11-24, later pass 2)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| field | `aod.ClockAnimationStateManager.f15995o` | `containerMode` | ctor switch picks callback container type |
| field | `...f15996p` / `f15997q` | `stretchAnimationRunning` / `springAnimationRunning` | set by the two setters, OR'd by `isClockInternalAnimationRunning` |
| field | `...f15998r` | `lastNotifiedRunningState` | notify-state compare in `notifyAnimationStateChanged` |
| field | `...f15999s` | `callbackContainer` | ArrayDeque / rp4 / CopyOnWriteArrayList per mode |
| method | `aod.ClockAnimationStateManager.m6679a` / `m6682d` | `drainPendingRunnables` / `clearCallbackStore` | queue drain / store clear |
| field | `aod.SubLauncherClockActions.f27645o` / `f27646p` | `coroutineState` / `ownerController` | Function2 state index / captured controller |
| field | `aod.xp0.f34554o` / `f34555p` / `f34556q` | `listenerMode` / `clockBlurManager` / `clockViewFrame` | onGlobalLayout switch + case-0 casts |

## Stretch measure + Now Bar callbacks pass (2026-11-24, later pass 3)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| field | `aod.RunStretchMeasureProcessUseCase.f9742a..f9746e` | `clockPositionCalculator` / `clockStretchDataRepository` / `clockDataRepository` / `stretchMeasureProcessRunner` / `logTag` | ctor param names + tag string |
| method | `aod.RunStretchMeasureProcessUseCase.m4462b` | `computeStretchContentRect` | rect copy minus stretch offset |
| field | `aod.SetNowBarMediaExpandStateUseCase.f4330a` / `f4331b` | `nowBarMediaExpandStateRepository` / `statusCallback` | ctor param names |
| method | `aod.SetNowBarMediaExpandStateUseCase.m2426a` / `m2427b` | `setMediaExpandedState` / `updateExpandStateLiveData` | repo write + `onMusicItemExpaned` / LiveData-only post |
| field | `aod.ClockStretchPhysicsAnimator.f4736n` / `f4749m` | `debugLogEnabled` / `profiler` | `bj1.f3760z` debug gate / String[100]+long[100] recorder |
| field | `aod.NowBarStackCardCallbacks.f2395o` / `f2396p` | `callbackMode` / `callbackTarget` | dispatcher switch + per-interface casts |
| method | `aod.NowBarStackCardCallbacks.m1389v0` / `m1391A0` / `m1393B0` / `m1454t0` / `m1460x0` / `m1462y0` / `m1464z0` | `loadCoverKeyguardImage` / `onExpandVIStarted` / `onExternalNowbarExpandCardAttached` / `createBottomMarginAnimator` / `notifyViewVisibilityChanged` / `onCollapsedTouchFromCard` / `onExpandCardInflatedFromCardView` | SystemUI cover-image URI, log labels, delegated calls |

## ModuleClockView pass (2026-11-24, later pass 4)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| field | `ModuleClockView.f43476l1` / `f43477m1` | `frameWidgetTypeProvider` / `settingsHelper` | `sn2.getFrameWidgetType` / `SettingsHelper` |
| field | `ModuleClockView.f43478n1` / `f43479o1` | `briefWidgetComponent` / `briefWidgetManager` | created in `createBriefWidgetComponent` / `IBriefWidgetManager` |
| field | `ModuleClockView.f43480p1` / `f43481q1` | `briefWidgetContainer` / `clockWidgetContainer` | `brief_widget_container` / `basic_clock_widget_clock_container` view ids |
| field | `ModuleClockView.f43482r1` / `f43483s1` | `clockView` / `moduleInfo` | inner `AbsClockView` / `setModuleData` string |
| field | `ModuleClockView.f43484t1` | `currentFrameWidgetType` | `updateWidgetType` compare |
| field | `ModuleClockView.f43485u1` / `f43486v1` / `f43487w1` | `positionX` / `positionY` / `clockScale` | `ek4` position-data args |
| field | `ModuleClockView.f43488x1` / `f43489y1` / `f43490z1` | `previewMode` / `editable` / `frameDecorator` | `setPreviewMode`/`setEditable`/`C0663p1` wrapper |
| method | `ModuleClockView.m17024U0` / `m17025V0` | `hasWidgetManagers` / `updatePositionData` | readiness check / position controller write |

## Clock view subclasses pass (2026-11-24, later pass 5)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| method | `ClockFaceView.m16976U0` / `m16985d1` | `requestClockFaceInfoOnFontChanged` / `requestClockFaceInfo` | "requestClockFaceInfo ..." log labels |
| method | `ClockFaceView.m16978W0` / `ClockFaceCustomView.m16955V0` | `changeToDefaultClockType` | log label |
| method | `ClockFaceView.m16979X0` | `captureViewBitmap` | measures view, draws to Bitmap |
| method | `ClockFaceView.m16981Z0` / `ClockFaceCustomView.m16957X0` | `applyBatteryActions` | iterates `BatteryAction` list |
| method | `ClockFaceView.m16982a1` | `applyMinSize` | log label |
| method | `ClockFaceView.m16983b1` / `ClockFaceCustomView.m16959Z0` | `getLinearGradientDrawable` | log label |
| method | `ClockFaceView.m16984c1` | `createClockFaceRequest` | builds Bundle + ClockFaceRequest |
| method | `ClockFaceCustomView.m16958Y0` | `applyColorFilter` | log label |
| method | `ClockFaceCustomView.m16960a1` | `applyHoneyFaceRule` | applies `HoneyFaceRule` |
| method | `ClockFaceCustomView.m16961b1` | `readStreamBytes` | InputStream→byte[] loop |
| method | `ClockFaceCustomView.m16962c1` | `isMainScreenCategory` | LOCK_SCREEN/SUB_LAUNCHER/SUB_UI |
| method | `ClockFaceCustomView.m16963d1` | `loadWatchFaceSurface` | WatchfaceSurfaceView "load:" |
| method | `DigitalDualClockView.m16989W0` / `m16991V0` / `m16992X0` / `m16994Z0` | `setImageViewsColorFilter` / `setTimeAndSpaceVisibility` / `updateAreaVisibility` / `applyDateTimeTexts` | view-binding bodies |
| method | `DigitalHorizontalClockView.m16996W0` / `m16997X0` | `onFontStyleUpdated` / `showNormalTimeArea` | log label / normal-vs-stretch area switch |
| method | `AnalogClockView.m16951V0` | `applyScalePivotToView` | `getScalePivot()` |
| method | `CityClockView.m16953a1` | `setTextSizeScaled` | setTextSize wrapper |

Area notes: `common.grid.rule`, `common.provider.p003DB`, and
`facewidget.complication` are Samsung-named at class and member level
(sampled: 0 raw members)  -  no renames needed there.

## Debug flags + settings-key infrastructure pass (2026-11-24, later pass 6)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.xi1` | `aod.BuildTypeFlags` | `isNonShipBuild` (`!ro.product_ship`), `isEngOrUserdebugBuild` |
| class | `aod.bj1` | `aod.AodDebugFlags` | 28 flags mapped to `debug.aod.*` / `persist.debug.*` SemSystemProperties (e.g. `debugTapToShow`, `debugClockMovableArea`, `clockBlurStateDebugEnabled`, `debugPreviewSaveBitmap`, 7 `logModule*` filters) |
| class | `aod.rt6` | `aod.SettingKeyEntry` | `settingsTable`/`key`/`keyUri`/`dataType`/`defaultIntValue`/`userSuffix`/`cachedIntValue`/`cachedStringValue`; `getIntValue`/`getStringValue`/`buildKeyUri`/`loadValueFromResolver` |
| method | `aod.z82.m14360M` / `m14362O` | `registerSettingEntry` / `getSettingEntry` | settings cache on mega class |
| field | `SettingsHelper.f39619e` | `settingsCache` | z82 cache of 227 registered keys |
| method | `SettingsHelper.m15243x` | `isNowBarAodEnabled` | `now_bar_aod_enabled==1` + fullscreen-AOD/media-card check |
| method | `SettingsHelper.m15244y` / `m15245z` / `m15200B` | `isLockscreenWallpaperTransparent` / `isRemoveAnimationsEnabled` / `isCoverScreenShowNotificationEnabled` | |

See `HIDDEN_FEATURES.md` for the full hidden-settings inventory and the
Now Bar on AOD flow (`now_bar_aod_enabled`, backup key `/AOD/ShowNowBarEnabled`).

## Device feature wall pass (2026-11-24, later pass 7)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.ha6` | `aod.DeviceFeatureFlags` | 135-flag clinit over SamsungFeatureReader / SemSystemProperties / CSC / sysfs |
| field | `aod.DeviceFeatureFlags.f12824J0` | `aodFullscreenLevel` | `SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN`, consumed by `cj1.isFullScreenAODSupported` |
| field | `f12845U` / `f12847V` / `f12866g0` | `supportsLockscreenTile` / `supportsAodTile` / `supportsVirtualDisplay` | tile config contains checks |
| field | `f12865g` / `f12899x` / `f12871j` / `f12873k` / `f12867h` | `isChinaDevice` / `isIranDevice` / `isUsDevice` / `isKoreaDevice` / `isChinaCarrier` | country ISO / sales code |
| field | `f12829M` / `f12817G` | `isE5SeriesDevice` / `isB4qE4qDevice` | `ro.product.vendor.device` |
| field | `f12813E` / `f12828L0` | `nativeAiEnabled` / `nativeAiDisabled` | `SEC_FLOATING_FEATURE_COMMON_DISABLE_NATIVE_AI` |
| field | `f12898w0` / `f12900x0` / `f12902y0` | `aiBriefSupported` / `aiConfigVersionGte20263` / `aiConfigVersionLt20263` | AI version + AI Brief feature |
| field | `f12878m0` | `selfPokeDrawLockEnabled` | `persist.enable.self.poke.draw_lock` |
| field | `f12848V0` | `foldPerformanceDebug` | `debug.aod.fold_performance` |
| field | `f12808B0` / `f12897w` | `defaultClockPackage` / `hasClockPackVersionOverride` | ClockPack vs AOD / clockpackversion config |
| field | `f12854a0` / `f12856b0` / `f12858c0` | `hasBatteryTimeToFull` / `hasLedPattern` / `isAmoledDisplay` | sysfs + AMOLED feature |
| field | `f12831N` / `f12850X` / `f12876l0` / `f12886q0` | `supportsDisabledMenuN40` / `frameworkVersionGte110500` / `semVersionGte50000` / `semVersionGte80000` | feature/version gates |

Full hidden inventory (providers, receivers, SCPM policies, Rubin feeds,
NHN Now Bar provider, Dressroom provider): `HIDDEN_FEATURES.md`.

## SCPM / per-app Now Bar pass (2026-11-24, later pass 8)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.df6` | `aod.ScpmAodPolicyManager` | "ScpmManager_AOD"; SCPM provider registration + policy write to `Secure/scpm_aod_common_policy` |
| class | `aod.AbstractC0814t1` | `aod.AodCommonSettingsUtils` | "AODCommonSettingsUtils"; parses SCPM XML (`nowbar_commute`, device_policy) |
| method | `m11184A`..`m11187D` | `isNowBarCommutePolicyEnabled` / `isGoogleMapsNowBarCommuteEnabled` / `isGoogleFinanceNowBarEnabled` / `isGoogleQuickSearchBoxNowBarEnabled` | policy contains-check + per-app `key_now_bar_*` System keys |

Hidden settings/policy inventory: `HIDDEN_FEATURES.md` (SCPM flow, `key_now_bar_*`
family, `dls_state` bitmask, all 107 registered keys, search-exposed subset).

## Settings consumer map pass (2026-11-24, later pass 9)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.C0336g7` | `aod.AODSettingsMainPrefsFragment` | "AODSettingsMainPrefsFragment"; erase-background toggle + preview |
| class | `aod.iz6` | `aod.SleepStateManager` | "SleepStateManager"; sleep/fence state writes |
| class | `aod.C0409i6` | `aod.RunestoneManagerDump` | RunestoneManager dump incl. fence status |
| method | `SettingsHelper.m15241v` | `isMultipleLockscreenEnabled` | kg_multiple_lockscreen observer |

20 hidden keys traced to their driving feature  -  see `HIDDEN_FEATURES.md` consumer map.

## Hidden test UI pass (2026-11-24, later pass 10)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.ti8` | `aod.TestModeSettingFragment` | hosted by `aod.setting.TestModeSettingActivity`; 14 hidden test keys + `fix_aod_position`/`aod_clock_type`/`locale_index`/`calendar_index` extras |

Hidden activities, Knox-AI (`SemEnterpriseDeviceManager` app restrictions for
`com.samsung.android.knox.galaxyai`), and test knobs: `HIDDEN_FEATURES.md` §I-K.

## Test-mode gate + Rubin pass (2026-11-24, later pass 11)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| method | `aod.wt6.m12972e` | `isAodTestModeEnabled` | `Settings.System "aod_test_mode_only"==1` |
| method | `aod.wt6.m12975h` | `isHijriTestModeEnabled` | test mode + `aod_hijri_test_mode` |

Rubin = on-device AI ("Runestone"): persona/context content providers feed Now Bar;
`RunestoneSleepApi` context fences drive AOD sleep/wake (`AODFenceReceiver` →
`aod_sleep_state` / `aod_context_fence_not_working`). Details: `HIDDEN_FEATURES.md`.

## Settings UI / helper class pass (2026-11-24, later pass 12)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.nb2` | `aod.FaceWidgetSettingsMainPrefFragment` | log tag; where_to_show switch |
| class | `aod.ga2` | `aod.FaceWidgetMusicSettingsPrefFragment` | log tag; music transparency |
| class | `aod.tn1` | `aod.DimAnimationHelper` | "DimAnimationHelper" |
| class | `aod.s66` | `aod.ResetUtils` | "ResetUtils"; CMF color-area reset |
| class | `aod.la2` | `aod.ExternalFileReader` | "ExternalFileReader" |
| class | `aod.C0372h6` | `aod.SleepStateSettingObserver` | sleep/fence key observer |
| class | `aod.jz6` | `aod.SleepStateObserverRegistrar` | observer registration |

Session summary: `AOD_9_EXPLORATION_SUMMARY.md` (273 renames, 17 batches).

## Key-trace helper pass (2026-11-24, later pass 13)

| Kind | Original | Alias | Evidence |
| --- | --- | --- | --- |
| class | `aod.v10` | `aod.ScpmSubscreenPolicyHandler` | "ScpmSubscr" log; SCPM subscreen preload policy JSON |
| class | `aod.rv2` | `aod.MultiFoldSubDisplayModeController` | writes `aod_multi_fold_sub_display_mode` |

16 more settings keys traced + Rubin→Now Bar wiring: `HIDDEN_FEATURES.md` §L-M.


## Animation engine contract pass (2026-11-24)

62 successful rename operations are recorded for this pass: 58 from the first
animation trace and four palette callback aliases on continuation. Three failed
synthetic-capture field attempts were excluded. Some captures became inlined
in JADX after decompilation; an alias does not imply a separate visible field
in every decompiler view. Identifiers are the pre-rename JADX presentation,
not reusable runtime hook keys.

See [animation engine audit](CLOCK_ANIMATION_ENGINES.md) for the source/smali
contracts, timings, version boundaries and outstanding proof. R8-merged
callback classes were not renamed wholesale.

| Kind | Before | Alias | Evidence |
| --- | --- | --- | --- |
| field | `aod.c97.f4737a` | `slotCount` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4738b` | `requestedHeights` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4739c` | `slotEngine` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4740d` | `frameCallback` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4741e` | `physicsConfig` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4742f` | `maxHeightRatio` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4743g` | `mainHandler` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4744h` | `started` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4745i` | `running` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4746j` | `continueFrames` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4747k` | `frameRequested` | Constructor and frame/state reads/writes |
| field | `aod.c97.f4748l` | `lastFrameNanos` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36713a` | `accelerationUp` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36714b` | `accelerationDown` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36715c` | `maxSpeed` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36716d` | `speedScale` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36717e` | `restitution` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36718f` | `nominalFrameNanos` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36719g` | `minTimeDeltaScale` | Constructor and frame/state reads/writes |
| field | `aod.z87.f36720h` | `maxTimeDeltaScale` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3287a` | `maxHeightRatio` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3288b` | `currentHeight` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3289c` | `targetHeight` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3290d` | `initialDelta` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3291e` | `velocity` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3292f` | `acceleration` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3293g` | `restitution` | Constructor and frame/state reads/writes |
| field | `aod.b97.f3294h` | `speedScale` | Constructor and frame/state reads/writes |
| field | `aod.mr0.f19882o` | `phase` | Constructor and frame/state reads/writes |
| field | `aod.mr0.f19883p` | `axisAnimator` | Constructor and frame/state reads/writes |
| field | `aod.PaletteItemTransitionController.f9606g` | `animationFraction` | Constructor and frame/state reads/writes |
| field | `aod.PaletteItemTransitionController.f9608i` | `targetOffset` | Constructor and frame/state reads/writes |
| field | `aod.PaletteItemTransitionController.f9609j` | `windowTransitionLength` | Constructor and frame/state reads/writes |
| field | `aod.PaletteItemTransitionController.f9610k` | `windowOffset` | Constructor and frame/state reads/writes |
| field | `aod.ClockFontAxisAnimator.f21185k` | `animationStateListener` | Constructor and frame/state reads/writes |
| method | `aod.ClockFontAxisAnimator.m8609c` | `hasStretchedHeights` | Body, callers and completion/clip semantics |
| method | `aod.ClockFontAxisAnimator.m8610a` | `resolveClockType` | Body, callers and completion/clip semantics |
| method | `aod.ClockFontAxisAnimator.m8611b` | `getAnimationLogTag` | Body, callers and completion/clip semantics |
| method | `aod.c97.m2585a` | `requestAnimationFrame` | Body, callers and completion/clip semantics |
| method | `aod.c97.m2586b` | `updateMaxHeightForFont` | Body, callers and completion/clip semantics |
| method | `aod.c97.m2587c` | `seedStretchHeights` | Body, callers and completion/clip semantics |
| method | `aod.c97.m2588d` | `setRunning` | Body, callers and completion/clip semantics |
| method | `aod.b97.m1961a` | `applyPhysicsConfig` | Body, callers and completion/clip semantics |
| method | `aod.z87.m14369a` | `accelerationForDelta` | Body, callers and completion/clip semantics |
| method | `aod.FaceWidgetTransition.m9763a` | `applyPropertyAnimationOptions` | Body, callers and completion/clip semantics |
| method | `aod.FaceWidgetTransition.m9774m` | `applyTransitionClipping` | Body, callers and completion/clip semantics |
| method | `aod.FaceWidgetTransition.m9775n` | `clearDescendantTransitionClips` | Body, callers and completion/clip semantics |
| method | `aod.FaceWidgetTransition.m9776o` | `clearAncestorTransitionClips` | Body, callers and completion/clip semantics |
| method | `aod.CombinedBlurStrategy.m11663a` | `finishBlurTransitionAlpha` | Body, callers and completion/clip semantics |
| method | `aod.ub2.m11827a` | `setTransitionMonitor` | Body, callers and completion/clip semantics |
| class | `aod.c97` | `ClockStretchPhysicsAnimator` | Construction, frame ownership and xrefs |
| class | `aod.b97` | `ClockStretchSlotState` | Construction, frame ownership and xrefs |
| class | `aod.z87` | `ClockStretchPhysicsConfig` | Construction, frame ownership and xrefs |
| class | `aod.C0230db` | `ClockStretchFrameDrawable` | Construction, frame ownership and xrefs |
| class | `aod.mr0` | `ClockFontAxisFrame` | Construction, frame ownership and xrefs |
| class | `aod.a97` | `ClockStretchHeightsFrame` | Construction, frame ownership and xrefs |
| class | `aod.rb2` | `ClockPageTransitionOptions` | Construction, frame ownership and xrefs |
| class | `aod.ub2` | `ClockPageTransitionSynchronizer` | Construction, frame ownership and xrefs |
| class | `aod.e95` | `ClockPaletteTransitionCallbacks` | Interface plus palette/ModuleClockView xrefs |
| method | `aod.e95.mo1632a` | `onPaletteTransitionFrame` | Palette frame/start/end callers |
| method | `aod.e95.mo1635e` | `onPaletteTransitionFinished` | Palette frame/start/end callers |
| method | `aod.e95.mo1641m` | `onPaletteTransitionStarted` | Palette frame/start/end callers |
