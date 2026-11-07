# 0.7.4 alpha - native widget integration

Fixes the 0.7.1 custom widget layout and non-scrolling Advanced panel. The panel now uses Samsung's NestedScrollView host. Its Rotate action invokes Samsung's existing `cg.v.a` transaction, including the native animation, provider update, assist geometry and completion callback. The stock rotation guide and Advanced share the same model.

The module no longer changes `WidgetGridData.isGridType`. That flag selects another widget backend; custom capacity now extends the complication controller's dimensions and native larger-grid rules. Repacking calls the native sorter on existing holders, preserving AppWidget IDs and rectangular provider spans. Direction is read from the native model; capacity follows Samsung's Default/Secondary position convention. Contradictory dimensions are rejected with an explanation.

Apply finalizes the widget list using `op.m.c` before freezing the module extras, preserving primary and secondary positions in Samsung's store. The editor's initial binding and resume each have portrait-mode restrictions; only the custom widget capability bypasses those restrictions. The actual screen orientation remains unchanged. Custom editor bounds are clamped inside the phone preview. Samsung default placement retains native positioning; explicit docking/free placement remains an extra override.

SystemUI's widget owner reads Samsung's saved orientation from AODDataManager's native G flow. This supplies the custom grid on phones where the native large-device orientation observer is disabled. It does not derive the runtime arrangement from a second independent orientation preference. Native providers, listening, IDs, content, blur lifecycle and depth ownership remain in place.

## Validation

- Signed APK compiled, verified with APK v3 signing, installed as versionCode 53. SystemUI reloaded with the final build.
- 51 Java checks passed: reflection 6, stretch identity 6, save/lifecycle 18, widget layout 21.
- On the phone, Advanced scrolls to Placement, Appearance and Reset.
- Existing battery (2×1) and activity (1×1) widgets rotate to a native 2×4 grid while retaining their shapes and IDs 297/298. Corrected preview bounds show both widgets fully inside the screen.
- Apply writes both native position sets; after force-stopping/reopening Dressroom, vertical arrangement renders without opening Advanced first.
- Captures show vertical widgets on both settled AOD and awake lockscreen after SystemUI reload. Logs report nativeAlign=Vertical, grid=2×4, independent widget depth Off.
- The standalone app_process contract probe was killed by the phone in this session; its expanded signature checks were not counted as passing. Actual hooked editor/runtime calls provided the integration evidence above.

The saved phone configuration was left as the 2×4 vertical test grid, scale 100%, Samsung default placement and widget depth Off. Rotate in Advanced returns it to horizontal; Custom grid can then restore the stock phone capacity. No providers were added or removed during testing.

## Remaining scope

Other provider variants, every capacity combination, large-grid cancel behavior, custom scale and detached placement through all transitions still need broader device testing. Native portrait positioning places a vertical grid beside the clock in Dressroom and below it on this phone's lockscreen/AOD; explicit placement overrides are separate settings. Cover screens and other One UI versions are not validated. Main wallpaper-preview differences remain deprioritized at the user's request.
