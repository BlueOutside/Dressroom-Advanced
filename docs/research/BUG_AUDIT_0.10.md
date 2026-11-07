# Rendering and lifecycle audit - 0.10.4 alpha

Installed versionCode 75. Research uses the current phone's AOD APK through JADX MCP, targeted classes, the local module source, Android platform probes, and ADB recordings. This is a device-specific alpha audit, not a compatibility certification.

## Verified with the user

- **Now Bar empty-space drag:** Samsung's stack bounds include empty space above the visible pills. Its gesture handler accepts DOWN there. The module now checks each visible card's normal-content bounds at DOWN, with native touch slop. Gestures starting on a pill retain native dragging; expanded/settings/expanding cards retain their native surface. The user confirms that dragging from the fingerprint gap no longer works and ordinary pill interaction remains usable.
- **Media collapse:** the user confirms smoother bottom-blur fading and widget reveal in 0.10.0. Controls blur opacity follows native control alpha and spring progress. Both widget content outlines and their separate native blur submissions exclude the moving rounded cover; they are revealed as it clears their position.
- **Native palette bypass:** the initial adaptive text-only change affected temperature, but not digits or weather glyph. JADX identifies a second shader pass in `ClockNonePaddingTextView.onDraw` and a separate outline color in `NonePaddingTextView.Q`. 0.10.2 scopes a temporary paint/outline adjustment to that native draw, while weather uses its image color filter. The user confirms the clock and glyph now adapt. Native state-animation colors remain authoritative outside the scoped draw.
- Prior 0.9.7 collapse foreground and manual-height restoration are user-confirmed. Those paths are preserved.

## Current contrast behavior

`MediaClockContrast` samples a cached, owned image of at most 96×96 pixels. Sampling follows the center-cropped artwork under each text/icon's screen rectangle. Linear-light luminance chooses black or white, with a hysteresis band to avoid rapid switching near the contrast threshold. Hardware artwork is copied once per bitmap/generation, not read on every frame.

0.10.4 uses 220 ms native ARGB animators for palette changes and returning text to its saved/native appearance. Return starts at native collapse, rather than waiting for the artwork to disappear. Reversals cancel the previous animator without running its obsolete cleanup. The native gradient, outline paint, and normal palette are restored. Weather filters with a readable native tint can also fade back; unknown filter types restore their original filter directly. The user confirms smooth return and restored depth without tapping or screen cycling.

Wallpaper depth is suspended for clock views while the media cover is active: the wallpaper subject must not punch holes into a clock over different artwork. On media exit, the module invalidates the clock and digit display lists, drops their local geometry caches, and rebuilds native blur against the restored placement. Detached tracked clocks are also released from the temporary suspension. This addresses the user-reported random cuts and stale post-player depth in 0.10.3.

The earlier experimental clock-size increase was removed following the user's clarification. Native media clock sizing and geometry are unchanged. Clock-shaped GPU blur samples the cover rather than the original wallpaper; this does not reproduce Samsung's full nonlinear color curve. Highly detailed covers and arbitrary multicolor clock palettes still merit visual checks.

## Other corrections in this audit

| Area | Finding and change | Evidence / limit |
| --- | --- | --- |
| Artwork touch | Native artwork frame remained an invisible launch target. In fullscreen mode its non-title tap now calls the native close action; the title keeps Samsung's secure launch action. Controls retain native interaction. | Closing through the old artwork area recorded on phone; title-to-app action awaits explicit user confirmation. |
| Depth bitmap ownership | Identity crop can return Samsung's preview bitmap. Detection now owns a copy. Mask encoding draws onto a separate neutral-white alpha bitmap, preserving SDK output even for equal dimensions. | Six Android mask/ownership checks pass; the shared preview-copy case was reviewed in code, not exercised by the standalone probe. |
| Detection concurrency | The Object Capture singleton could be initialized/captured/released by overlapping threads, and older requests could update a later session. | Serial executor, request+session tickets, success/error gating, main-thread delivery, and editor-destroy invalidation. Rapid detection/cancel still needs phone interaction testing. |
| Bad mask payload | A failed PNG decode could leave the previous subject active. | Invalid payload now removes the old entry and refreshes native clipping. |
| Settings restart | Provider failure could clear a loaded clock profile; Extras could repeat IPC every animation frame. | Runtime clock retains its profile on exceptions and uses bounded retries. Extras retains last-known values with backoff. Provider startup publishes a refresh notification. Extended restart/boot testing remains open. |
| Media/doze restore | A media-close restore deferred while dozing could remain pending. | Retry on the next doze update as well as attach; native keep-stretch choice remains respected. |
| Charging animation | Cancelling the removal animator permanently removed native listeners, potentially affecting reuse on another plug event. | Listeners are detached only around cancellation, then restored. Repeat unplug/replug remains unverified. |
| Display scope | Main-screen profile lookup also admitted SUB_UI; widget depth/placement did not consistently reuse native cover exclusions. | Main clock profiles are limited to LOCK_SCREEN/AOD on display 0; widget ownership uses the existing native cover/informative guard. |

