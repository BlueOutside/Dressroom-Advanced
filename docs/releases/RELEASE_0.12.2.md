# Native settings pages and color/blur - 0.12.2 alpha

Installed versionCode 84. The user authorized discarding the test color draft before restarting Dressroom. No settings-schema migration.

## UI

Accordion sections are replaced by native secondary tabs: Clock uses Layout, Effects, Depth, Date; Widgets uses Layout, Position, Effects beneath Widgets/Extras. `PanelTabs` changes group visibility without rebinding the renderer or staging/saving settings. Selection survives ordinary panel refreshes.

Controls come from Dressroom's `clock_panel_layout`. JADX confirms raw TabLayout method `t()` selects Samsung's native sub-tab styling. Dividers use `clock_panel_item_divider` and native insets. Slider captions/controls are grouped together, with the native font-weight caption size instead of the weather row's minimum height.

Clock Layout/Effects and widget Layout were inspected on the phone; screenshots are `work/ui0122-layout.png`, `work/ui0122-effects.png`, `work/ui0122-widgets.png`. No new inflation/callback errors appeared in inspected logs. The user approved the page-navigation direction in 0.12.0 and requested separators. Final UI preference feedback and broader Apply/cancel regression coverage are pending.

## Opaque color and blur

AOD `AbsClockView.E0()` derives blur foreground visibility from custom tint alpha, while preset/adaptive colors use 0.35/0.5 based on brightness. `cq0.d` maps that input to native target alpha. A fully opaque custom color can cover much of the glass.

For module-forced blur with an opaque custom tint and no explicit visibility override, the hook now supplies the native brightness-dependent 0.35/0.5 input. RGB tint is unchanged; explicit visibility, translucent colors and stock behavior retain precedence. This does not recolor the sampled background or replace the blur renderer. The user reported some effect but said it looked off: this remains under visual review, not fully fixed.

Nine opacity checks and all 100 current JVM checks pass. Signed APK/v3 verification passes. SystemUI/AOD startup rendering passed on 0.12.0; 0.12.1/2 change only editor presentation and are loaded in Dressroom. Runtime scopes keep equivalent 0.12.0 code until their next reload.

## Remaining compatibility audit

| Area | Verified limitation | Next requirement |
| --- | --- | --- |
| Landscape depth | Runtime skips loading the full source matte and center-crops the baked screen mask into current metrics. | Map the canonical matte through Samsung's actual rotation/crop transform. Rotating a baked portrait mask cannot restore omitted subject pixels. |
| Foldable/cover profiles | Clock settings reject nonzero display IDs; widgets exclude cover/informative types. Settings use main-wallpaper identity. | Resolve display/which-mode identity and independent crop/profile ownership before removing guards. |
| Tablet UI | Clock panel uses a 190-310 dp height range; bottom widget panel requests 376 dp. Real wide-window layouts are untested. | Inspect native wide-screen owners and available panel/window constraints. |
| A-series Now Bar blur | Current firmware provides Samsung's native SemBlurInfo renderer; another model's eligibility and implementation are unverified. | Inspect target firmware's gates, blur renderer/assets and capabilities. A gate override alone cannot establish support. |

These are open work, not newly supported features. Recovery snapshot: `work/source-before-ui-012/` contains the 0.11.5 source/APK.
