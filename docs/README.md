# Project notes

## Overview

- [Architecture](ARCHITECTURE.md): source folders and rendering ownership.
- [Current validation](VALIDATION.md): what has been checked on each device.
- [Test suites](../tests/README.md): JVM, Android, native glyph and integration checks.

## Compatibility

- [Compatibility audit](compatibility/README.md): One UI 8.0+ surface audit and requirements.
- [Implementation status](compatibility/IMPLEMENTATION.md): supported bindings, capabilities and gaps.
- [One UI 8.0 preview](compatibility/ONE_UI_8_0.md): One UI 8.0 compatibility notes and tested APKs.
- [One UI 9.5 beta](compatibility/ONE_UI_9_5_BETA.md): One UI 9.5 beta shim bindings and audit status.
- [Adapter design](compatibility/ADAPTER_DESIGN.md): capability-gated shim model.
- [Shim map](compatibility/SHIMS.md): runtime shims and inspected package mappings.
- [APK feature audit](compatibility/APK_FEATURE_AUDIT.md): cross-APK feature and signature checks.
- [Font bindings](compatibility/FONT_BINDINGS.md): file font and variable-axis renderer mappings.
- [One UI 9 foldables](compatibility/ONE_UI_9_FOLDABLES.md): foldable cover/inner screen design.
- [Foldable parity](compatibility/FOLDABLE_PARITY.md): foldable validation notes and parity checklist.
- **Package inspections**: [Fold8 3.0.01.20](compatibility/FOLD8_3.0.01.20.md), [Fold8 screen profiles](compatibility/FOLD8_SCREEN_PROFILES.md), [Dressroom 2.9.60.51](compatibility/DRESSROOM_2.9.60.51.md), [AOD 8.9.32.95](compatibility/AOD_8.9.32.95.md), [AOD 8.9.36.23](compatibility/AOD_8.9.36.23.md), [SystemUI 16 4006](compatibility/SYSTEMUI_16_4006.md), [Dressroom 2.9.50.90 / AOD 8.9.28.18](compatibility/Dressroom-2.9.50.90-AOD-8.9.28.18.md).

## One UI 9 Deep Dives & Aliases

- [One UI 9 clock deep dive](research/ONE_UI_CLOCK_DEEP_DIVE.md): end-to-end Samsung lockscreen clock pipeline.
- [Clock ownership map](research/CLOCK_OWNERSHIP_MAP.md): native/module property writers, host and editor event routes, and cross-version evidence.
- [Clock animation engines](research/CLOCK_ANIMATION_ENGINES.md): timing, cancellation/finalization, editor physics and 9.5 engine changes.
- [One UI 9 Dressroom deep dive](research/ONE_UI_DRESSROOM_DEEP_DIVE.md): editor architecture, save pipeline and preview stack.
- [One UI 9 SystemUI deep dive](research/ONE_UI_SYSTEMUI_DEEP_DIVE.md): wallpaper engines, ambient flags, blur and keyguard host.
- [One UI 9 widget deep dive](research/ONE_UI_WIDGET_DEEP_DIVE.md): AOD/lockscreen widget and complication pipeline.
- [One UI 9 depth deep dive](research/ONE_UI_DEPTH_DEEP_DIVE.md): subject/background regions and motion IPC.
- [One UI 9 bug findings](research/ONE_UI_9_BUG_FINDINGS.md): inspected 9.0 issues and implementation fixes.
- **Alias tables**: [One UI 9 Dressroom aliases](research/DRESSROOM_9_ALIASES.md), [One UI 9 AOD aliases](research/AOD_9_ALIASES.md).
- [One UI 9 SystemUI aliases](research/SYSTEMUI_9_ALIASES.md): host animation and callback names verified through source and xrefs.

## Feature & Pipeline Notes

- **Clock & blur**: [Clock layer handoffs](research/CLOCK_LAYER_HANDOFFS.md), [Clock pipeline audit](research/CLOCK_PIPELINE_AUDIT.md), [Clock rendering](research/CLOCK_RENDERING.md), [AOD render pipeline](research/AOD_RENDER_PIPELINE.md), [Unlock blur](research/UNLOCK_BLUR.md), [Forced blur experiment](research/FORCED_BLUR_EXPERIMENT.md), [UI polish notes](research/UI_POLISH.md).
- **Widgets**: [AOD widget pipeline](research/AOD_WIDGET_PIPELINE.md), [Widget customization audit](research/WIDGET_CUSTOMIZATION_AUDIT.md), [Widget native features](research/WIDGET_NATIVE_FEATURES.md), [Widget outline audit](research/WIDGET_OUTLINE_AUDIT.md).
- **Now Bar & media**: [Now Bar stack](research/NOW_BAR_STACK.md), [Media 8.5 notes](research/MEDIA_8.5.md), [Media return investigation](research/MEDIA_RETURN_INVESTIGATION.md).
- **Depth & audits**: [Depth research](research/DEPTH_RESEARCH.md), [Depth upgrade research](research/DEPTH_UPGRADE_RESEARCH.md), [Native limits](research/NATIVE_LIMITS.md), [8.5 lifecycle audit](research/AUDIT_8.5_LIFECYCLE.md), [0.10 bug audit](research/BUG_AUDIT_0.10.md).

Samsung APKs, signing keys, device recordings and raw logs are kept outside the public repository.