## Native anchors

- `NowBarCardStackView.dispatchTouchEvent/onInterceptTouchEvent`, native expanded/settings accessors `k/n`, raw expansion flag `U`, and each card's `getNormalContentRootView`.
- `aod.fa2`: artwork/title/control owners, bitmap `G0`, native model `J`, animation flag `T0`; `FaceWidgetMusicViewModel.m` closes the expanded native card.
- `aod.n82`, `NowBarMediaCardView.g0/v0/x0`: native expansion/collapse overlays and cleanup.
- `aod.tx0.b/c/d/e`, `aod.e70.a`: native glyph blur path, host and screen origin. `aod.qb0` applies Samsung's saturation/brightness color curve.
- `ClockNonePaddingTextView.onDraw`, `NonePaddingTextView.onDraw/setTextColor`, and `widget.text.a` state animation: shaders and outline color explain why ordinary TextView color was insufficient.
- `MediaExpandStateObserver.c/g/h`: native sizing/pivot/spring-adjacent clock transforms; inspected, not overridden in the final build.

## Validation

- Build succeeds with the existing signing key; APK v3 signature verifies.
- SystemUI reload passed the rendering guard after installation. No AOD force-stop was used.
- 66 existing JVM checks pass: reflection 6, identity locks 6, lifecycle 18, widget layout 24, deferred discovery 12.
- Android probes: 16 Region geometry checks, 7 platform clipping/blur groups, 6 mask encoding checks, 4 artwork-local luminance checks pass. The app_process fake-View test could not initialize Samsung's framework shared-memory context and is not counted as passing.
- Recordings: `work/audit-media-new.mp4` (old artwork close, cover reveal, manual restoration), `work/audit-pill-touch.mp4` (gap/pill gestures), `work/audit-contrast-fade.mp4` and `work/audit-contrast-104.mp4` (palette/AOD interaction traces). The last recording primarily captures wake/expansion; final close behavior is user-confirmed. Recordings are relative to the workspace, not this directory.

## Remaining work

- User-observed residual blur refresh on a digit change or after entering/leaving the clock view. Depth and return colors are confirmed fixed; the remaining native blur redraw is tracked separately and is not claimed fixed.
- Cyan/raised subject edge: cause remains unproven. Soft alpha versus thresholded native geometry, provider extraction spill, and blur sampling need isolated comparison. This audit does not claim the rim is fixed.
- Samsung Object Capture quality, thin/disconnected/multiple subjects, transparent/video/live wallpaper, failed SDK/library availability, rapid crop/rotation/Apply, and allocation limits need broader testing.
- Foldable cover/informative layouts are guarded from main profiles. Tablet/foldable main-display landscape behavior, A-series devices lacking blur/Object Capture capabilities, and One UI 8.5/9 obfuscation changes are **not verified**.
- Updating the APK does not hot-replace code already loaded into scoped processes. Process reload/reboot is still needed; retry/refresh changes address temporary provider gaps, not arbitrary mixed-version execution.
- Native title launch, interrupted/reversed animations, media output dialog, artwork changing while expanded, unknown weather filter types, and fade with custom gradient/outline fonts still need broader checks.
