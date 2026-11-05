# AOD 8.9.32.95: One UI 8.5 inspection

Inspected through the JADX MCP on 2026-10-30. Manifest: `com.samsung.android.app.aodservice`, versionName `8.9.32.95`, versionCode `893295000`, min SDK 35, target/compile SDK 36, shared UID `android.uid.systemui`. The user identifies the input as a stock firmware APK published on APKMirror; its signature and file hash were not independently checked in this pass.

This complements [Dressroom 2.9.60.51](DRESSROOM_2.9.60.51.md). It records source/bytecode findings, not a working port. No runtime code, installed APK, saved profile or phone process was changed. Raw responses are local, ignored files in `.build/compatibility-85-aod/`.

## Clock provider and stretch

The named `com.samsung.android.uniform.plugins.UfClockProvider` and `com.samsung.android.uniform.widget.clock.AbsClockView` classes remain. AbsClockView implements FontExtension and ClockBlurExtension. Provider discovery by the named base is therefore a useful shared entry point; its internal bindings differ.

| Role | Current reference binding | 8.9.32.95 binding |
| --- | --- | --- |
| Stored ClockFontData | AbsClockView `T0` | `L0` |
| Current integer font type | `D0` | `w0` |
| Doze/font animation state owner | `W0` | `O0`, type `aod.ys` |
| Media expanded flag on clock | `d1` | `W0` |
| Native font render/update | `g0(ClockFontData)` | `b0(ClockFontData)` virtual method |
| Height/doze/media transition | `K0(float, boolean)` | `B0(float, boolean)` |
| Height balancing | `aod.qn1.X(int, ClockFontData, List)` | `aod.PO.k(List)` for a narrower native case |

This is not just obfuscation renaming: `W0` is a **boolean** in 8.5, and `D0` is also a boolean rather than the font ID. Type-checking every bound field is essential before installation. A resolver that accepts the first matching field name can select a real field with the wrong role.

JADX could not decompile `setFontType(ClockFontData)`. Its actual smali was inspected:

1. It returns immediately when the requested font equals stored `L0`.
2. It copies the incoming font, including a separate height list.
3. For clock type 1 and font IDs 803–805 it calls `PO.k(List)`.
4. If attached and a native stretch animator (`S0`) exists, it can replace the new font's heights with the previous heights while asking that animator to move toward the target.
5. It stores the font in `L0`, updates `O0.d` and its active axes, then calls `b0` and `refresh`.

`PO.k` is explicitly a two-element vertical-height balancer. It keeps a ratio up to 1.6 and otherwise caps the larger element at `smaller * 1.6`; other input shapes can return null. Our reference normalizer signature and scope do not apply unchanged. A manual-stretch adapter must bypass only the appropriate normalization call within a module-owned font operation, and coordinate with the separate native height animation. Do not disable the shared utility globally.

`aod.ys` is the font/doze animation state manager. Source confirms these roles: `c` is dozing, `d` is font data, `e` is scale, `g` is media-expanded, `i` is SpringAnimation and `j` is the active axis list. `B0` interpolates HEGT toward 1.0 during the transition while also updating weight. The spring calls virtual `b0` and refreshes the view.

The shared keep-stretch policy can still preserve only HEGT. Its adapter needs the new owner/transition/render members and native state semantics. Keep native doze, weight, palette and roaming updates alive. Do not replay the reference `W0.d/h/i/j` access sequence on this build.

Named `setMediaExpanded(boolean)`, `setDozing(boolean)`, `setFontType(ClockFontData)` and `isStretchable(List)` exist, but their presence alone does not establish compatibility with our callbacks and animation restoration.

## Blur, depth and color

