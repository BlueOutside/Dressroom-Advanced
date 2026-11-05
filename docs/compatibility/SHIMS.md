# Shim layout

`compat/shared` contains the binding contracts used by both supported setups.
`compat/oneui85` maps the inspected 8.5 Clock Style and AOD methods; `compat/oneui9`
maps the reference AOD methods. All classes retain the existing Java package.

The clock source contract resolves its entire dependency list before hooks are
installed. Missing methods disable that optional synchronization, leaving native
behavior intact. Do not try nearby obfuscated names until their behavior is
checked in the matching APK.

In 8.5 Clock Style, `FaceWidgetClockPage.w()` measures the stretched height.
The equivalent 9.0 method is `y()`. Both consult the provider's font data, so
restoring only the visible clock leaves native measurement with old heights.

AOD 8.9.32.95 has an experimental 8.5 source/measurement and depth-blur shim.
It is declaration-checked, not device-validated. 9.0 Clock Style remains
unsupported. The fullscreen media player and glyph-cache optimizations are
not ported to 8.5.
