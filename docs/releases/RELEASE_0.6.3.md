# 0.6.3 - final widget blur submission and startup stretch restoration

0.6.2 did not fix the widget background problem. The user reported that backgrounds move behind the subject while entering full-screen AOD, then move in front as the clock stretches on screen-on. The previous helper/builder hook is replaced, not treated as verified.

The new hook intercepts `View.semSetBlurInfo` for complication background views. It retains the native unmodified info, makes a separate copy of all its instance fields, and subtracts the subject from its clip path. If there is no native path, the clip starts from the native corner radii. The original instance and its path remain unchanged, even if Samsung shares them. Geometry changes resubmit the original info through the hook; native null submissions remain null. This covers transition submissions without forcing blur back on when Samsung disables it. Other clock and unrelated blur surfaces are excluded.

## Saved settings observation

The module preferences were backed up to `work/settings-after-062.xml`. The active `wallpaper_17_2_803` record still contained depth enabled, manual enabled, a mask, and five saved stretch heights. Samsung's native clock record also contained those heights and saved position/scale. SystemUI's dump nevertheless showed its startup `updateClockFont` passing `fontHeights=null`.

Runtime font hooks now copy the native font with the saved heights before rendering, preserving its font, path, weight and rounded corners. Attachment/profile updates also repair a missing runtime font handoff without requiring Apply or toggling depth. Manual validation accepts finite positive independent heights, matching the editor. Depth also establishes manual ownership during editor restoration when the old manual mirror is absent. This addresses a demonstrated missing stretch handoff; an independent position reset has not yet been reproduced or separately fixed. No saved profiles are deleted or migrated by this update.

## Validation

The signed 0.6.3 APK built and passed v3 verification. All 30 Java reflection, identity, lifecycle and saved-height checks passed. The on-phone probe checks actual Samsung blur copying (all native fields preserved, original path isolated), native rounded corners plus subject subtraction, installed provider font copying with nullable style fields, and the existing clock blur signatures.

The user confirmed after reload that saved clock placement, manual stretch and depth restore correctly before reapplying. Widgets still retained stale content fragments across screen-off/on transitions; 0.6.4 addresses that separately. Existing imperfect mountain segmentation and the deferred Dressroom main preview remain recorded limits.
