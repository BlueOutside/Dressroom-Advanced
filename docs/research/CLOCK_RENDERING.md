# Clock rendering and shader optimization

Inspected the reference One UI 9 AOD 8.9.66.1 through JADX MCP and a local DEX
reference scan. These findings concern the normal stretchable digital clock.
They do not describe every motion-clock style or the vendor compositor internals.

## Rendering path

`ClockNonePaddingTextView.onDraw` draws a shadow pass and a foreground pass when
its paint has a shader. `NonePaddingTextView.onDraw` generates a glyph path with
`Paint.getTextPath` when blur-path mode is enabled.

`getPathForBlur` returns an offset copy of the stored glyph path, or constructs a
path from the current text paint. The combined blur strategy copies and transforms
the visible glyph paths, merges weather geometry, builds `SemBlurInfo`, and calls
`semSetBlurInfo` on the native blur host. The clock's background blur is submitted
through that Samsung API, rather than loaded from an editable AGSL asset here.

The APK also contains AGSL weather assets, SESL effects, and Now Bar media
lighting/masking shaders. Motion-clock styles have separate GLSL assets. A shader
being present in the APK does not establish that the current clock uses it.

## Existing optimizations

`GlyphDrawReuse` already reuses identical outlines within one native draw call.
Its memo is reset before and after the draw. `GlyphFontCache` skips identical
native typeface requests. Changing stretch axes still requires fresh geometry.

## Candidates to measure

- Retain an exact outline cache across unchanged frames, with explicit font and
  configuration invalidation. Do not reuse outlines after text/metric/axis changes.
- Combine path scaling and translation to avoid intermediate paths and matrices.
- Check duplicate blur submissions and native blur-host coverage with a frame
  trace before attempting to reduce compositor work.

No CPU/GPU timings were collected during this inspection. The allocation and
copying sites are confirmed; their share of animation cost is not. Do not cache
blur submissions across media/doze transitions without proving all invalidation
paths, since stale compact-clock blur previously caused visible artifacts.

## Framework continuation

The connected S26 Ultra's `framework.jar` contains the rest of the Java blur
implementation in `View`. `SemBlurInfo` selects window (0), captured bitmap (1),
or canvas (2) blur. These paths must not be treated as interchangeable.

- Window blur updates `BackgroundBlurDrawable` and its compositor region data.
  Radius/color-curve setters avoid some unchanged updates, but clip-rectangle
  and clip-path setters notify the aggregator unconditionally.
- Canvas blur records a background `Picture`, discards/re-records a render node,
  and applies a filter before clipping the output. Expanded capture and optional
  downsampling have different edge behavior. A smaller capture needs enough
  background padding for the blur kernel.
- On the newer image-filter backend, `RenderEffectImageFilter` uses
  `ShaderAssembler`. This is where framework AGSL color/effect code is assembled;
  it is not an AOD shader asset. The ordinary `BlurFilter` itself uses
  `RenderEffect.createBlurEffect`, with the supplied radius divided by 2.25.
- The assembler already caches its render effect and checks whether size or
  parameters changed. A call to `build()` does not establish recompilation on
  every frame. Shader rewriting needs evidence beyond those call sites.
- `SemBlurInfo.scaleDownRadius()` mutates its stored radius. Reusing or resubmitting
  that object under canvas downsampling needs care to avoid repeated scaling.

This establishes additional optimization and visual-consistency candidates, not
a measured diagnosis of GPU cost. Window blur's lower renderer implementation is
beyond these Java classes. No framework or rendering hooks were changed here.


## Neutral clock blur readiness follow-up (2026-11-24)

AOD 8.9.66.1, inspected in JADX: `AbsClockView.onTimeChanged` starts the native
blur reveal before updating glyphs and calling `setClockTextBlur`.
`ClockBlurManager.applyTextBlurWithRetry` applies text alpha 127 when glyph paths
are not ready, then changes it to the configured target on success. Cached-path
views also return the previous outline until their next draw. `getPathForBlur`
returns a copy, so the strategy's in-place scaling does not corrupt that cache.

Our readiness hook queued an after-draw retry but still ran the premature native
submission. With Neutral clock blur enabled and an already visible, nonempty
window blur surface, it now keeps that surface and submits after glyph draw.
Repeated requests coalesce to the latest ticket. First render, hidden surfaces,
unsupported hosts and other blur modes retain the native submission/retry path.
Host discovery uses the existing zero-argument ViewGroup signature; there are no
new obfuscated-name dependencies or animation-duration changes. This targets the
reported minute/media refresh flash, not every possible transition cause.


