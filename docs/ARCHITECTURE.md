# Runtime and editor boundaries

All production classes remain in `dev.denver.dressroomadvanced` to preserve package-private/reflection contracts. This cleanup deliberately avoids moving hook targets between Java packages or changing Samsung method names.

| Area | Primary classes |
| --- | --- |
| Module startup and editor injection | `DressroomModule`, `DeferredDiscovery`, `NativeClockPanel`, `PanelTabs` |
| Editor transactions | `NativeEdits`, `SaveSnapshot`, `EditorClockRefresh`, `EditorExtras`, `EditorWidgets` |
| Committed settings and IPC | `SettingsProvider`, `SettingsBridge`, `SettingsScope`, `RuntimeExtras`, `DepthStorage` |
| Clock runtime and manual heights | `RuntimeClockHooks`, `SavedStretch`, `AodStretchExtras`, `MediaReturnCompletion`, `RuntimeClockData` |
| Date, weather, blur and shadow | `DateEffects`, `BlurEffects`, `GlyphBlurReadiness`, `ClockBlurSync`, `ShadowEffects` |
| Wallpaper subject extraction/tracking | `DepthAuto`, `EditorWallpaperSource`, `LiveSubject`, `MaskAlpha`, `MaskContour` |
| Depth rendering | `DepthEffects`, `DepthGeometry`, `DepthBlurHooks`, `ClockContentClip` |
| Widgets | `WidgetEngine`, `WidgetLayout`, `WidgetPanel`, `WidgetPlacement`, `RuntimeWidgetHooks` |
| Widget compositor paths | `WidgetContentClip`, `WidgetDepthBlur`, `WidgetBlurInfo`, `WidgetMediaOcclusion`, `WidgetMediaReveal` |
| Media artwork and interactions | `MediaFullscreenExtras`, `MediaBlurHost`, `MediaClockContrast`, `MediaInteraction`, `NativeArtworkReveal` |
| Render reuse | `ArtworkSamples`, `GlyphFontCache`, `GlyphDrawReuse`, `GlyphOutlineMemo`, `ViewGeometry` |
| Now Bar cards | `NowBarExtras`, `ChargingState`, `ChargingVisuals`, `PillTouchBounds` |

## Important rendering contracts

Samsung's clock view and clock blur are separate owners. The blur is submitted to the sibling `lock_clock_blur` host. Clipping only a clock canvas cannot cover that host. Widget content, rounded backgrounds and native blur also have separate paths.

During full-screen artwork, keep the clock's native BackgroundBlurDrawable attached. `MediaBlurHost` suppresses host alpha and retains the latest native alpha request. Replacing/restoring that drawable previously resurrected compact clock contents before Samsung submitted the stretched outline. The captured regression is kept locally at `docs/validation/0.13.11/compact-clock-trace.txt` and is excluded from public source.

Native cached glyph readiness does not reset for every font/size update. `BlurEffects` synchronizes manager submissions and retry calls after glyph drawing, rather than guarding only the public refresh method. Gradient outline reuse is limited to one draw and requires equivalent text-measurement paint state. Exact font-request reuse never rounds stretch axes, and standalone native typeface/configuration updates invalidate it.

The native height spring owns animation timing. The module restores committed manual heights after its completion and suspends wallpaper depth while artwork covers a different image. The color fade and media widget cover have independent lifecycles. Avoid changing these boundaries as a cosmetic cleanup.

## Diagnostics and tests

Production retains installation/capability messages and error reporting. Repeated profile/shadow success logs, the delayed date-state dump and temporary media-return tracing are removed. Diagnostics, APK-derived snapshots and recordings live in the research archive; only curated regression evidence remains in `docs/validation/`.

Tests compile separately and are never included by the module build. Native APK-dependent integration probes are opt-in, documented fixtures rather than assumed passing checks. Test runner lists are centralized in `tests/suites.json`.
