# 0.6.5 - native ownership of runtime widget depth

An unprofiled temporary clock could clear the shared widget grid's depth state. Samsung creates multiple clock views while the widget panel belongs to its own native controller. Versions through 0.6.4 updated that panel from individual clock hooks.

`RuntimeWidgetHooks` now uses `ComplicationWidgetPluginController.container` and its `KeyguardClockProvider.clockItem` to apply the selected profile to the actual widget panel. All runtime clock-to-widget writes are removed. Native controller transitions, widget replacement, selected-clock/font changes and settings commits update the owner; release clears only its own container. The confirmed persistence changes remain in place.

The [JADX pipeline audit](../research/AOD_WIDGET_PIPELINE.md) traces RemoteViews content, separate blur/dim backgrounds, creation, native state changes, and the ownership defect. Build, D8, APK v3 verification and the on-phone native owner/transition/font/blur signature and geometry checks passed. End-to-end widget rendering remains pending after SystemUI/AOD reload; the ownership bug is established by code, but is not yet proven to account for every visual fragment.
