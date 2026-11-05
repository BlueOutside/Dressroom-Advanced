# Charging liquid restoration — 0.11.5 alpha

Installed versionCode 81. The user confirmed 0.11.4 preserves charging percentage text when cycling cards, but reported an odd wave/meniscus redraw.

Samsung's `nowbar_gradient_*` assets animate the wave mask's position during their intro. Pausing near frame zero on reattach freezes an incomplete mask; applying the percentage translation afterward leaves an incorrect-looking fill edge/width. The native intro also installs a wave blur callback, which cancellation can leave at a nonzero value.

Held charging cards now pause the liquid, set the native asset to its completed progress, and replace the wave blur callback with zero. The existing current-percent translation, localized text and finished inner text/icon properties remain. Native charging-speed resource switches re-settle a held card instead of restarting the intro. The initial non-held charging animation and outer Now Bar swipe transforms remain native.

Validation: signed APK build/v3 verification pass; install and SystemUI startup rendering guard pass. An Android probe loads all six actual SystemUI LTR/RTL normal/fast/superfast charging assets and applies the real Lottie drawable/callback APIs: 18 checks pass for completed progress, stopped animation and repeated restoration. The existing 16 Android touch/text/widget lifecycle checks pass against the final APK. The standalone probe was adapted for SystemUI's optimized fields and drawable testing because constructing its Lottie view outside an application requires the settings-provider lifecycle; those harness failures did not occur in SystemUI.

The user confirmed the liquid keeps a consistent fill without the odd redraw and percentage text remains visible after card cycling. Charging-speed changes still need broader validation. No preferences migration or depth/widget rendering change is included.
