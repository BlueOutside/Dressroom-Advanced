# Clock rendering ownership map

Audit date: 2026-11-24. Reference providers: One UI 9 AOD 8.9.66.1,
Dressroom 3.0.10.38 and SystemUI Android 17 (version code 37).
This is a source/smali ownership audit of the digital clock, its host and the
module's replacement glass. It does not certify every style or prove the
remaining player-animation blink fixed.

The installed baseline is `2.0.6-blur-baseline` (20006033). The failed
Natural-player renderer exemptions were reverted before this audit. This
audit changes research notes and offline tooling, not production rendering.

Follow-up implementation: [readiness-based handoff preview](CLOCK_RENDERING.md#readiness-based-player-handoff-preview-2026-11-24).
The writer tables below describe the audited baseline; that follow-up records
the subsequent production changes and their validation separately.

See also [clock deep dive](ONE_UI_CLOCK_DEEP_DIVE.md),
[layer handoffs](CLOCK_LAYER_HANDOFFS.md),
[rendering investigation](CLOCK_RENDERING.md) and
[SystemUI deep dive](ONE_UI_SYSTEMUI_DEEP_DIVE.md).
The alias tables record verified names; aliases are never runtime hook keys.

Follow-up: [animation engine audit](CLOCK_ANIMATION_ENGINES.md) separates timed
axes, editor physics, palette, blur-alpha and page/host motion. It also records
9.5's additional shrink animator and the RGB-source policy version boundary.

## 1. Distinguish instances, draw passes and surfaces

| Object / pass | Owner | Can look like another clock? |
| --- | --- | --- |
| Live page clock | FaceWidgetClockPage's bound AbsClockView | The ordinary lockscreen clock. |
| Dashboard / seconds clock | Dashboard clock holder and source/destination page transition | Yes: separate attached source and target during handoff. |
| LTPO lock-ambient clock | The live lockscreen clock in LOCK_AMBIENT page state | Usually reused rather than a new root; page policy determines this. |
| Standalone AOD clock | AODClockContainer when policy needs its own clock | Yes: separate inflation, font data, scale and lifecycle. |
| Nested module / world clock | ModuleClockView and nested provider inflation | Separate inner clock; a largest-view heuristic can select the wrong instance. |
| Detached measurement clock | Provider size/stretch repositories | Not a visible owner. Do not apply live profiles or media state merely because it is an AbsClockView. |
| Base/shadow glyph pass | ClockNonePaddingTextView or ClockTextView onDraw | Two native draws at the same geometry when a shader exists. This alone is not a displaced duplicate root. |
| Gradient glyph pass | The same text view, with its shader restored | The visible color can include shader alpha independently of path ink alpha. |
| Native glass silhouette | CombinedBlurStrategy and the shared lock_clock_blur host | A stale or displaced matte can remain while only one text clock is attached. |
| Module artwork glass | MediaClockContrast glyph RenderNodes / mask renderer | An additional drawing authority over player artwork. |
| Module fallback clock mask | Fullscreen backdrop's cached clock outline | Can coexist with glyph and native-host passes unless ownership is explicitly handed off. |

Native `PaletteItem.SOLID` still produces repeated color stops and passes through
the gradient shader helper. A visually solid clock can therefore use the
two-pass native onDraw path. Smali confirms both super.onDraw calls.

## 2. Property writers

| Property | Native authority | Module authority / review boundary |
| --- | --- | --- |
| Desired blur | AbsClockView.mIsBlurEffectEnabled | BlurEffects and saved clock settings. |
| Active blur presentation | AbsClockView.mIsBlurActive, setDozing, onDozingChanged | ClockBlurMotion can retain wallpaper-AOD presentation. |
| Manager blur enabled | ClockBlurManager observable delegate | Motion hooks can enable during allowed movement; this differs from desired/active flags. |
| RGB and palette stops | PaletteItemTransitionController, blendPaletteColor(s), applyPaletteGradientBatch | MediaClockContrast replaces paint/shader temporarily for the player. |
| Path ink color | NonePaddingTextView.pathTextColor when blurPathMode is true | MediaClockContrast reads/writes this field separately from TextView colors. |
| Text color state | TextView ColorStateList, ordinary text color setters | Player's ordinary-text override tracks both setter overloads. |
| Glyph alpha | Palette stop ARGB, blurTextAlpha, manager's applyAnimatedAlpha | ClockBlurContinuity can finish native alpha transitions early; player contrast has its own opacity. |
| Glyph geometry | Font data, HEGT/wght axes, layout, draw offsets, outline getter | RuntimeClockData/VariableFonts/manual stretch; glyph reuse only within a draw. |
| Weather geometry / ink | WeatherInfo, icon path generation, text renderer, image alpha/filter | Player weather rendering and MediaBlurHost's tracked weather alpha. |
| Glass clip and scale | CombinedBlurStrategy builds scaled glyph/weather paths | ClockBlurAlignment and ClockBlurSync follow current geometry. |
| Host alpha | Manager reveal/dashboard/tick animators and strategy transitions | MediaBlurHost multiplies requested native alpha by its held factor. |
| Parent alpha / scale | SystemUI swipe, shade, visibility and wake animation controllers | UnlockBlur retains/fades blur through supported movement. |
| Blur radius / curve / tint | Native SemBlurInfo request and backend selection | BlurAppearance adjusts appearance. Natural/neutral is a tone-curve option, not renderer ownership. |
| Player replacement glass | No single native owner for module fullscreen replacement | MediaFullscreenExtras, MediaClockContrast and legacy artwork path. |
| Profile persistence | Editor model, WallpaperClockBundle, native wallpaper identity | Module profile source, read/commit lifecycle and display identity. No renderer flag should invalidate a profile. |

### Observable delegate side effects

On the reference, changing the manager's enabled delegate from false to true
marks a pending reveal and asks the owner to submit blur. Changing true to false
clears the strategy directly, cancels its tick transition, applies native ink
alpha 127 and cancels manager reveal/dashboard animators. Assigning the same
Boolean does not replay those transitions.

Consequences:

- Intercepting only AbsClockView.removeBlur does not cover every native clear.
- Calling an enable setter with true is not proof that a fresh render happened.
- A successful render result does not prove its host is visible.
- Retaining a matte does not preserve the ink alpha, gradient or player renderer.

### Glyph readiness and color caveats

ClockNonePaddingTextView.getPathForBlur requires a completed glyph draw and
returns a copy of the cached path. The strategy's in-place scale therefore does
not establish accumulated mutation of that cache. ClockTextView instead builds a
fresh outline from its layout and paint after its own draw-ready gate.

NonePaddingTextView.setTextColor(int) writes pathTextColor and may not update
TextView's ColorStateList in path mode. GradeStateAnimTextView's stateful color
handling is a different path. Do not restore all these families through the same
ColorStateList operation.

The reference grade-text base contains a preprocessing worker implementation,
but all four callers of setPreprocessingThread pass false in smali. Its presence
does not prove a live worker or justify enabling it. R8 merges that Handler and
several state/callback helpers with unrelated roles.

## 3. SystemUI host contract

### Alpha and gesture ownership

FaceWidgetContainerWrapper.initPlugin stores the overall facewidget root and
the plugin's contents list; index 0 becomes mClockContainer. Touch index 1 uses
that clock container, falling back to the overall root. Widget index 14 comes
from provideComplication; Now Bar is index 12.

FaceWidgetContainerWrapper.setAlpha writes the root's alpha then dispatches
KeyguardStatusViewAlphaChangeControllerWrapper.updateAlpha to plugin listeners.
It skips alpha=1 while the status bar is in state 2. Smali confirms this ordering.

The alpha callback has five reference call sites:

1. Wrapper setAlpha (including shade updates).
2. ActionMoveHandler's direct swipe updates.
3. KeyguardTouchAnimator resetChildViewTransforms.
4. ViewAnimationController's clock-container ObjectAnimator update listener.
5. The restore-drag SpringAnimation update listener.

During swipe, the principal fade reaches zero over 0.64 * swipeUnlockRadius;
scale grows toward 1.07 over 0.8 * radius. Some affordances use a separate,
faster fade. These are parent/view transformations, not glyph-axis updates.

Canceling the gesture springs alpha and scale back to 1. The plugin alpha
listener feeds its separate ALPHA_ANIMATING blur blocker, cleared through the
native debouncer. An independent KeyguardSecVisibilityHelper path animates the
root directly with ViewPropertyAnimator and changes visibility at completion.
Not every parent alpha write travels through the wrapper's alpha callback.

Shade updateClock$1 calls the wrapper; getFaceWidgetAlpha returns -1 to defer to
an active touch/bouncer animation, or fades to zero over the first half of the
shade transition. Now Bar and affordances have their own synchronized writes.

### Doze has several routes

- Ordinary StatusBarStateListener.onDozeAmountChanged forwards linear and
  interpolated amounts, except when the light-reveal/fullscreen policy owns
  that transition.
- SecLightRevealScrimHelper's flow forwards 1-linearRevealAmount and
  1-interpolatedRevealAmount. In fullscreen AOD it also calls the AOD plugin's
  onUnlockedScreenOffAmountChanged.
- The fold-state listener resets the main facewidget's amounts to 0,0 on open;
  closed state can dispatch amounts to the subscreen plugin.
- PluginAODManager.setIsDozing separately updates plugin lifecycle/overlay state
  and returns early for an unchanged Boolean.
- ScreenOnAffordanceViewController separately brackets native wake springs with
  onScreenOnOffAnimationStart/End. When DozeUiState suppresses that animation,
  the screen-on route resets child transforms rather than running the springs.

A single setDozing hook cannot represent every fraction, wake blocker or display
handoff.

### Media is not SystemUI's generic fullscreen mode

FaceWidgetKeyguardStatusCallbackWrapper.onMusicItemExpaned updates the media
expanded state, notification presentation, NIO and optional LockStar, and runs a
separate notification-stack fade. Its setFullScreenMode path hides the entire
panel for a different native mode. Do not use that Boolean as the player glyph
renderer or font-collapse fraction.

The base SystemUI KeyguardClockPositionAlgorithm is mostly a notification
padding fallback in this APK. FaceWidgetPositionAlgorithmWrapper delegates the
actual plugin geometry and reads its result. Changing the AOSP fallback cannot
repair a provider's stale clock measurement.

## 4. Dressroom preview contract

Verified in source, xrefs and smali:

- ClockBlurCallback.isClockBlurAvailable is exactly
  PreviewState.idle AND ClockData.blurEffectEnabled AND ClockData.idleBlur.
- Internal animation start cancels the settle job and emits animationIdle=false.
  Animation end schedules animationIdle=true after 300 ms.
- A clock-position rectangle change emits positionIdle=false, then true after
  300 ms through transformLatest. New motion cancels the earlier settle.
- ClockBlurIdleStateCoordinator combines measure state, animation, position,
  suggestion, preview/assist, wallpaper and scroll state. Only an
  ObserveSizeChange measure state can pass the final gate; layout-changing
  and processing-suggestion states block it.
- Applying final idle=true has a further cancellable 300 ms delay before
  dispatching SetClockIdleBlurUseCase on Main. These delays can compose;
  there is no single universal 300 ms blur timer.
- Blur-type changes explicitly set idleBlur=false through a separate preview
  view-model callback.
- ClockMeasureStateCoordinator maps init to Init, drag/scale start to Pause,
  drag/scale finish to ObserveSizeChange and cached-position/type/multi-stretch
  changes to ForceReset. ObserveSizeChange/ForceReset clear requested measured
  geometry, which can trigger more position events.

The field now aliased layoutChangingFlow is asserted by the layout-changing job
and negated by the idle predicate. Its native diagnostic says "idle=true" while
asserting the blocker. Interpret its callers and predicate rather than the
wording of that one log.

ClockDataBindingHelper removes the old native blur callback and installs the
callback on the new bound clock. ClockPreviewItem applies window-blur capability
and desired blur separately during provider view replacement.

BlurItemController's per-item background views are another layer: it scales
visibleRect coordinates by PreviewScale, reconciles children by item ID, applies
SemBlurInfo and outline clipping. Clearing that container is separate from the
clock provider's glyph silhouette. BlurInfoFactory can build window blur (mode 0)
or canvas blur (mode 2); its preset canvas path uses canvasScale=4, while custom
curve blur uses canvasScale=1. Radius/color-curve inputs and coordinate space
must therefore be checked independently.

## 5. Event sequences and module boundaries

| Event | Native stages | Module stages that must agree |
| --- | --- | --- |
| Minute tick | Text changes; shader/ink update; fresh glyph draw; outline readiness; blur submission; native alpha/reveal | Retry generation cancels stale work; confirmed Natural minute fix defers a ready matte rebuild until the new glyph draw. |
| Media open | Expanded state; font collapse and spring callbacks; independent blur scale; palette and host fades | Fullscreen backdrop holds native host, player glyph glass starts later, cached fallback may paint. Ownership must be assigned for every frame, including the opening threshold. |
| Media close | Reverse media fraction and axes; independent palette/alpha animation; settled page state | Player glyph override is cleared, native host hold released and cached fallback removed. End-of-morph ink and host must be ready together. |
| Seconds open/return | Source and target clocks; capture/pre-draw; reparent/move; source removal; dashboard-specific blur fade | Handoff tracks actual target and its first submission, not any last-rendered clock. Continuity also intercepts native fade completion. |
| Enter/wake AOD | Doze fraction and lifecycle; page policy; font weight/axes; wake spring blocker; possibly a distinct AOD instance | Artwork snapshot/video lifecycle, clock visibility, active blur presentation, wallpaper depth and profile must follow the current instance/display. |
| Swipe/cancel | Parent fade/scale; alpha callback; restore springs | Blur drawable fading and clock host retention must follow requested opacity without multiplying it repeatedly. |
| Editor drag/settle | Measure-state and position flows; cancellation; idle gate; per-item preview backgrounds | Preview geometry, wallpaper crop and glyph blur use different spaces; settings refresh must not select a detached or outgoing clock. |

Reference runtime installation starts native blur/glyph synchronization before
font/runtime lifecycle hooks; player extras are installed later. This is source
installation order, not a guarantee of interceptor execution priority. The
offline hook inventory contains 298 literal hook-ID sites for the whole module,
not 298 clock hooks and not proof that all hooks execute on a particular APK.

The current ClockBlurContinuity still finishes some native reveal/strategy
animators after letting their methods run. This is active module behavior, not
untouched Samsung timing. Its interaction with palette interpolation and player
glass needs to be evaluated with the other writers before removing or bypassing
another animator.

The fullscreen replacement currently has explicit phase boundaries:

| Phase | Native host | Per-glyph artwork glass | Cached outline fallback |
| --- | --- | --- | --- |
| Opening below 0.50 progress | Held by the backdrop | Clear requested | Excluded by progress threshold |
| Opening at/above 0.50 | Still held | Apply when the clock/artwork bounds qualify | Can draw only when hasGlass is false |
| Settled expanded | Still held | Owns the player clock if active | Same conditional fallback |
| Closing | Held until saved state is restored | Clear requested; its own fade can outlast that call | Explicitly excluded while closing |

MediaClockContrast.hasGlass checks a snapshot specification and nonempty bounds.
hasArtwork additionally requires a hardware canvas, recorded RenderNode,
and artwork opacity above 0.005. Treat these as
different readiness states. A hasGlass result suppresses the cached fallback
before proving that the actual artwork fill can draw. The closing path has no
cached-matte fallback after the per-glyph clear completes. These are concrete
coverage boundaries to test, not proof that every reported blink has one cause.

### What the existing player trace establishes

The bounded trace from the preceding investigation shows successful six-path
native submissions during media/stretch movement, with the shared native host
held at alpha 0. The module's artwork renderer also has opening/closing thresholds
and an opaque contrast-ink pass. This proves competing presentation ownership
exists; it does not identify the final visible pass in every failing frame.

No new phone trace, renderer exemption or duration change was added in this
audit. Natural-player blinking and the previous preview's missing AOD clock are
not reported as solved.

## 6. Cross-version evidence

The targeted audit uses original DEX declarations, call prototypes and literals,
never JADX display aliases. See [audit rules](../../scripts/apk-audit/clock-ownership.tsv).
A match proves that exact role exists uniquely; it does not validate all field
bindings, dynamic eligibility or visual behavior. Missing a reference role is
not equivalent to a missing module feature.

### Provider scan

13 distinct supplied AOD/Clock Style APKs, 23 reference roles, 299 checks:
260 unique matches, 39 absent roles, zero ambiguous roles. Full identity and
role results remain in ignored .build/clock-ownership-audit/report CSVs.

| Package / version | SHA-256 prefix | Unique roles | Absent reference roles |
| --- | --- | ---: | --- |
| AOD 8.9.15.50 | `ca9197daf79a` | 8/23 | `media`, `axis_fraction`, `blur_entry`, `blur_enable`, `blur_clear`, `blur_host`, `blur_retry`, `blur_collect`, `ink_alpha`, `blur_reveal`, `dashboard_reveal`, `none_outline`, `path_color`, `text_outline`, `axis_spring` |
| AOD 8.9.28.18 | `02ae3a7b32ce` | 21/23 | `path_color`, `axis_spring` |
| AOD 8.9.32.95 | `9afd419f2cb3` | 21/23 | `path_color`, `axis_spring` |
| AOD 8.9.36.23 | `4dce599dcb0d` | 22/23 | `axis_spring` |
| AOD 8.9.36.23 | `ea8ad221a10c` | 22/23 | `axis_spring` |
| AOD 8.9.57.1 | `cb5b0bce22e1` | 23/23 | None |
| AOD 8.9.65.74 | `706a046f88b2` | 23/23 | None |
| AOD 8.9.65.81 | `077da1810d9c` | 23/23 | None |
| AOD 8.9.66.1 | `1d1b07db9855` | 23/23 | None |
| AOD 8.9.81.55 | `55557795719b` | 21/23 | `axis_fraction`, `blur_retry` |
| Clock Style 8.9.15.39 | `6579347551c4` | 8/23 | `media`, `axis_fraction`, `blur_entry`, `blur_enable`, `blur_clear`, `blur_host`, `blur_retry`, `blur_collect`, `ink_alpha`, `blur_reveal`, `dashboard_reveal`, `none_outline`, `path_color`, `text_outline`, `axis_spring` |
| Clock Style 8.9.35.60 | `5aabeee49653` | 22/23 | `axis_spring` |
| Clock Style 8.9.65.92 | `55cb7b857f86` | 23/23 | None |

The two 8.0 providers lack native clock-glass entry/readiness/outline roles.
This is expected: widget/Now Bar blur and inline seconds support do not imply
native clock glass. Earlier 8.5 NonePaddingTextView
does not declare the reference int-color override. Resolve renderer families
rather than assuming pathTextColor on every build.

9.5 has a three-argument font transition (FFZ)V instead of the reference (FZ)V
and synchronous native blur instead of the old retry entry. The existing
OneUi95Aod shim already resolves those differences. The absent reference scan
roles are not newly discovered unsupported features.

### SystemUI scan

| Package / version | SHA-256 prefix | Unique roles | Absent reference roles |
| --- | --- | ---: | --- |
| SystemUI 16 | `5843fc24c6ab` | 4/7 | `host_alpha`, `host_dark`, `host_shade_alpha` |
| SystemUI 16 | `a847ee77e09c` | 6/7 | `host_dark` |
| SystemUI 17 | `eebcad118579` | 7/7 | None |

The old SystemUI host shapes need separate contracts: the 8.5 sample lacks the
two-amount wrapper and the 8.0 sample additionally lacks the reference wrapper
alpha/shade forwarding shape. Their shared direct swipe and AOD/plugin routes
still exist. No new runtime binding was chosen solely from these scan results.

### Dressroom scan

9 distinct editor APKs, 6 roles, 54 checks: 41 unique matches, 13 absent
reference roles and zero ambiguities.

| Dressroom version | SHA-256 prefix | Unique roles | Absent reference roles |
| --- | --- | ---: | --- |
| 2.9.10.46 | `5bdbc6cea046` | 1/6 | `available`, `idle_write`, `animation_idle`, `position_idle`, `item_blur` |
| 2.9.50.90 | `030f4386d53e` | 4/6 | `animation_idle`, `item_blur` |
| 2.9.60.42 | `7a396000be89` | 4/6 | `animation_idle`, `item_blur` |
| 2.9.60.50 | `fa385c0aca13` | 4/6 | `animation_idle`, `item_blur` |
| 2.9.60.51 | `0fa224ac1b81` | 4/6 | `animation_idle`, `item_blur` |
| 3.0.10.32 | `1f7c1858d12d` | 6/6 | None |
| 3.0.10.38 | `2fb281188aec` | 6/6 | None |
| 3.0.10.38 | `f493af985544` | 6/6 | None |
| 3.0.50.60 | `8140af052028` | 6/6 | None |

The 8.5 samples share the idle-availability, idle writer and position-idle roles
but lack the reference internal-animation callback body and per-item view blur
shape. The stock 8.0 editor lacks five of these native clock-glass lifecycle
roles even though it contains a custom SemBlurInfo factory. A factory declaration
does not establish that the clock uses it. Both reference 9.0 binaries, the
supplied modified 9.0 beta and 9.5 beta match all six roles.

Across these three scans there are 25 distinct APKs and 374 applicable checks:
318 unique matches, 56 absent reference shapes and no ambiguous shapes. These
are deliberately narrow structural checks, not a claim of complete RE or
runtime feature certification for every APK.

### Reproducing the targeted structural audit

From the module repository, with JADX_JAR pointing to the local JADX jar:

```powershell
$apks = Get-ChildItem ../apk-collection -Filter '*.apk' |
    Select-Object -ExpandProperty FullName
./scripts/apk-audit/run.ps1 -Apk $apks -JadxJar $env:JADX_JAR `
    -Rules scripts/apk-audit/clock-ownership.tsv `
    -OutputDir .build/clock-ownership-rerun
```

Add stock 8.0, beta and SystemUI paths to the input array when those binaries are
outside the collection. The script needs a previously built .build/module.jar
for its existing scanner dependencies. Its default rule file is unchanged when
-Rules is omitted. The scanner handles all DEX 041 container sections. Match raw
prototypes; a method's JADX alias does not replace its original DEX name.

## 7. Remaining proof before the next rendering fix

1. For each media opening/closing threshold, identify which visible renderer owns
   digits, ordinary date/temperature text, weather icon and glass. Include the
   last interpolation frame and the first settled frame.
2. Relate player holds, native enabled-delegate changes, shader stop alpha and
   path ink alpha. Restoring a host without restoring the intended foreground
   cannot establish continuous glass.
3. Treat wallpaper-AOD and standalone AOD separately; bind state to the current
   holder and display. Do not use Natural as a renderer-switching flag.
4. If static call graphs leave ordering unresolved, use one bounded trace that
   records those specific writers and instance identities. A broad phone dump
   or successful blur result alone does not answer that question.

The new aliases and this map make the next fix reviewable without repeating
blind enable/alpha changes.

