# One UI 8.0 preview

| Component | Version checked |
| --- | --- |
| Dressroom | 2.9.10.46 on SM-G990B2 |
| AOD | 8.9.15.50 on SM-G990B2 |
| Clock Style | 8.9.15.39 from a separate APK |
| SystemUI | Android 16 build from SM-G990B2 |

The preview binds the Advanced clock panel, size and position, manual stretch, and Apply on Dressroom 2.9.10.46. Both clock providers expose Samsung's dynamic `HEGT` clock and accept a native height list. This editor converts drag geometry through its own stretch repository and uses an older component-completion callback to finish Apply.

Depth uses the older Object Capture API in Samsung's wallpaper SDK and the drawable used by the 8.0 crop view. ArcSoft is present on the SM-G990B2. The module initialized it and detected a subject in a generated image on that phone.

On the SM-G990B2, depth survived Apply and reopening Dressroom. It also rendered after restarting SystemUI with the module enabled. The Clock Style APK was checked for compatibility bindings without a device test.

Advanced is available at the bottom of the lockscreen editor, including wallpapers that hide Samsung's toolbar. Clock → Advanced → Layout also has a shortcut. If the preview has no widget controller, the shortcut opens Extras.

The custom Now Bar message can appear with lockscreen notifications turned off. The message still follows Now Bar's visibility and dismissal behavior.

Widget placement and subject depth use the older widget lifecycle. On the SM-G990B2, widget scale resizes contents and backgrounds together, and a widget placed in a second row stays in its cell after Apply. The grid controls require matching editor and provider bindings. The picker refreshes its catalog when a grid change alters eligibility for two-row widgets.

Extras includes widget and Now Bar blur controls where Samsung's window blur is available. Blur styling works with the 8.0 framework's rectangular clip. Arbitrary-path clipping of the blur surface requires a newer Samsung framework.

Inline seconds use the older dashboard launch path and fit inside the visible clock container. On the SM-G990B2, seconds opened on the first tap after SystemUI restarted without applying a new editor draft. Both 8.0 clock providers passed the binding check. Extras also offers fullscreen artwork and a switch to hide the clock in the expanded player. The artwork and wallpaper-fade bindings passed on both providers.

Clock glass and additional Now Bar stack slots are outside this preview.

## Music page attachment crash

The 2026-11-23 S901E report crashes in `ViewGroup.dispatchAttachedToWindow`
while the AOD pager's RecyclerView adds a page. The supplied 8.9.15.39 sample
matches the reported `ml.c` child-helper and `Xv0.b` layout-manager call chain;
the affected device's exact provider version has not yet been confirmed.

The 8.0 framework snapshots the child count and array, then dispatches each
child's attachment before accessing the next child's visibility. The shared
lyrics controls listener previously moved the lyrics view out of its ancestor
music page synchronously during this traversal. That can leave the next slot
null and matches the reported exception. This does not require lyrics to be open.

Lyrics and toggle attachment now run in a coalesced main-handler task, including
initial construction and retargeting. The task resolves the current controls,
rejects detached or different-window hosts and updates bounds after reparenting.
The correction is shared with later providers because it fixes our view lifecycle
without changing Samsung bindings or catching framework exceptions.

Signed build: `2.0.6-8.0-attach-fix`, version code `20006018`. The build, 29 JVM
suites (including 438 lyrics checks), repository/layout checks and diff checks
passed. Confirmation on the reporting 8.0 device is pending; no live reproduction
or on-device fix confirmation is claimed.