User confirmed the minute-change flash is gone in preview6, but blur disappears
through player stretch movement. An ALPHA_ANIMATING policy exception in preview7
did not fix this and was removed.

A bounded clock-only trace of one player open/close cycle showed successful blur
submissions throughout MEDIA_EXPAND_ANIMATING and STRETCH_ANIMATION, with
`keep=true`, but the `lock_clock_blur` host alpha was 0. No native clear caused
that interval. The module's fullscreen Backdrop Saved.apply hid the host from
opening start, while MediaClockContrast was explicitly cleared during closing
and the first half of opening. This left neither native nor replacement glass
visible.

An initial host-opacity handoff correction did not resolve Natural mode and was
removed. The next confirmed layer is MediaClockContrast: its glyph draw hooks
force an opaque contrast-ink fill. That conceals the native blur even when its
host is restored. Natural mode was never exempted from the player reskin.

Natural clock blur keeps Samsung's native clock/weather renderer in the awake
player. MediaClockContrast does not install a per-song glyph override for
that setting; the fullscreen and legacy artwork paths release their native-clock
blur holds, and the fullscreen cached-outline fallback is skipped so it cannot
draw a second clock. Other clock appearance modes keep the existing player
reskin. The minute-readiness correction remains unchanged. No native motion
blocker was relaxed. Temporary trace code was removed; evidence is under ignored
.build/widget-tint-audit/cycle-complete.log. User reported that this reduced the missing-blur interval to the ends of the
morph, but removed the player clock on doze. The follow-up uses the tracked
native clock dozing state to retain artwork-backed rendering on AOD. It also
restores the manager's own configured glyph opacity after palette recollection,
before glyph draw. Otherwise that recollection can record opaque ink for one
frame before an after-draw blur submission restores translucency. Opacity targets
and their setter are signature-resolved and optional; seconds-view return fades
remain excluded. The corrected preview still needs visual confirmation.


### Baseline restored before further RE

Natural-clock preview3 did not fix the end-of-morph flash or the missing player
clock on doze. All player-renderer exemptions and pre-draw opacity experiments
above were removed. `2.0.6-blur-baseline` (20006033) retains only the user-confirmed
minute-change readiness correction, plus the widget appearance work. The
player-animation blink remains unresolved. Preserve this distinction when
continuing: successful blur submissions do not prove a visible, correctly blended
clock frame. See CLOCK_LAYER_HANDOFFS.md for the next ownership audit.

The resulting reference AOD, SystemUI and Dressroom pass is recorded in
[CLOCK_OWNERSHIP_MAP.md](CLOCK_OWNERSHIP_MAP.md), including player renderer
thresholds, independent alpha authorities and older-provider shape differences.

### Readiness-based player handoff preview (2026-11-24)

`2.0.6-clock-handoff` (20006034) keeps the modern player's native clock host at
Samsung's requested opacity until replacement artwork has a recorded RenderNode,
valid bounds/clip and positive opacity, and a glyph has actually consumed it.
Palette availability alone does not hide the native host. A lost/discarded frame
restores the native fallback. Per-window ownership is weak and released when
the player restores its saved views.

The modern closing/thumbnail path keeps its existing clock-glass renderer
through the return rather than starting its independent 220 ms clear at collapse
start. Completed return drops the overrides immediately while handing native
opacity back. The clock-hide option remains authoritative. Natural remains an
appearance option; no AOD renderer exemption or native blur blocker was added.

Both artwork implementations publish frames from glass demand rather than
mistaking an already-rendered frame for demand. The 8.5 native compositor clear
and replay workaround is preserved, because it prevents expanded cover loss.
Returning snapshots now remain alive until weather icons as well as text finish
their fades; the last icon also prunes the snapshot.

Validation: 30 JVM suites, including 167 media checks; on the reference phone,
24 actual RenderNode/glyph handoff checks, 61 glyph-sync checks, 91 blur-appearance
checks and 78 media-return checks. The media-return fixture was updated for the
existing HolderContract constructor. No provider signature changed. Visual
player opening/closing and fullscreen AOD confirmation is pending.

