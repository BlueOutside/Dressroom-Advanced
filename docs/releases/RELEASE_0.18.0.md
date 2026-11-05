# 0.18.0-alpha: initial Clock Style depth port

Enable Samsung Object Capture subject detection and live crop/zoom tracking for the inspected P613 Dressroom 2.9.60.50 / Clock Style 8.9.35.60 combination. Validate the named SDK API before exposing the editor controls. Access the older private crop drawable getter without assuming it is public. Bind the 8.5 weather SurfaceView handoff separately.

Reuse the existing detection worker, full/detail mask merge, manual-stretch ownership, native Apply snapshot and saved source-mask metadata. Clip Clock Style render nodes so cached idle clock contents follow the mask. Add widget depth policy and saved clock-profile synchronization to its existing widget adapter. The separate 8.5 AOD provider remains gated; its native blur/depth surfaces are not ported by this build. No device-wide or unsupported-device blur forcing is reintroduced.

Actual detection, crop alignment, idle rendering and Apply/reopen behavior require tablet validation. SDK/native-library presence alone is not detection success.
