# Clock-style depth and wallpaper effects — 0.12.6 alpha

Installed versionCode 88. No preference-schema migration. Dressroom is reloaded; no SystemUI restart was requested for this editor-only change. Existing SystemUI hooks remain 0.12.5.

## Clock styles

- Picker thumbnails and nested World Clock provider children cannot own the automatic detector. Digit updates are restricted to the main clock preview. New main renderers reset pending ownership and reuse a valid current subject instead of launching another inference.
- Calendar's idle bitmap/cached content now clips through a render-node outline. The same editor-only path covers other native clock classes, leaving the standard horizontal/vertical pair on the established canvas/native-blur path.
- Analog additionally exposed a profile mismatch: its renderer reports font 0 while the editor can retain another font ID. Detection used to publish the mask into a new disabled profile. Current editor model identity now governs both restore and detection publication.
- On-phone Calendar and Analog idle clipping is verified. In `work/analog-profile-fixed.png`, the right flower hides the numeral 4 and the central flower clips the hand. Other built-in classes and other-style lockscreen/AOD rendering are not exhaustively verified.
- Final World Clock check: subject reuse completes, but its time still draws over the flower after dragging (`work/world126-moved.png`). Nested city rendering remains unresolved; this is not a universal all-style fix.
- Temporary StyleClip diagnostic logs were removed. Released watchers restore the native outline and remove their observer.

## Wallpaper styles, frames and weather

- Grayscale was applied to the editor draft through the native filter sub-panel. JADX `h7.x.e()` -> `rh.a.o(true)` -> `c7.h.C1()` establishes this button only updates the parent preview; the editor's final Done was never used. Automatic/explicit detection completed (`work/filter-redetect2.xml`) rather than remaining at Detecting. This does not establish all gallery filters, including Sepia, work.
- Native style switching uses temporary ClipDrawable/LayerDrawable previews and eventually publishes a composite bitmap. Do not treat a successfully restored mask or one filter test as proof of every transition case.
- Native frames change both the photo's placement and shape. A rounded rectangular frame was exercised in the draft (`work/frame-test.png`). Arbitrary cutout geometry and committed AOD frame alignment remain to verify; no new frame-mask algorithm is claimed.
- Weather uses `WeatherSurfaceView` rather than drawing the source CropImageView. JADX `i7.g.e()` updates that surface from its owning crop. The new hook binds this exact pair. The hidden crop remains available only while attached to the same window as its visible weather surface; normal hidden/retired previews remain ineligible. Detection continues to use the static source and crop, without per-frame inference on particles.
- Apply also rejects an invalid old live-source binding after failure instead of trying to bake the previous image. Failed scans/snapshot preparation clear the pending-source state so a failed attempt cannot leave depth permanently suppressed behind a loading state.

## Validation

- Final APK build and v3 signature verification pass.
- Eight new real Android weather/source ownership checks pass, covering static visibility, hidden native ownership, wrong window, detached source, stopped surface, and editor reset.
- Eight render-node/editor ownership checks and 20 existing depth contracts pass on the final APK (36 total).
- Snow preview with the already detected flower was visually exercised (`work/weather126-preview.png`); clock/widget depth coexisted with weather particles. Samsung intercepts clock taps while this effect preview is active. Fresh subject detection on a weather wallpaper and saved lockscreen/AOD alignment remain unverified. The ownership checks do not verify animated deformation or the runtime wallpaper transform.

The assistant's clock, filter and frame tests were drafts. Restarting Dressroom discarded them; no final wallpaper Apply/Done was performed.
