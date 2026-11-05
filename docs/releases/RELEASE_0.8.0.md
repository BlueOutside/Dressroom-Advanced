# 0.8.0 alpha — live crop masks and depth performance

Installed versionCode 58. The user confirms that AOD screen-off/on is noticeably smoother than 0.7.8.

The detailed-mask path introduced in 0.7.8 could repeatedly run expensive native subtraction. Subject paths now retain their screen/local transforms, and a bounded cache reuses subtraction results when the clock geometry and subject transform have not changed. Subpixel contour simplification reduces pixel staircase vertices before compositor submission. The Region fallback for detailed flowers is retained.

Depth now establishes the manual renderer hold synchronously before enabling the effect, retaining existing float height values rather than reconstructing them from missing measurement rectangles. Measured defaults are used only when current heights are unavailable. Disabling a depth-owned hold clears its staged state before native publication; a pre-existing manual hold is retained. The renderer validation override is updated immediately in both directions.

New detections scan the current drawable once in image coordinates. Live preview maps that mask through Dressroom's drawable bounds, image matrix and view transform. Apply freezes a cropped PNG for the runtime processes; the editor keeps its source mask. Detection is still required when switching to a different image, and old saved masks need one new detection to acquire source-coordinate tracking.

Validation:

- APK compiles and verifies with the existing v3 signing key; installed as 58.
- All 54 JVM reflection, identity-hold, lifecycle and widget-layout checks pass.
- 16 real Android geometry checks pass: thin stems, holes, empty/full coverage, immutable cached results, staircase reduction and matrix composition.
- Saved-mask benchmark on the phone: 6,456 contour vertices reduced to 3,198; first subtraction about 36 ms, repeated cached subtraction about 0.001 ms. This is a geometry microbenchmark, not an FPS measurement.
- Fresh SystemUI sample after reloading: 95th percentile 81 ms, 99th 150 ms, no frames of 500 ms or longer in that sample. Historical pre-update sample included multi-second stalls. Samples cover different activity and do not establish a controlled speedup. User reports noticeably smoother transitions; remaining jank is still possible.
- Live editor pan/zoom, depth on/off and manual-height preservation need direct editor verification. No claim of complete elimination of jank or all wallpaper-specific behavior.
