# Forced lockscreen blur: abandoned experiment

Stopped at the user's request on 2026-11-02. Device-wide blur is better handled by a separate module; this project no longer forces widget or Now Bar blur on unsupported devices. The existing clock blur feature and ordinary native widget/media blur are separate and remain enabled where supported.

## Tested target and observed results

- P613 custom One UI 8.5 port, API 36, Clock Style 8.9.35.60, Dressroom 2.9.60.50 and its actual SystemUI APK.
- 0.17.0 eligibility forcing produced a transparent Now Bar pill with an unsuitable foreground color. The user confirmed Extras appeared and its widget-blur flag saved; widget blur itself was not visually confirmed.
- The ROM's native dump reported Now Bar supported, blur unsupported, and window blur disabled. These are observations about this port, not proof that all A-series firmware behaves the same way.
- A real hardware-accelerated test Activity compared separate windows. Samsung window mode did not blur the underlying checkerboard. Samsung canvas mode blurred content supplied inside its own window, but did not blur the separate underlying checker. Android RenderEffect worked on its own checker content. Merely changing window mode to canvas mode therefore was not a demonstrated lockscreen solution.
- The 0.17.1 captured fallback obtained a wallpaper-only bitmap successfully. The user and an ADB capture still showed a solid white pill. The final bitmap submission/rendering path was not proven correct. It was not a successful blur fix.
- The captured-info/crop probe passed 13 checks on the tablet framework. This validated data ownership and geometry, not visible blur. No working A-series or foldable implementation was established.

## The actual API boundary

Clock Style calls the Samsung framework's View.semSetBlurInfo(SemBlurInfo). SystemUI hosts the lockscreen views; it is not a different blur API. The SemBlurInfo mode selects the backend:

- 0: BLUR_MODE_WINDOW, requested by the native widget/Now Bar helpers.
- 1: BLUR_MODE_WINDOW_CAPTURED, supplied with a bitmap.
- 2: BLUR_MODE_CANVAS, local canvas blur. Its Builder rejects backgroundColor, unlike window mode.

RenderEffect blurs the rendered content of its target. Applying it to an empty transparent view does not by itself blur another window behind that view.

## Native mappings inspected

Clock Style's runtime names (JADX may display renamed aliases):

- aod.Sp0.O(Context): Now Bar eligibility, combining Reduce transparency with aod.uJ0.u0.
- com.samsung.android.app.facewidget.presentation.a: base card. D recomputes policy; O(boolean,boolean,boolean) updates colors; J handles the background; U handles borders.
- aod.Ke.e(View,int,float,Integer,String): preset/window-blur builder. Ke.d handles custom attributes. Both ultimately submit through aod.QT.f and Ke.b(View,SemBlurInfo,String).
- Widget ComplicationWidgetHostViewContainer.addBgViews/refreshBlur/setDarkAmount/setMusicExpandState; aod.QT.L(double,int) selects native WidgetBlurType.

Reference 9.0 counterparts were aod.m05.X, base-card H/T/N/Z, aod.ln.o for widget type and aod.e70 for blur helpers. Native signatures were checked, but visual success of forcing on unsupported 9.0 hardware was not established.

The tablet's SystemUI has SecCapturedBlurBitmapGenerator, ScreenShotBitmapFactory, WallpaperScreenShotProvider and WindowManagerScreenShotProvider. The last uses SemWindowManager.screenshot with target type 2000, a reduced resolution, and additional native flags. Some classes did not fully decompile in JADX.

## Captured fallback stopping point

The experiment used the seven-argument SemWindowManager.screenshot overload with display ID, target type 2013 (wallpaper), include-target=true, an empty Rect, quarter display width/height, and useIdentityTransform=false. Capturing while awake returned the applied wallpaper without lockscreen controls. Capturing while the wallpaper was hidden returned black. No screenshot-policy bypass was added.

A single worker cached a reduced full-display bitmap. Per-view crops used transformMatrixToGlobal and preserved the full sampling width during partial off-screen swipes; unchanged crops were reused. The native SemBlurInfo constructor has parameter order mode, bitmap, radius, background color, four corner radii, canvas scale. Mode-specific getters reject some captured-mode queries, so stored fields were checked separately. Native curves/clips were copied to a separate object instead of mutating shared info.

The final attempt added screen-on, wallpaper-change and configuration retries, delayed 500 ms to avoid the initial hidden wallpaper surface. Logs confirmed the wallpaper capture became ready; the pill remained white. Whether the final native submission occurred inside the scoped helper hooks, was delayed beyond them, or was subsequently covered/replaced remained unresolved. A potential next investigation was an ancestry-scoped View.semSetBlurInfo observation, rather than relying on a thread-local helper scope. That was not implemented or verified.

## Removal and archived source

0.17.2 removes the experimental controls, hook installation, force flags from the allowed settings schema, and both production backend classes. Stored old flags are ignored and disappear on the next ordinary Extras save. A process reload removes the already-installed hooks; APK replacement alone cannot unhook a running process.

The removed experimental implementations are kept in a local backup, outside this repository. The standalone [framework comparison Activity](../../tests/blur-backend/README.md) remains available for manual diagnostics.
