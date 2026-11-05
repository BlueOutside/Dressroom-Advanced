# Runtime and editor boundaries

Production classes are organized into clear domain packages under `dev.denver.dressroomadvanced`:

| Package | Purpose | Primary classes |
| --- | --- | --- |
| `dev.denver.dressroomadvanced` | Core module startup, IPC, reflection, and lifecycle | `DressroomModule`, `SettingsProvider`, `SettingsBridge`, `SettingsScope`, `Reflect`, `HookTransaction`, `NativeContract` |
| `dev.denver.dressroomadvanced.clock` | Clock runtime, manual heights, and glyph rendering | `RuntimeClockHooks`, `ClockBinding`, `AodStretchExtras`, `SavedStretch`, `DateEffects`, `GlyphFontCache`, `ShadowEffects` |
| `dev.denver.dressroomadvanced.depth` | Wallpaper subject extraction, matting, and depth rendering | `DepthEffects`, `DepthGeometry`, `DepthAuto`, `LiveSubject`, `MaskAlpha`, `MaskContour`, `DepthStorage` |
| `dev.denver.dressroomadvanced.widget` | Complication layouts, sizing, panels, and compositor paths | `WidgetEngine`, `WidgetLayout`, `WidgetPanel`, `WidgetPlacement`, `RuntimeWidgetHooks`, `EditorWidgets`, `WidgetDepthBlur` |
| `dev.denver.dressroomadvanced.media` | Full-screen media artwork, interactions, and Now Bar cards | `MediaFullscreenExtras`, `MediaBlurHost`, `MediaClockContrast`, `MediaInteraction`, `ChargingState`, `ChargingVisuals` |
| `dev.denver.dressroomadvanced.blur` | Per-clock path-based blur, opacity, and wallpaper tones | `BlurEffects`, `BlurOpacity`, `WallpaperBlurTone` |
| `dev.denver.dressroomadvanced.editor` | Native editor activity bindings, tabs, and scroll coordination | `EditorBinding`, `EditorClockRefresh`, `EditorExtras`, `EditorScrollView`, `EditorWallpaperSource`, `PanelTabs` |

## Important rendering contracts

Samsung's clock view and clock blur are separate owners. The blur is submitted to the sibling `lock_clock_blur` host. Clipping only a clock canvas cannot cover that host. Widget content, rounded backgrounds and native blur also have separate paths.

During full-screen artwork, keep the clock's native BackgroundBlurDrawable attached. `MediaBlurHost` suppresses host alpha and retains the latest native alpha request. Replacing/restoring that drawable previously resurrected compact clock contents before Samsung submitted the stretched outline. The captured regression is kept locally at `docs/validation/0.13.11/compact-clock-trace.txt` and is excluded from public source.

Native cached glyph readiness does not reset for every font/size update. `BlurEffects` synchronizes manager submissions and retry calls after glyph drawing, rather than guarding only the public refresh method. Gradient outline reuse is limited to one draw and requires equivalent text-measurement paint state. Exact font-request reuse never rounds stretch axes, and standalone native typeface/configuration updates invalidate it.

The native height spring owns animation timing. The module restores committed manual heights after its completion and suspends wallpaper depth while artwork covers a different image. The color fade and media widget cover have independent lifecycles. Avoid changing these boundaries as a cosmetic cleanup.

## Diagnostics and tests

Production retains installation/capability messages and error reporting. Repeated profile/shadow success logs, the delayed date-state dump and temporary media-return tracing are removed. Diagnostics, APK-derived snapshots and recordings live in the research archive; only curated regression evidence remains in `docs/validation/`.

Tests compile separately and are never included by the module build. Native APK-dependent integration probes are opt-in, documented fixtures rather than assumed passing checks. Test runner lists are centralized in `tests/suites.json`.