| Role | Reference | 8.9.32.95 |
| --- | --- | --- |
| Blur manager | `aod.cq0` | `aod.Zr` (JADX C1766Zr), held at AbsClockView `Z0` |
| Combined path strategy | `aod.tx0` | `aod.Lv` (C0888Lv) |
| Blur options | `aod.xb0` | `aod.dl` (C2011dl) |
| Strategy submission | `b(ImageView, WeatherInfo, options)` | Same role/signature shape with the new options type |
| Clear strategy | `c()` | `Lv.c()` clears native SemBlurInfo |
| Builder clip utility | `aod.e70` helper | `aod.rF.c(SemBlurInfo.Builder, Path)` |
| Text collection | Manager `d(ArrayList, int, float)` | `Zr.d(ArrayList, int, float)` |
| Submission/retry | Manager `c(int, int, boolean)` | `Zr.c(int, int, boolean)` |

The native strategy still combines text paths and a separate weather-icon path. It translates into a selected blur host, applies a SemBlurInfo clip and submits the built info. Its hosts are selected by tags:

- Runtime: `lock_clock_blur`.
- Editor window blur: `lock_clock_blur_on_lockeditor`.
- Editor canvas blur: `Clock_Blur_Container`.

Editor positions use location-on-screen; runtime positions use global visible rectangles. The blur adapter must expose the chosen host and coordinate convention, not only the strategy method. Shared depth clipping can consume that information while keeping the existing separate-surface ownership rule.

`MediaBlurHost`'s fix remains relevant: leave the native BackgroundBlurDrawable attached while hiding its host, and restore the latest native alpha. Do not bring back a saved compact drawable after media collapse.

Native `Zr.c` retries missing text/weather paths after 8 ms, up to five retries. This is bounded native readiness work; it is not permission to add an independent unbounded redraw loop. An 8.5 adapter should follow its draw/readiness lifecycle.

`Zr.h()` derives brightness/saturation from the selected custom clock color in its Custom branch. Other branches use native wallpaper/override data. Our custom-colored-blur policy therefore needs a mapped tone source as well as an opacity override. Blindly transferring the reference `WallpaperBlurTone` fields would read different objects.

Framework SemBlurInfo constructor/field compatibility is still unverified. The APK uses Builder and submission APIs, but that does not prove our reflective nine-argument cloning constructor and private corner fields match the target framework. Matching SystemUI/framework evidence is needed before enabling widget surface cloning there.

## Glyph optimizations are version-specific too

`ClockNonePaddingTextView` still draws shader shadow and fill in separate native passes, so sharing outline shaping within one draw is a plausible common optimization. It must retain its existing measurement/reentry guards.

However, this build's `getPathForBlur()` builds a fresh Path with `Paint.getTextPath` whenever called, after checking the superclass has-drawn flag `NonePaddingTextView.L`. It does not use the same cached-ready path lifecycle as the reference provider. `NonePaddingTextView.onDraw` records baseline offsets `J/K` and sets `L` true.

Consequences:

- Do not port `GlyphBlurReadiness`'s reference `R/S/U` cache assumptions by replacing letters. That defect/workaround is not established here.
- Font request methods are `g(int, String)` and `h()`, rather than the reference `h/i` pair. The corresponding fixed/type/variation fields are `Q/P/R`, rather than `c0/b0/d0`.
- Keep optional glyph optimizations disabled until their own invalidation/native-field contract is bound and verified. Missing an optimization must not disable normal rendering or effects.

## Widgets: several native interfaces survive

The following named classes and useful members exist:

- ComplicationWidgetSizeManager: `getColumn`, `getRow`, `isBiggerSize`, `init(WidgetDisplayType)`, `widgetAlignType`, `_scaleFactor` and `context`.
- ComplicationGridUIController: `changeAlignType`, `onclickChangeWidgetAlign`, `setRootContainer`, `reconfigureGridLayout`, `updateGridRule`, native size-table setup and module/host management.
- ComplicationWidgetPluginController: native runtime owner with `container`, grid/clock/layout managers, initialize/release, doze/dark-amount/AOD callbacks, wake callback and widget updates.
- WidgetLayoutManager: `refreshGridLayout` and `setupRootContainer`.

The inspected sizing rules are close to the assumptions behind our native-cooperating grid logic: phone defaults 4×1; feature-gated tablet/multifold grids use 8×2 or 10×2 and rotate their dimensions. Native Vertical initialization derives scale using the row count against screen width. Keep custom capacity separate from physical scale and preserve the phone horizontal baseline when appropriate; a tablet needs its own verified metrics policy.

