# World Clock container experiment — 0.12.8 alpha

VersionCode 90. Added a render-node clip to Dressroom's composite clock preview, with four Android checks for its geometry, switching styles, and editor reset (40 checks total with existing probes). This experiment still failed the actual World Clock drag/release test (`work/world128-moved.png`). It must not be described as a working World Clock fix.

Follow-up source inspection established that the active renderer itself is CityClockView. Testing for WorldClockView's class name therefore misses the editor case. See the 0.12.9 correction.
