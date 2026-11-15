# Widget outlines and placement ordering

Reference: One UI 9 AOD 8.9.66.1, inspected through JADX MCP on 2026-11-10.
Implementation changes are in the shared widget/depth code.

## Native behavior

The complication host measures an unscaled viewport from WidgetSizeSpec, then
applies its scale and an RTL-aware pivot. Its layout callback reapplies native
corner outlines to the host and the RemoteViews background. Those providers can
be replaced on later layouts; they are not module-owned objects.

The smooth-corner provider usually calls Outline.setPath. It uses a round rect
only for the special capsule case. Treating getRect failure as a rectangular
widget loses the native smooth corners, including equal-radius smooth corners.

## Shared fixes

WidgetContentClip now copies an existing native path before subtracting subject
or media coverage. Rectangular/round-rect outlines retain their existing path.
The resulting outline preserves native shadow alpha. Disabling restores the
latest native provider, including one replaced during host layout. The framework
Path field is resolved by type and cached; no new provider names are required.

There are two motion paths. The separate-surface outline watcher already moved
after WidgetPlacement's pre-draw listener. The canvas-motion watcher did not,
so ordinary phones could sample blur geometry before the same frame's free
placement. Placement now orders both watchers after its own update. This runs
when placement is registered, not as a new per-frame tree traversal.

The placement regression failed before this ordering change. Its extended case
also changes viewport dimensions and the parent's translation/scale. The shape
probe now exercises composition with explicit local cut geometry: the headless
system context otherwise left its mask unscaled and could not test subtraction
at the widget's dimensions. Provider capture/restoration is checked separately.

These changes do not alter provider size options, widget eligibility, native
scale/pivots or stored grid positions. Sizing and transition visuals on other
device/provider combinations still need user verification.

## JADX aliases

| Original | Alias |
| --- | --- |
| aod.l11 | aod.ComplicationWidgetHostView |
| aod.il6 | aod.SamsungAppWidgetHostView |
| aod.c63 | aod.SmoothWidgetOutlineProvider |
| c63.a / f4597a | topLeftRadius |
| c63.b / f4598b | topRightRadius |
| c63.c / f4599c | bottomRightRadius |
| c63.d / f4600d | bottomLeftRadius |
| l11.D / f17500D | widgetDisplayType |
| l11.E / f17501E | widgetSizeManager |
| l11.F / f17502F | resizeSpec |
| l11.w / f17513w | lastRemoteViews |
| l11.x / f17514x | defaultWidgetView |
| l11.z / f17516z | boundWidgetId |
| l11.A / f17497A | visibilityAlphaChanged |
| l11.B / f17498B | wakeLock |
| l11.M / f17509M | visibilityAlpha |
| nn1.h / m8563h | applyWidgetContentColor |
| nn1.i / m8565i | applyUniformWidgetCorners |
| nn1.j / m8567j | applyWidgetCornerRadii |

These 19 aliases describe the reference APK only. They are not runtime bindings.

## Refresh, sizing and movement pass (1.1.16)

The supplied reference APKs were inventoried, then widget-owned classes and their
relevant call sites were inspected locally in one batch. This is a targeted
static comparison, not full feature validation on each firmware.

| APK family | Reference versions inspected | Relevant difference |
| --- | --- | --- |
| AOD | 8.9.66.1, 8.9.65.81 | Factory applies setResizeScale; modern size-spec API |
| AOD | 8.9.36.23 (both supplied files), 8.9.32.95 | Legacy calculateWidgetSizeSpec/getWidgetSizeSpec pair; no factory setResizeScale call |
| AOD | 8.9.28.18 | Older getWidgetSizeSpec-only variant |
| Clock Style | 8.9.65.92 | Modern factory resize path |
| Clock Style | 8.9.35.60 | Legacy sizing path |
| Dressroom | 3.0.10.38 (both files), 2.9.60.50, 2.9.60.51, 2.9.60.42, 2.9.50.90 | Editor widget entities/repositories compared; runtime sizing stays provider-owned |
| SystemUI | Supplied 16/36 references, 17/37, 17.99.02/179902 | No provider-owned widget factory/size manager in the inspected namespace |
| Samsung live wallpaper | 2.8.78.0 | No widget sizing/refresh owner in that namespace |
| Older references | AOD 8.8.40.20, Dressroom 2.8.10.24 | Outside the supported port; structurally different, no support added |

The unrelated OPPO AOD reference was identified by manifest and excluded from
Samsung widget comparison. There were 22 candidate files: 21 Samsung/SystemUI
references inspected in the scoped comparison, plus that OPPO reference.

Provider option bundles retain appWidgetSizes and semDisplayDensity across the
supported provider references. The modern host's RemoteViews update does not
replace its resize spec. No new refresh or sizing defect was established in this
pass, and the existing two sizing paths were retained.

An independent movement defect was reproduced before the fix: a native write to
translation while custom placement is active invalidates the module's old delta.
Reusing that delta made disabling custom placement restore a stale position.
Placement now detects external writes per axis and starts its adjustment from
the newly written native value. Clearing still leaves a later native write alone.
This uses shared View state and adds no provider-specific names.

Optional host-ID bookkeeping was also moved inside the factory hook's existing
error guard so it cannot discard a successful native creation result on failure.
Samsung's own creation exceptions still propagate normally.

Additional aliases in the loaded 8.9.66.1 JADX session:

| Raw / displayed member of aod.l11 | Alias |
| --- | --- |
| c / m7232c | animateVisibility |
| g / m7235g | setWidgetContentColor |
| d / m7233d | refreshAdaptiveContentColor |
| G / f17503G | appliedContentColor |
| H / f17504H | requestedContentColor |
| I / f17505I | adaptiveContentColor |
| J / f17506J | adaptiveColorProgress |

Other APKs were inspected locally, not renamed in the loaded JADX session.
