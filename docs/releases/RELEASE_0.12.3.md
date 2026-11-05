# Section separators and wallpaper-based tinted blur — 0.12.3 alpha

Installed versionCode 85. The user clarified that separators should divide major sections, not every setting; the 0.12.2 colored-blur result was not visibly convincing.

- Removed middle dividers from the page groups. Clock sections retain their section-start divider; widget sections now have a section-start divider. Slider/caption grouping and native sub-tab navigation remain.
- Verified from Samsung logs that the provider successfully submitted a six-path blur layer. The observed issue was not established to be a missing blur surface.
- JADX `cq0.h()` shows custom colors selecting blur brightness as `100 - LabL(tint)` and saturation from the tint. Preset colors use the wallpaper sample. This is separate from foreground opacity and can produce a different, flatter-looking custom-color result; source inspection alone does not prove its perceptual impact.
- Added `WallpaperBlurTone`: for module-forced custom-color blur outside the module's fullscreen-media state, sample the wallpaper exactly through Samsung's preset path. Editor uses `setClockAreaWallpaperColor`'s sample (`AbsClockView.t`); runtime uses `n16.A().C(clock.s)`. Native `rv2.w` supplies Lab brightness and native `l49` carries brightness/saturation. Missing samples fall back to Samsung's original decision. No screenshot capture, image processing or segmentation is introduced.
- Existing RGB foreground tint and visibility controls remain. Relevant native callers are deoptimized so the collection and tone hooks are not bypassed by inlining.

Validation: build and v3 signature pass; installed and scopes reloaded; SystemUI rendering guard passes. Nine new Android checks exercise actual AOD sampling interfaces, brightness/saturation contracts and missing-sample fallback. The existing 16 Android touch/charging/widget lifecycle checks pass on the final APK. Earlier 100 JVM checks cover unchanged opacity/lifecycle/layout helpers. The user confirmed section-only separators. Blur still appears weak even at radius 300, so tinted blur is not claimed visually fixed. The flower wallpaper/depth combination may conceal high-contrast features beneath the clock; this is an inference requiring a textured-background or depth-off comparison.

The user previously authorized discarding the active test draft; no new customization was saved by the assistant. Landscape/tablet/foldable/A-series work remains open as recorded in RELEASE_0.12.2.md.
