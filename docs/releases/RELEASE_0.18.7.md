# Media transition ownership - 0.18.7 alpha

Version code 124. **Rejected by the user: both original bugs remain, thumbnail return regressed, and the clock now bounces vertically. Superseded by the next candidate.** The build and 634 Android checks passed, but did not establish visual correctness. Its fraction interception, forced direct media frames, and thumbnail-only foreground crop are reverted. The existing native height spring is restored.

## Why the previous repairs were unreliable

The reference AOD has two independent timelines. `FaceWidgetClockPage.T0` drives a 300 ms clock animator, and `X0` receives its animated fraction. `W0` reports the artwork expansion animation. Native `A(doze, fraction)` uses the artwork flag to decide whether to accept the fraction. Treating these as one timeline can replace an in-flight clock fraction with a binary end state. The earlier workaround also interpreted intermediate fractions outside an active clock animation, and a separate module animator competed with Samsung on close.

The new hooks use the actual page animator while it is started. Only within that page update does `AbsClockView.K0` receive `disableAnimation=true`: the supplied fraction is already animated, so starting its height spring as well ignores the timeline. Outside that update, native/AOD behavior remains. Media-exit restoration waits for the page animator instead of an unrelated controller spring. The fixed-duration opening exemption, competing return animator, forced exact measurement/layout, temporary page clip and delayed blur rebuild are removed.

`nr0.g(font)` computes axes but does not assign `nr0.e`. Runtime restoration now restores the controller's source font as well as its axes. The native font setter can skip this when the restored font already equals `T0`. This is checked at settings restoration and before media expansion; it is not an extra per-frame settings read. The measurement calculator uses that same source font's ID, matching the native full-font setter, rather than the deprecated integer-font field. The reference controller repair is excluded from Clock Style's adapter.

## Foreground artwork strip

Our root-overlay bridge painted the bottom of the whole returning card, including still-visible controls. It now uses the final thumbnail center and size passed to native `NowBarMediaCardView.g0`, transformed from its overlay host into shade coordinates. The bridge is circular and cannot cover the rest of the card. The main fullscreen artwork remains behind the controls. Native early returns that do not reparent the small artwork no longer activate collapse overlay ownership.

The previous safety guards and live native clock-blur drawable ownership remain. The placeholder launcher remains removed. No settings reset or migration is required by this change.

## Checks and limits

The media regression renders the actual return-layer view into an Android bitmap and checks thumbnail coverage, rounded clipping, and clear pixels over controls and elsewhere on the card. It also covers both directions of clock progress, stale settled fractions, doze precedence and native animator cancellation/replacement.

A pre-change trace showed interpolation, but the user had already touched the clock to temporarily repair it. That trace is a repaired baseline and does not reproduce the cold-update failure. A fresh SystemUI process must be tested without editing or reapplying the clock. Tablet/8.5 media behavior is not newly certified.
