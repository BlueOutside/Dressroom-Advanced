# Runtime and editor boundaries

Source directories under `app/src/main/java/dev/denver/dressroomadvanced/`:

| Directory | Contents |
| --- | --- |
| Root | Module startup, settings IPC, reflection and lifecycle helpers |
| `clock/` | Clock rendering, manual heights, date, shadows and glyphs |
| `fonts/` | Font imports, file picker, variable axes and typeface updates |
| `depth/` | Subject detection, tracking and clipping |
| `widget/` | Widget layout, placement and compositor handling |
| `editor/` | Editor panels and Apply/cancel handling |
| `media/` | Artwork, contrast and media transitions |
| `blur/` | Blur controls and draw synchronization |
| `compat/shared/` | Editor, clock, depth, widget and Now Bar contracts; shared stack engines |
| `compat/oneui85/` | Clock Style and experimental AOD 8.5 mappings |
| `compat/oneui9/` | AOD 9.0 method mappings |

These are source folders. Classes still share the Java package
`dev.denver.dressroomadvanced` so package-private access and existing hook names
stay intact. Version-specific mappings belong in `compat/`; effect logic stays
in its feature folder. See [the shim map](compatibility/SHIMS.md).

## Important rendering contracts

Samsung's clock view and clock blur are separate owners. The blur is submitted to the sibling `lock_clock_blur` host. Clipping only a clock canvas cannot cover that host. Widget content, rounded backgrounds and native blur also have separate paths.

During full-screen artwork, keep the clock's native BackgroundBlurDrawable attached. `MediaBlurHost` suppresses host alpha and retains the latest native alpha request. Replacing/restoring that drawable previously resurrected compact clock contents before Samsung submitted the stretched outline. The captured regression is kept locally at `docs/validation/0.13.11/compact-clock-trace.txt` and is excluded from public source.

Native cached glyph readiness does not reset for every font/size update. `BlurEffects` synchronizes manager submissions and retry calls after glyph drawing, rather than guarding only the public refresh method. Gradient outline reuse is limited to one draw and requires equivalent text-measurement paint state. Exact font-request reuse never rounds stretch axes, and standalone native typeface/configuration updates invalidate it.

The native height spring owns animation timing. The module restores committed manual heights after its completion and suspends wallpaper depth while artwork covers a different image. The color fade and media widget cover have independent lifecycles. Avoid changing these boundaries as a cosmetic cleanup.

## Diagnostics and tests

Production retains installation/capability messages and error reporting. Repeated profile/shadow success logs, the delayed date-state dump and temporary media-return tracing are removed. Diagnostics, APK-derived snapshots and recordings live in the research archive; regression evidence remains in `docs/validation/`.

Tests compile separately and are never included by the module build. Native APK-dependent integration probes are opt-in, documented fixtures rather than assumed passing checks. Test runner lists are centralized in `tests/suites.json`.
