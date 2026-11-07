# Keep the native clock blur drawable live - 0.13.11 alpha

VersionCode 104. Installed; SystemUI started normally under PID 28804 with the runtime provider and artwork hooks. **User confirmed: “Flash is gone”** after leaving the full player open and closing with the usual physical tap. USB keep-awake remains at its original 0.

## Captured cause

0.13.9's native cache guard passed its native reproduction but did not remove the compact-clock flash. 0.13.10 added temporary bounded observations, and the user reproduced the long-open physical close. The resulting trace is `work/media01310-native-return.txt`:

- At return +0 ms, the visible LOCK_SCREEN clock measured 1053×565.
- At +785 ms, our artwork layer restored `lock_clock_blur`'s actual `com.android.internal.graphics.drawable.BackgroundBlurDrawable@3d4fae2`, alpha 255. While wrapped, the saved drawable's bounds had become 0×0; our replacement occupied the full 1440×3120 host.
- At +823 ms, the same clock owner submitted the correct full-size path `RectF(218.48518,279.06418,1221.331,1511.9427)` at scale 1.0, with clock size 1053×1295. The restored native drawable was again full-screen.

Thus our restoration exposes the saved compact blur drawable before the fresh stretched outline is submitted. The separate native blur state explains why the large clock's effects return later. The traced interval is 38 ms before the new submission; the earlier video showed the compact output for roughly 64 ms including presentation timing. No newly submitted compact path or alternate owner appeared in this captured return. This evidence does not certify every possible player/device path.

## Correction

The artwork layer no longer wraps or replaces the `lock_clock_blur` background. It temporarily suppresses the host's view alpha, retains native alpha requests while held, and releases it using the latest requested native alpha. The actual native blur drawable stays attached and can be cleared/rebuilt by Samsung throughout the transition. In particular, finishing our artwork layer cannot reattach the old compact drawable.

Other artwork/control backgrounds retain their existing behavior. Samsung's height animation and the previous palette/depth restoration remain. 0.13.9's native glyph submission guard remains because its cache defect was reproduced against the real Samsung class. Temporary 0.13.10 tracing was removed from the source.

## Verification

Build and v3 signing pass. All **222 Android contract checks** pass, including ten new checks for host suppression, latest native alpha, repeated holds, untouched drawable identity/callback/bounds, preserving current drawable state, native clearing without resurrection, and unrelated/released views. A test initially assumed ColorDrawable alpha round-tripped as exactly 17; it was corrected to compare the actual updated drawable state before and after release. The implementation did not need an alpha change for that test.

The four actual Samsung glyph-cache checks from 0.13.9 remain applicable to unchanged readiness code and are recorded separately. No animation FPS result or visual resolution is claimed from the contract tests. The temporary native probe app has been removed. No preference migration or wallpaper edits.
