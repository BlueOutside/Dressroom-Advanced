# 0.17.0-alpha: initial 8.5 Extras and force blur

Add the native Extras tab to the 8.5 editor, saved by its ordinary Apply coroutine. Port charging-card retention to SecPowerUI and use the native custom-message card bridge. Keep full-screen media and AOD stretch-transition controls reference-only until their adapters are ready.

Add separate Force Now Bar blur and Force live widget blur switches. Reuse native provider renderers and presets. The Now Bar eligibility override is confined to mapped rendering calls; widget forcing uses its own path. Respect Reduce transparency and native widget blur-off/media states. No device-wide feature flags are changed.

This is an alpha port. Actual compositor behavior, visual transitions, charging lifecycle and Apply/reopen need target-device checks, especially on A-series devices.
