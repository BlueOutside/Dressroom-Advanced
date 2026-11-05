# Native media source and layer ownership - 0.18.8 alpha

Version code 125. Signed build, 152 JVM checks and 435 Android checks pass. Installed on the reference phone; **The user confirms the clock animation is fixed after this update. The controls rectangle and cover-to-pill occlusion remain.**

0.18.7 failed the user's test. Its clock fraction override, forced media K0 frames and thumbnail-only foreground crop are removed. Samsung's existing height spring and the established media-return completion logic are restored. The reference adapter no longer forces clock measurement/layout or writes the measurement calculator from the legacy integer font field.

## Clock source

JADX confirms `FaceWidgetViewModel.C()` selects stretched-clock handling through `KeyguardClockProvider.hasStretchFontHeights()`. That method reads `getClockFontData().hasFontHeights()`. `FaceWidgetClockPage.y()` measures the full and collapsed container using the provider's heights, not the visible clock's T0. Updating only the rendered clock can therefore leave native transition eligibility and container measurement inconsistent.

RuntimeClockData supplies the same committed manual/depth-owned heights to the native provider's full font data and height getter. No native storage is changed. A small weak-owner cache retains only validated height numbers, keyed by clock type, font and orientation. Settings notifications and native wallpaper-color handoffs invalidate it. Missing settings are not cached during startup. The hooks are optional and reference-only; Clock Style's different provider is unchanged. Known direct native decision/measurement callers are deoptimized.

## Artwork return

The return copy moves from the shade's top overlay into the stable native card content parent, below both expanded and normal content. The original artwork geometry and complete shrinking cover are retained. This prevents the copy from painting a raw strip over controls without cropping away the shrinking artwork. The copy does not inherit the expanded controls' fade. Its drawing matrix maps the shade-space artwork into that parent's space, and restoration removes only the module-owned view. The existing live native blur drawable ownership and crash guards remain.

## Validation boundary

Android contracts exercise the production return-layer draw beneath real child views, preserve the complete artwork, and test that fading expanded controls does not hide it. Saved manual, depth-owned, disabled and invalid heights are covered. Helpers cannot establish the final phone animation, especially the first open after update; it must be tested before touching or reapplying the clock.
