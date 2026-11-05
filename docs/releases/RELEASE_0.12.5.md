# Other clock styles: editor depth correction — 0.12.5 alpha

Installed versionCode 87. The user reports all other built-in clock styles fail in Dressroom, except the normal horizontal/vertical pair. Lockscreen/AOD failures were not reported/tested.

## Reproduction and findings

- Calendar was selected in the open editor draft. Enabling depth left white calendar text over the flower; explicit detection reported success but the overlap remained (`work/depth-calendar-enabled.png`, `work/depth-calendar-detect.png`). No native Apply was performed by the assistant.
- JADX: Analog extends AbsClockView; Calendar and Image extend DigitalHorizontalClockView, as does the Typo variant. Shared base inheritance does not guarantee all bitmap/background/cached content passes through the same canvas hooks.
- Picker thumbnails are real provider clock views (`clock_panel_clock_item_view`, `clock_container`). The shared attach hook previously let these views rebind/disable the automatic detector. Only clocks under the actual `clock_preview` may now own editor depth.
- A new renderer/profile did not reset the detector's resolved-source state. Enabling another style could therefore leave it with no attached mask even though this wallpaper had already been processed. Changed selections invalidate pending ownership; the current valid live matte is reused without another inference pass.
- The selected main clock's depth state now updates the editor container as well, so an old global layout callback cannot re-enable the previous style's depth on a new selection.

## Editor-only content clip

Other clock classes use a cached render-node outline clip through the established WidgetContentClip geometry; it includes backgrounds/bitmaps and hardware-cached descendants. Normal DigitalHorizontalClockView and DigitalVerticalClockView retain their working canvas/native-blur path. Native blur subject subtraction is retained. The new watcher checks transforms, dimensions, subject identity and media state; it does not run inference, encode PNGs, or subtract paths on every unchanged frame. Disable/editor reset restore the original outline. New render-node style clipping is deliberately limited to Dressroom pending verification.

## Validation and startup incident

- Eight new Android checks pass for actual Dressroom main-versus-picker resource ownership, subject geometry in a render-node outline, media suspend/resume, and native-outline restoration. The existing 20 Android depth contracts pass on the final APK. Signed APK/v3 verification passes.
- 0.12.4's combined scope reload failed the startup rendering guard. A native thread trace was captured (`work/sysui0124-stack.txt`): main waited inside Dagger DoubleCheck dependency construction, before deferred provider installation appeared in logs. Causation is not established from that trace.
- Restored installed 0.12.3 and restarted SystemUI; the user also requested another restart, which succeeded. Rendering was verified.
- Installed 0.12.5 and reloaded Dressroom only. The initial running 0.12.3 hooks were left in place; rendering remained active (3570 frames at inspection). Later logs show an independent SystemUI restart successfully loaded 0.12.5 (PID 7307). Other-style runtime/AOD support is not claimed.
- Calendar idle clipping was verified (`work/styles125-calendar-on.png`). Analog still failed: detection used the renderer font ID 0, while the editor profile retained another font ID. The follow-up 0.12.6 resolves profile identity from the current editor model; Analog idle clipping was then verified (`work/analog-profile-fixed.png`).

No preference-schema migration. The assistant's Calendar/style/depth changes were draft tests; committed wallpaper settings were not changed.