## Native animation and path-color preview (2026-11-24)

Preview: `2.0.6-clock-animation` (20006035), following the readiness-based
player handoff above. Findings and native contracts are recorded in the
[animation-engine audit](CLOCK_ANIMATION_ENGINES.md). Existing Mistral aliases
and hidden-feature research are preserved.

Removed ClockBlurContinuity's forced ValueAnimator.end calls for native reveal
and combined transitions. Native frame, cancel and end listeners now retain
their timing instead of committing text/background alpha while font/page motion
continues. Alignment subscribes to the newly started fade and requests a fresh
outline on actual completion. Returning seconds clocks still avoid restarting
an already active dashboard fade.

During a draw-barrier rebuild on an attached, visible clock with an enabled,
already live native blur surface, ClockBlurRetries can use Samsung's force
argument to refresh geometry while reveal runs. This requires the skip-running
anchor and ValueAnimator.isRunning call in the submit body. Missing/unknown
contracts retain native timing and completion-based alignment. It does not
enable blur against a native visibility/blocker decision or end an animator.

Added a scoped cached-path color correction. Affected combined-transition
frames/finalizers read the path's stored ink rather than a stale TextView color.
Both callback and field bindings follow prototypes, DEX reads/writes and
constructor references. The 8.5 merged callback's Object captures are checked
against the strategy type and exact current animator; multiple matching owners
are rejected. A glyph must belong to that strategy and have path rendering on.
Nested callbacks and exceptions restore the preceding thread-local scope.

Older providers inheriting the TextView setter and 9.5's already corrected
frame/finalizer retain their native color policy. No obfuscated runtime class,
method or field name was added. The getter correction neither changes saved
palettes nor globally replaces TextView's color state.

Validation:

- Signed APK build and all 30 JVM suites passed.
- Reference-phone probes passed 21 color-scope/owner checks, 62 glyph-sync and
  native-animator checks, 24 RenderNode handoff checks, 91 appearance checks and
  78 media-return checks (276 checks total).
- Read-only runtime binding checks passed for all 11 local native-glass
  provider APKs: both 8.9.36.23 variants, AOD 8.9.28.18/8.9.32.95/8.9.57.1/
  8.9.65.74/8.9.65.81/8.9.66.1/8.9.81.55 and Clock Style 8.9.35.60/8.9.65.92.
  Eight resolve the two stale writers; the two earlier inherited-setter builds
  and 9.5 resolve no correction. Stock 8.0's absent native-glass path remains
  unchanged; these checks do not certify rendering on all devices.
- The preview was installed on the reference phone. Visual Natural-blur
  opening/closing, seconds return and fullscreen AOD confirmation remains a
  user check; passing isolated probes does not establish a blink-free animation.

## Cover-progress clock return (2026-11-24)

Follow-up preview: `2.0.6-clock-return` (20006036). The reported fullscreen
collapse held a dark clock until the end with Natural blur off, most noticeably
on one static-cover track. Choosing dark player ink on bright artwork is valid;
retaining it across the entire return to the wallpaper is not.

Fullscreen collapse now restores the current native ink and glass with the
actual remaining cover progress. It retains the glyph renderer through the
motion, but blends its emphasis down and restores the native host alongside it.
Return starts from the current appearance, including a partly completed opening,
and reads the latest native palette. It does not start another fixed color timer.
The common 8.5 native wallpaper-fade return uses the same helper.

Cached clock paths use an opaque glyph mask, so their fill must preserve native
ink alpha on return. Ordinary TextView and icon masks already contain their ink
alpha and keep the corresponding single-alpha composition. Pending palette
application is removed at collapse start so a late sampling result cannot restart
the player tint during return. Palette selection and the fully expanded player
look remain unchanged.

The handoff probe now covers a dark player tint returning to translucent native
ink, intermediate native-host opacity, updated palette targets, pending sample
removal, cached-path alpha and final cleanup. Isolated native tests do not certify
the reported track's visual animation; that confirmation is a separate check.

Validation: signed preview build, all 30 JVM suites, and 294 reference-phone
checks (42 handoff, 78 media return, 21 color scope, 62 glyph synchronization,
91 blur appearance). The media-return probe ran with the phone awake because
its native collapse fixture intentionally follows the interactive return path.
Preview installed; Dressroom, AOD and SystemUI reloaded for user confirmation.
