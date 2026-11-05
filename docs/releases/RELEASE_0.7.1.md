# 0.7.1 alpha — widget customization

Adds a native Advanced button to the editor toolbar, opening a Widgets panel with separate grid capacity and physical scale, horizontal/vertical arrangement, clock-side docking or free placement, independent depth, and native live-blur policy. Widget drafts commit through the native Apply completion independently of the clock Advanced tab. Settings are scoped to the wallpaper; existing clock settings are retained.

The first 0.7.0 test exposed oversized/malformed vertical widgets. Native vertical sizing fitted one row to the screen width. Version 0.7.1 calculates cell scale against the normal phone baseline, preserves provider width/height spans, and corrects the editor container dimensions. Custom direction no longer implicitly selects a clock side. The panel now uses Samsung's smaller WIDGET window type so the preview remains visible.

The user's saved widget depth Off was present in storage while the old SystemUI process still rendered widgets. After installation and SystemUI reload, device logs explicitly report build 0.7.1, mode=2 and depth=false for the selected clock profile. This verifies setting consumption, not the final visual result.

Validation: signed APK built and installed as versionCode 50; existing Java checks passed (30), new layout checks passed (16), and the on-phone Samsung signature/settings probe passed (53). Device logs confirm grid hooks installed and SystemUI consumes independent widget depth. The corrected vertical layout, free-placement transitions, large-provider eligibility, and final lockscreen/AOD appearance still require visual testing. Cover-screen customization and other One UI versions are not validated.

Stable 0.6.6 source and APK remain backed up. No widget IDs are replaced by the customization controls. Live blur On retains Samsung's native policy; it does not force unsupported AOD blur surfaces.
