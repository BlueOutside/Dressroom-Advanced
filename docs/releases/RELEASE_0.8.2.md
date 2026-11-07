**WITHDRAWN FROM PHONE: SystemUI startup stalled. Current installed build is 0.8.3 recovery with runtime hooks bypassed. See RECOVERY_0.8.3.md. The geometry checks below do not establish startup safety.**

# 0.8.2 alpha - faster animation cuts and one mask pass

Installed as versionCode 60 with v3 signature verification. Dressroom, SystemUI and AOD were reloaded. Includes the manual hold, source-coordinate tracking and bounded geometry caches from 0.8.0.

## Detection regression

The user reported that detecting again in 0.8.0 left only one leaf. Its trace showed a much smaller detected subject after switching the detector input from the current crop to a reduced full drawable. Version 0.8.1 restores `CropImageView.getCroppedBitmapWithMatrix()` as Samsung Object Capture's input. It unprojects the returned cropped mask into source coordinates using the crop matrix captured at detection start. Preview movement can then reuse that result. Previously unseen parts outside the original detection crop remain unknown; a different image still requires detection again.

Apply bakes a snapshot of the current crop. Independent widget masks use the same freeze and become dirty when a newly detected clock mask replaces their source. A pre-draw listener checks source transforms so native parent scaling also updates the mask; it does not request another detection. The listener is removed when replaced or the editor resets.

## Remaining AOD lag

The user confirmed 0.8.0 was noticeably smoother but still lagged. Caching only helps stationary geometry; animation changes geometry. Detailed masks now use the bounded Region cut directly instead of attempting the expensive vector operation before falling back. The diagnostic intersection operation has also been removed.

On the phone's saved-mask probe, first subtraction went from about 37 ms to 1.6 ms; cached subtraction was about 0.0005 ms. Sixteen real Android checks pass, including thin stems, holes, cache immutability and crop matrix composition. These are geometry microbenchmarks, not measured AOD FPS. Actual 0.8.2 animation smoothness remains to be confirmed.

## Edge artifact

The user reports a cyan rim/raised appearance with depth and says it remains with Clock blur Off. A depth-on/off screenshot pair is saved in `work/depth-rim-{on,off}.png`. The exact visual cause remains unconfirmed. The bitmap drawing hooks did allow parent/container/digit hooks to erase the same antialiased edge repeatedly; 0.8.2 suppresses nested bitmap mask passes. Native blur clipping remains separate because its surface is composed outside that canvas. This is a concrete rendering correction, not a claim that the user's rim is already fixed.

The existing 54 JVM contracts passed for the 0.8.x changes; the final Android build and signature checks are part of the local build. Direct checks still needed: AOD screen-off/on, one-detection live pan/zoom, manual Off → depth On without Apply/reopen, and rim comparison.
