# One UI 9.5 beta

The `compat/oneui95` shim was checked against Dressroom 3.0.50.60, AOD 8.9.81.55 and the matching beta SystemUI. It uses native method signatures, resource tags and DEX call sites to resolve the beta contracts.

- Dressroom resolves the Advanced panels, native clock size and position, widget toolbar, grid and cell measurements, drag binding, and resume lifecycle.
- AOD resolves clock stretch and blur, depth, fold profiles, Now Bar stack and glass, Now Brief blur and fill, and fullscreen artwork.
- The beta stores widget dependencies behind providers, moves the clock blur host into its strategy, and builds AOD clock heights separately from the lockscreen. The shim resolves each path and preserves the native unstretched measurement alongside the saved height.
- Dressroom uses the original gallery URI to identify a wallpaper when its working copy changes. The Now Bar tint fade defers to Samsung when it is already animating the tint.
- Colored Now Bar pills keep their native fill until the replacement blur can draw. Now Brief also checks GenUI's per-card blur flag, preset and opacity before clearing its fill. Blur recovery after wake or the notification shade restores the prior drawable if the framework fails to create a replacement.
- After wake, visible Now Bar pills rebind their window blur. A retained opaque AOD tint can use the pre-sleep request when the same item and bounds are still active.
- The clock page shares one observer across media, screen animation, and other events. Stretch restoration now waits for a real collapse and the native return animation. A collapse during wallpaper AOD drops retained artwork and pending frame decodes.
- Widget layout settings are reapplied after Samsung's layout coroutine completes. Widget docking follows the view returned by the active clock page, which avoids selecting a secondary clock by size.
- The runtime re-reads clock and widget profiles when Android reports a wallpaper change, including when the clock style and font stay the same.
- AOD widget blur resolves the beta's direction-aware curve and tint helpers. Keeping lockscreen colors changes the amount passed to those helpers while Samsung still tracks the actual wake and sleep transition.

## Binding audit

| APK pair | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| One UI 9.5 beta Dressroom, AOD and SystemUI | 52 | 0 | 1 |
| One UI 9.0 reference Dressroom and AOD | 45 | 0 | 0 |
| One UI 8.5 Dressroom and AOD | 37 | 0 | 0 |
| One UI 8.5 Dressroom and Clock Style | 25 | 0 | 0 |

The supplied beta AOD lacks the cover-screen Now Bar media card, so the audit skipped that check and ran the main-screen unlock check separately. The APKs were loaded on a One UI 9.0 phone for read-only binding checks. Widget editing, clock placement after Apply, depth persistence and AOD wake animations still need a live 9.5 check.

## Wallpaper parallax

SystemUI's gyro-driven theme wallpaper engine is present in the 9.0 reference. The renderer for the reported 9.5 effect that adds parallax to a gallery photo is still unidentified. Compare the active wallpaper component and type on 9.5 with the effect off and on. Depth currently tracks a single wallpaper transform. If the effect moves the foreground separately, depth needs that layer's transform to keep the mask aligned.

One UI 8.0 remains a separate shim target in the [compatibility audit](README.md).
