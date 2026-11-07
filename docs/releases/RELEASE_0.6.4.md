# 0.6.4 - widget content clipping through AOD transitions

The user confirmed in 0.6.3 that saved placement, manual stretch and depth restore correctly without reapplying. Widget rendering still fails: partial widget UI remains from the cutout during AOD, then its background returns over the mountain on wake-up.

The previous runtime widget mask was drawn into `grid_layout.dispatchDraw`. That recording could remain cached while parent transforms changed. The geometry observer resubmitted blur backgrounds but did not invalidate the grid recording. This is a concrete redraw gap; the user-visible root cause still requires validation of the replacement.

Runtime widgets now use `WidgetContentClip`, a subject-shaped outline clip on the widget grid's render node, instead of recording a DST_OUT hole into the grid canvas. Individual Samsung blur backgrounds also receive an outline clip, alongside the existing final blur-info path subtraction. The clock and Dressroom editor canvas paths remain as before.

The pre-draw observer refreshes outlines and invalidates recordings only when geometry, mask identity, or native outline ownership changes. It reattaches to a replacement view-tree observer. Disabling depth restores the original outline provider and clipping flag. Native rounded outlines are retained for the backgrounds. Full occlusion uses a valid clip outside the view bounds, because an empty outline disables clipping. Android versions before 33 retain the canvas fallback.

Validation: compilation, D8, packaging and v3 APK signature verification passed. On-phone platform checks passed subject-path outline eligibility, full-occlusion bounds, native path subtraction, native blur-info isolation/corners, and installed weather/save/font/clock signatures. An attempted offscreen GPU pixel test was terminated by Android's native process environment; it did not produce a visual validation result and is not part of the passing checks. The prior 30 Java contract checks cover the unchanged persistence helpers.

Visual validation is pending after SystemUI/AOD reload: widgets should refresh their content and background cutout while entering AOD and waking. No settings migration or persistence behavior changes in this build. The mask itself can still miss parts of the mountain, and Dressroom's main preview remains deferred.
