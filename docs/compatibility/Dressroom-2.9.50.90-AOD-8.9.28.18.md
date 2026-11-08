# Dressroom 2.9.50.90 / AOD 8.9.28.18

APK pair supplied for the missing Advanced report. The user subsequently confirmed that Advanced appeared. The initial batch audit resolved 17 of 23 groups. With the 1.0.13 signature changes it resolves 22 of 23. It loaded these APKs through isolated class loaders on the tablet; it did not install them or change the tablet's setup.

## Passing groups

- Clock panel controller and native tabs
- Final wallpaper-save completion and wallpaper source
- Object Capture SDK/crop declarations
- Editor configuration callback and stretch setters
- Widget resize adapter and menu-item constructor
- Provider clock renderer, height normalization and font identity
- Provider font axes and clock source/measurement callers
- Native clock blur and widget blur submissions
- Native grid options and widget lifecycle callbacks
- Wallpaper fade

## Remaining gap

The widget rotation/restore group reaches a missing native API: `ClockAssistData.getWidgetAlignTypeValue()`. The 2.9.60.50 editor supplies it; 2.9.50.90 does not. This is an ABI difference, not another ambiguous class match. Custom rotation remains unavailable through that adapter on this older editor.

## Resolved signatures

- Widget toolbar: menu array, Activity/View constructor, and the method that reads that array and rebuilds native child views.
- Widget metrics and placement: full parameter lists restricted to the main-screen repository rather than sublauncher.
- Widget sizing: use the older 8.5 size-spec getter when the newer calculator is absent. The 9.0 static sizing fallback is retained.
- Media depth: deoptimize actual DEX callers of the public expansion setter. No unrelated Object observer is selected and no unused private boolean field is required.
- Artwork holder: require its full constructor shape as well as the music page/model dependencies.
- Now Bar identity: require the native key builder to read notification ID, Now Bar key and package. The stack-count callback also has to expose its zero-argument result method.

## Binding regression matrix

| Editor | Provider | Result |
| --- | --- | --- |
| Dressroom 2.9.50.90 | AOD 8.9.28.18 | 22/23, missing rotation API above |
| Dressroom 2.9.60.50 | Clock Style 8.9.35.60 | 21/21 |
| Dressroom 2.9.60.50 | AOD 8.9.32.95 | 23/23 |
| Dressroom 3.0.10.38 | AOD 8.9.66.1 | 19/19 |

Counts differ because each family runs its own binding groups. These are read-only resolution checks, not a claim that every visual feature works on each combination. Known working paths are preserved; new fallback paths add no obfuscated-name aliases.

The audit continues between groups, but a constructor can still stop before checking all of its internal dependencies. A passing group confirms declarations and resolution, not live behavior, native SDK initialization, or successful LSPosed hook installation.

`tests/integration/CompatibilityBatchProbe.java` prints every group result in one run. Arguments: editor APK, provider APK, editor version code. This pair uses version code `295090000`.
