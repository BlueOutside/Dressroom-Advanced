# World Clock editor ownership — 0.12.7 alpha

VersionCode 89, follow-up to [0.12.6](RELEASE_0.12.6.md). No preference migration or new runtime/AOD style clipping.

The final 0.12.6 test exposed World Clock escaping depth after dragging. Its editor hierarchy (`work/world126-moved.xml`) places native CityClockView instances in Dressroom's `world_clock_view`/`home_city_container`, not just beneath the selected WorldClockView. Their provider type is 2097872928, while the editor selection is 50002. They can therefore pass a simple clock_preview ancestor test and clear the selected style's global depth state.

JADX AOD WorldClockView and CityClockView confirm separate child provider ownership, and font/color/time delegation. The editor's actual hierarchy is the decisive evidence for why excluding only nested children of WorldClockView is insufficient.

`applyEditorDepth` now accepts only the renderer referenced by the active editor controller's `w.a`. The visible city clocks may receive the selected mask through the preview/render-node traversal, but cannot bind the detector or replace the selected profile with their own identity. Picker/ancestor guards remain in place. The main binding hook already retries after the native renderer is installed.

Final APK build and v3 signing pass. Eight weather/source ownership checks, eight editor clipping/ownership checks, and 20 existing Android depth contracts pass (36 total). The user subsequently confirmed the World Clock correction still fails. Their wallpaper-style detection seemingly works across styles for the current subject. Calendar/Analog and grayscale confirmation from 0.12.6 remains the last visual evidence for those unchanged paths.

Weather/static depth coexistence was exercised with snow. Fresh detection on weather wallpapers, all gallery filters (including Sepia), arbitrary frame cutouts, and other-style saved AOD behavior remain open. Full details and screenshots are in the 0.12.6 notes. No final wallpaper Apply/Done was performed during testing.
