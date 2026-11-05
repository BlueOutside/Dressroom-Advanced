# Artwork and stretch rendering reuse — 0.14.0 alpha

VersionCode 105. Installed; SystemUI started under PID 19404 with all optimization hooks present. **The user confirms the cover/clock transition feels smoother.** The compact-clock flash remains resolved by 0.13.11's live blur-host handling.

## Changes

- Contrast sampling keeps an immutable array of the same at-most-96×96 artwork pixels instead of repeatedly calling Bitmap.getPixel. A 256-entry linear-light lookup replaces repeated Math.pow calls. The 32 sample positions, arithmetic order, thresholds and 220 ms color fade remain unchanged.
- Temporary contrast rectangles, coordinate arrays, identity sets and snapshot lists are reused. Lists/sets are cleared in a finally block, so thread-local storage retains no clock/window views between calls. Reentrant work uses separate scratch storage. Temporary software sampling bitmaps are recycled after copying their pixels.
- Artwork rendering reuses control/cover/mask geometry. A clock-shaped artwork mask is rebuilt when its native source, visibility or root transform changes, rather than for every movement of the album cover. Bitmap generation changes invalidate artwork contents independently of widget-cover geometry.
- Exact duplicate native glyph font requests are skipped only if font ID, complete axis string, current Typeface identity and native fixed-font fields still agree. Native standalone typeface updates/configuration changes invalidate that cache. Changed axis values are not rounded or skipped. Native h/i methods and their known callers were read through JADX MCP before adding the optional hooks.
- Native ClockNonePaddingTextView draws a gradient glyph twice, once for shadow and once for color. A draw-scoped memo reuses the first generated outline for an equivalent second request using Android's Paint.equalsForTextMeasurement. Both drawing passes remain. The memo resets after every draw and rejects changed text, range, origin or measurement attributes. Pure-color/contrast-overridden single-pass drawing bypasses the memo.

No blur-radius/resolution changes, spring-duration changes, frame skipping, per-digit stretch approximation, depth simplification or settings migration. The GPU RenderEffect pipeline and 0.13.11 BackgroundBlurDrawable fix remain.

## Validation

Build/v3 signing and **361 Android contract checks** pass: 222 previous checks plus 139 sampling/font-key checks, including 128 bit-exact comparisons against the previous contrast formula under different crop/position scenarios. Eight separately run native checks pass: the four Samsung glyph-cache contracts and four actual Android Paint outline-reuse checks covering shadow/shader/color changes, exact geometry and size/typeface invalidation. The temporary instrumentation APK was uninstalled.

Isolated 20,000-call contrast benchmarks on the phone:

| Run | Previous Bitmap/pow | Cached pixels/LUT |
| --- | ---: | ---: |
| Initial | 62.95 ms | 2.05 ms |
| Full regression run | 107.03 ms | 4.48 ms |

These measure only the sampling method, not animation FPS. Baseline native logs contained 1,405 font requests and 139 repeated logged text/font/axis tuples; this is an opportunity estimate, not a per-view hit count, because native logs lack view identities.

## Frame observations and limits

Android gfxinfo was reset before each user exercise. The user performed open/close cycles on both builds. The windows also include idle/system frames, the process was restarted for the candidate, and native logs show unequal expanded holding times. This is not a controlled transition-only benchmark.

| SystemUI statistic | 0.13.11 baseline | 0.14.0 |
| --- | ---: | ---: |
| Frames in observation window | 2,613 | 1,652 |
| Missed-deadline frames | 131 (5.01%) | 98 (5.93%) |
| Median frame | 13 ms | 10 ms |
| 95th-percentile frame | 25 ms | 29 ms |
| 99th-percentile frame | 44 ms | 53 ms |
| Median GPU | 7 ms | 4 ms |
| 95th-percentile GPU | 14 ms | 11 ms |
| 99th-percentile GPU | 16 ms | 13 ms |

The user reports smoother motion and GPU/median observations are lower, but frame-tail statistics are mixed. No overall jank reduction or fixed FPS gain is claimed. Further work should use a controlled Perfetto/frame-timeline capture to separate native font/layout cost from GPU filtering and startup frames.

Artifacts: `work/media-perf-01311-baseline.txt`, `work/media-perf-0140-after.txt`, `work/media-perf-0140-probe/results.txt`, `work/media-perf-0140-native-probe.txt`. No fatal exception or unavailable optimization hook appeared in the inspected new-process log. USB keep-awake remains at its original 0. Other devices and the stock non-fullscreen player are not newly certified.
