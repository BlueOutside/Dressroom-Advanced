# AOD 8.9.36.23

Two supplied APKs report versionCode 893623000, compile SDK 36. Their checked core
clock declarations match, although the APK hashes differ:

- `ea8ad221a10ce2e2a47e03cf9a5c5781e4063379f82d3ff1015b4817579b23ae`
- `4dce599dcb0df6e27ca6f9d99bc0b24c1bf90c11a79ad0ae5128c68f58689ab1`

The stored ClockFontData field is M0. The renderer is b0; shadow refresh is Y.
The fixed and variable typeface operations are A and z. These match the shared
legacy contracts once font storage is discovered by type.

AbsClockView.setFontType(ClockFontData) calls aod.VO.d(List), then stores the
returned heights through ClockFontData.setFontHeights. This is the legacy height
normalizer mapped by ClockSourceBinding. The old PO/QT hints do not identify it.

The integer font ID is x0. Earlier name probing could pick w0, a different integer
with a valid type. ClockBinding pairs the verified M0 font storage with x0.
Unknown layouts fall back to ClockFontData.getFontType; known providers retain
their native requested-font ID, which can differ from temporary transition data.

These are inspected core bindings, not certification of every media, widget or
blur feature on a device running this provider. Keep the runtime smoke check and
actual firmware/device details separate from these APK declaration checks.

A read-only PathClassLoader probe on the connected SM-S948B resolved the new
ClockBinding, FontBinding and ClockSourceBinding against the supplied 8.9.36.23
APK. No AOD APK was installed. This verifies reflection/loading on that host,
not the actual user's firmware, media behavior or blur capability.

The supplied Dressroom is 2.9.60.42 (296042000). The combined read-only probe also
resolved EditorBinding, WallpaperSourceBinding, the Object Capture SDK contract
and CropImageView.onDraw against that APK. Hook installation in the affected
user's process is still a separate check. The depth panel now reports the actual
failed provider/editor binding instead of assuming an unsupported version.

Native blur uses Nv.b(ImageView, WeatherInfo, fl), with the clock stored in Nv.b
(holder Cy0), then Cy0.a (Object). Its render method calls i20.c(Builder, Path).
The clip signature also exists on zi.a, so unconstrained fallback is ambiguous.
The verified Nv/i20 mapping uses clock field a, distinct from the earlier Lv
holder's b. Without that mapping the module can mask normal clock draws while
leaving the separate native blur layer over the subject. The 1.0.2 diagnostic
build includes this binding; the reported visual still requires device confirmation.
