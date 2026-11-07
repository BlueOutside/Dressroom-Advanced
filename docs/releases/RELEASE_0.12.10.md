# World Clock idle depth and manual-stretch eligibility - 0.12.10 alpha

Installed versionCode 92. Carries the selected-model type/font and composite container fix from [0.12.9](RELEASE_0.12.9.md), plus a correction to manual-stretch restoration. No new runtime/AOD render-node clipping or preference-schema migration.

## Final change

Depth must force manual stretch only for a stretchable clock. The restore path previously treated depthEnabled alone as permission to hold any provider renderer, including World Clock's CityClockView. Its font/measurement refresh could reset placement even though the UI never offered manual stretch for this style.

- Renderer restoration now checks the native selected model's getStretchable, and clears a hold on the current renderer when it is ineligible.
- Restoring saved font-height locks also requires getStretchable.
- Opening Advanced cannot restore a saved manual hold for an ineligible style; it requires the same native capability and FontExtension as the manual-stretch UI.
- Selected World Clock type/font continue to come from the editor model, and all visible city rows are covered by the native editor container clip.

## Visual verification on the final build

- Fresh World Clock drag with depth Off moved home_city_container to `[306,853][1134,1045]` (`work/world1210-native-drag.xml`).
- Enabling depth preserved placement. After closing the panel, the flower still hid the first part of the time while idle (`work/world1210-idle.png`).
- A further native drag with depth On moved the same container to `[306,953][1134,1145]`, exactly 100 px down; it did not snap back. The time, city/date and widgets stayed behind the flower after release (`work/world1210-on-dragged.xml`, `.png`).
- Switching back to the normal clock restored the normal provider and its depth (`work/world1210-normal-switch.png`). The World Clock container clip did not remain attached to it.
- The earlier 0.12.9 slider test also established idle clipping, but exposed the manual-hold placement bug. It is superseded by the final native drag/release check above.

Final build/v3 signature and 40 real Android checks pass: 12 clock/editor/composite clip checks, eight weather/source ownership checks, and 20 existing depth contracts. No further tests were needed after the unchanged final APK passed the native visual checks.

Test edits were discarded with native Back; the original applied wallpaper was reopened. Final wallpaper Apply/Done was never used. Temporary USB keep-awake was restored to its original value 0. SystemUI kept rendering; it was not restarted for this editor change.

Calendar and Analog editor depth were verified earlier. The user confirms wallpaper-style detection seemingly works across their styles for this subject. Arbitrary frames, fresh weather-wallpaper detection and saved other-style lockscreen/AOD behavior remain open; this release does not claim exhaustive support for every provider/device.
