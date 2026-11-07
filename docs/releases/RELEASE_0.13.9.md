# Native glyph-cache submission guard - 0.13.9 alpha

VersionCode 102. Installed and exercised under SystemUI PID 15834. **User verification failed:** the compact clock still flashes. The native glyph-cache experiment is valid, but the submission guard does not resolve the full media-return artifact. The user describes a transparent/effectless large returning clock followed by a briefly finished compact clock and finally the correct large clock.

## Native investigation

JADX MCP confirms that `NonePaddingTextView.R` resets on text changes, but not font/size changes. `ClockNonePaddingTextView.getPathForBlur` trusts R and returns U, the outline cached by onDraw when S is true. Media blur resumes through the coordinator's independent 500 ms media / 50 ms stretch debounces. Palette updates and the manager's 8 ms retry do not necessarily traverse `AbsClockView.setClockTextBlur`.

A separate instrumentation APK tested the actual Samsung class from the installed AOD package, with Android's normal initialized fonts. After a native glyph was drawn at size 80, increasing its size to 160 left R true and returned exactly the old outline, with the view dirty and layout requested. Drawing updated the outline to twice the height:

```
BEFORE      RectF(2.8710938, 0.24609375, 172.79297, 60.070312)
AFTER_SIZE  RectF(2.8710938, 0.24609375, 172.79297, 60.070312)
AFTER_DRAW  RectF(5.7421875, 0.4921875, 342.58594, 120.140625)
```

This reproduces a real stale-ready native cache and validates the guard against it. It does not by itself prove that this is the only source of the recorded media-return flash. Results and reproducible harness: `work/native-glyph-cache/`. The temporary probe APK was uninstalled afterward. Initial headless attempts failed during font initialization; they were not counted as passing tests.

## Change

- Replace the high-level setClockTextBlur deferral with a runtime-only guard at `cq0.c(int,int,boolean)`, the void submission method reached by both normal manager calls and retry runnables.
- For shown, attached clocks, defer when a shown native cached-path glyph is dirty, has layout pending, or has not drawn. Preserve native readiness fields; do not manufacture a ready path.
- Coalesce work per clock and resume after its child draw, carrying the latest original color, retry count and force flag. Hold the manager weakly and verify it is still the clock's manager before resuming.
- Deoptimize the two confirmed direct callers (`cq0.b` and `yp0.run`) plus the public/palette entry points to retain hook coverage.
- Remove 0.13.8's unsupported S-false reactivation guard. Keep the existing native height spring, contrast/depth return behavior and native-outline cleanup. The new manager guard is not installed in Dressroom's editor process.

## Verification and limits

Build and v3 signature pass. All 212 Android contract checks pass, with the eight old readiness-model checks replaced by dirty/layout/readiness/uncached-view checks. Four additional checks pass against the real Samsung text-view cache and new guard: **216 checks total**, with explicit separation between isolated contracts and native cache behavior. No new FPS improvement is claimed.

The runtime logs confirm installation of the native submission guard and provider hooks. Startup screenshot `work/media0139-startup.png` shows the saved tall clock, weather and widget depth restored on the AOD. No native-readiness errors or AndroidRuntime fatal exception appeared in the inspected new-process logs. USB keep-awake remains at its original value 0. No wallpaper edits or preference migrations were performed. The original 0.13.8 fallback APK was recovered from the previously pushed on-device candidate before installation.

The user's physical long-open collapse remains the acceptance test; earlier Back-only tests did not reproduce every physical-tap variant.
