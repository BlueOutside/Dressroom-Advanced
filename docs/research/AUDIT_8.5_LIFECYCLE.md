# One UI 8.5 lifecycle audit - first pass

Target: P613 custom ROM, Dressroom 2.9.60.50 and Clock Style 8.9.35.60. Inspected through JADX MCP on 2026-11-04. These findings do not establish stock 8.5 AOD or foldable compatibility.

## Native behavior checked

| Owner / method | What the inspected code does | Adapter consequence |
| --- | --- | --- |
| EditBaseActivity.onConfigurationChanged | Delegates to P3.f, which iterates all editor components | A rotation is a component lifecycle event, not necessarily a new Activity or clock |
| P3.f.onDestroy | Destroys every editor component before its own superclass cleanup | Delayed editor work must verify that its owner/session still exists |
| ComplicationWidgetPluginController.initialize(ViewGroup) | Binds the outer root and registers observers | Widget contents may arrive later |
| ComplicationWidgetPluginController.updateWidgetLayout | Removes existing children, sets up the container, starts module-info reordering | A stable outer root does not mean stable child views |
| ComplicationWidgetPluginController.release | Removes all children, sets container=null and unregisters native observers | Remove module listeners and cancel pending module refreshes too |
| ComplicationWidgetPluginController.onDensityOrFontScaleChanged | Reconfigures the grid, updates layout, then rebuilds the widget size table | Keep deferred refresh after the native stack completes; do not force a refresh in its middle |
| ComplicationWidgetPluginController.onFoldStateChanged | Updates the grid's dark amount | This is not evidence that all display/profile geometry is rebuilt |
| KeyguardClockProvider.updateWallpaperColorsItem(Object) | Publishes wallpaper colors and the clock-area color item, without requiring a clock-type change | Use this native handoff to check wallpaper identity and invalidate cached widget settings |

## Fixed in 0.18.5

- A queued callback could consume a newer callback's pending force flag after clear/reinitialization. Refresh tickets now validate their own lifecycle before consuming work. A cancelled ticket cannot consume its replacement.
- Runtime root listeners were left installed on release/replacement. A pooled or reused root could accumulate anonymous callbacks. RootLayoutWatch removes the old listener before binding another and on controller release.
- The reference editor's widget preview had the same anonymous-listener pattern. It now uses the shared lifecycle helper. The shared helper is Android-tested; reference-phone execution remains pending.
- The 8.5 editor's posted resize work checked the owner only before posting. It now also checks owner, session generation and attachment when it runs.
- Widget settings were cached by orientation, with no invalidation tied to an external wallpaper replacement that kept clock type/font unchanged. The native wallpaper-color handoff now checks the cached wallpaper ID and refreshes owners when it differs. No wallpaper-ID query was added to animation-frame callbacks.

The context field and wallpaper method signatures also exist in the original 9.0 AOD DEX. The updated binding probe resolves them against the installed tablet APK. This is declaration/ABI evidence, not visual validation on the reference phone.

## Remaining investigation

- Multi-touch and ACTION_CANCEL during custom widget group dragging, especially owner/rotation changes mid-gesture. Read the native dispatch/repository path before changing event ownership.
- Apply while subject detection is pending. freezeMask currently discards unfinished mask fields and shows a warning; test the resulting native save and editor reopen explicitly before changing commit semantics.
- Native save suspension and Activity recreation. Preserve the distinction between the normal save coroutine and skipped-save callbacks; do not attach commits to a generic completion listener.
- Density/font-size changes with mixed widget shapes. The native callback ordering is mapped, but provider content sizing still needs a targeted device test.
- Fold/display transitions and secondary-display exclusions. The current main-display guard remains; a tablet test does not validate a Fold cover display.

## Validation boundary

The signed build passes 129 JVM checks (including 12 refresh-ticket cases), 16 Android suites / 407 checks (including five real-view listener cases) and the tablet binding probe. Installed on the P613 with editor/runtime scopes reloaded. The user confirmed repeated editor opens, portrait/landscape rotation and lock/unlock preserve widget size, placement and depth on the P613. External wallpaper replacement still requires device validation; this audit does not claim that every feature or lifecycle path has been reviewed.
