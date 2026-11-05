# 0.6.2 — complication background depth

The user confirmed 0.6.1 depth on both lockscreen and AOD after reboot, weather restoration in the editor, and no remaining pale duplicate after controls settled. AOD's initial clock appearance corrected itself after reapplying. Its widget contents were masked while their blurred containers remained over the subject.

JADX MCP traced those backgrounds to `ComplicationWidgetHostViewContainer.blurView`. `addBgViews` calls `aod.e70.d` and `refreshBlur` calls `aod.e70.c`; both create independent Samsung compositor blur surfaces. A canvas mask on `grid_layout` cannot erase these surfaces.

`WidgetDepthBlur` scopes those two builder operations to a view inheriting an active depth mask. Before the builder finishes, it subtracts the subject from the rounded background path in that particular view's local coordinates. The mask uses the inverse global transform, including parent scaling and translation. Samsung retains its native blur radius, color curves, dim color, and corner radius. Other blur surfaces without a tracked subject retain native behavior.

A pre-draw observer checks complication background geometry and mask identity, rebuilding native blur only when they change. Removing depth rebuilds the native background without the subject clip. The successful clock glyph-path hook and editor weather/save transactions are unchanged.

Validation: Java compilation, D8, APK packaging and v3 signature verification passed. All 26 reflection/identity/save-scope checks passed. The on-phone platform probe passed rounded background subtraction, preservation of rounded corners and non-overlapping backgrounds, Samsung clip API availability, and the installed complication blur signatures. Visual confirmation after installing and rebooting remains required.

Remaining observations: initial post-reboot AOD clock appearance needed a reapply in 0.6.1; that startup issue has not been independently reproduced or fixed here. Subject extraction can still miss parts of the mountain. Dressroom's main preview remains deferred at the user's request.