The two native rotation operations remain distinct:

- `onclickChangeWidgetAlign` disables grid edit processing, changes alignment and size-manager state, updates the animated alignment layout, then restores processing.
- `changeAlignType` changes state and calls `reconfigureGridLayout`, which updates rules and initializes the dynamic grid.

Preserve that distinction in WidgetBinding. Replacing both with one manual coordinate transform risks the same rectangular-widget bugs we fixed earlier.

The plugin controller initializes/release observers around the actual root. It also gates some media-expand/profile-transition integration on native feature flags and `getSystemUIPluginVersion() >= 3005`. Package version is not a substitute for this interface-version check.

Its dark-amount handling goes through BlurEffectManager; update/release and AOD-state calls must be adapted together so cached widget contents and blur backgrounds stay synchronized. Exact SemBlurInfo cloning still needs the framework validation described above.

UfClockProvider exposes LockItemPluginService, whose `getWidgetUIController()` returns the native ComplicationGridUIController. Main and cover widget facades are separate. Keep loader/instance ownership and the current cover-display guard while porting; these signatures do not certify cover-screen support.

## Media player

NowBarMediaCardView and FaceWidgetMusicPage remain named classes. The music-page adapter is `aod.NR`, creating holder **`aod.YR`** (JADX C1677YR), not the reference `aod.fa2`. Its artwork bitmap is at `w0`; controls, masks, reveal/ripple surfaces and containers have different fields.

Constructor resource lookups identify roles such as `facewidget_music_page_controller_blur_view`, `facewidget_music_page_content_container`, `facewidget_music_page_title` and native artwork masks. These role/resource anchors can reduce dependence on field letters, but native animation ownership must still be mapped.

NowBarMediaCardView exposes content-animation and expanded-state callbacks and manipulates the expanded content root's alpha/children. Our reference synthetic lambda `aod.n82` discriminator cannot be assumed. Port the actual start/progress/completion chain, including both animated and immediate transitions.

The card references `com.iux.media_art_share.shared.utils.ExtractedColors` from native card extras. The user confirmed this input was supplied as stock firmware via APKMirror. That namespace by itself is not evidence of an externally patched APK; it should not trigger an alternate mod-specific binding.

Remaining: exact expansion overlay owner, collapse ordering, title/outside-close hit testing and SystemUI shade stacking. Full-screen media must remain unavailable on this variant until those dependencies are complete.

## Native item-store acknowledgement

The manifest exposes AodDataProvider at authority `com.samsung.android.app.aodservice.provider.LockScreenDataContent`. It routes calls through `aod.Kn0` (LockScreenDataManager). JADX failed to decompile the router method, so its smali was inspected.

The `post` / `aod-post` / `lock-star-post` branch for argument `items` decodes the input list, calls local item-store `aod.Sm0.c(List)` and returns a Bundle boolean under **`result`**. Sm0 aggregates the selected handlers' boolean returns.

The clock handler is `aod.Q41`, SetClockItemImpl. It rejects a non-ClockItem, copies/refines the clock into a display/profile bundle, calls the profile writer, then returns true. The profile writer (`datamanager.data.item.a.l`) can return without writing or catch an exception internally; its return type is void.

Therefore provider `result = true` is stronger evidence than Dressroom's generic Complete callback, but still does **not** prove the clock data was persisted. The compatibility transaction should combine the normal editor completion, accepted relevant native item submission, and targeted native readback where possible. Preserve the native target display/profile identity; do not use a fixed delay or assume success from every non-null result Bundle.

The exact Dressroom client-to-provider serialization/return-consumption link remains to be bound. No additional provider write calls were made during this analysis.

## Next dependency

The subsequent [older 8.5 SystemUI inspection](SYSTEMUI_16_4006.md) establishes interface version 4006, legacy charging and native input-gate roles. Exact firmware pairing and full dynamic layer/animation validation remain outstanding. The proposed [adapter structure](ADAPTER_DESIGN.md) keeps these findings separate from common feature logic.
