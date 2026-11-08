# Shared One UI 9 foldable support

Starting with 1.0.11-oneui9-fold-test, eligibility is based on One UI 9 and
Samsung's LOCKSCREEN/COVER subdisplay capabilities. No model name or Fold8
exception is used. The module's existing API 36/37 support boundary remains.
Book-style and clamshell capabilities are both recognized. One UI 8.5, other
One UI major versions, unknown capabilities and non-fold devices do not enter
this foldable path.

Screen profiles, wallpaper identity, Extras, screen-change refresh, vertical
default docking and separate widget-depth surfaces use this shared eligibility.
The native profile adapter checks its storage and SDK method/field contracts
before installing hooks, rather than requiring one model or numeric AOD
version. Migration initializes every native ProfileScreen enum value, covering
native main-screen variants such as Fold and Tablet. Missing contracts disable
that capability with a diagnostic; unrelated features retain their adapters.

The 1.0.14 port uses upstream's signature-based shim system. Known inspected
names are signature-checked hints, followed by unique structural discovery;
ambiguous matches disable the capability. The former AOD8957 and Editor3001
translation adapters are superseded by shared clock, glass, editor, widget,
media and Now Bar contracts. FoldProfileBinding resolves the complete native
migration transaction before installing hooks. No model/version selection is
used for the foldable feature path.

The original Fold8 compatibility and screen-profile notes retain the historical
device investigation. Their old model/version restrictions describe earlier
test builds; this document describes current eligibility. Physical validation
remains on SM-F971B with Dressroom 3.0.01.20 and AOD 8.9.57.1. Other models,
including Flip cover UI, still need live validation and compatible app bindings.

Validation: 264 JVM checks (including 12 model-independent capability cases),
206 targeted Android checks, 25 migration/settings checks, 24 Extras/wallpaper
checks and 34 synthetic native-profile checks pass. The installed-APK binding
probe also validates the full native storage contract before hook installation.

## Post-Apply wallpaper identity: 1.0.12

A cover-clock/depth save exposed a separate timing bug: the current cover
wallpaper ID was 68 while the last clock profile was saved under the frozen
pre-Apply ID 66. The inner wallpaper ID remained unchanged, so inner settings
continued to work.

Clock and widget commits now resolve the committed wallpaper ID after native
Apply completes. They use the snapshot's captured screen, not the phone's
current posture, and retain its orientation. A missing committed ID fails
instead of silently saving under an obsolete one. Existing records are retained.

ScreenProfileProbe has 32 checks, including changed post-Apply IDs, an Apply
completing after a posture change, retained depth/orientation, immutable editor
snapshots and unavailable-ID handling. The 206 targeted Android regressions,
24 parity checks, 34 native profile checks and APK binding probe pass as well.
The initial re-read at the acknowledgement was still too early: another live
save landed under 68 while the native cover ID advanced to 70.

1.0.13 adds provider-owned, bounded post-Apply rebinding. For five seconds after
a successful fold-screen clock/widget commit, it follows changes to that
snapshot's screen-specific wallpaper ID and writes the same frozen profile to
the new identity. Unchanged IDs cause no extra settings write. Overlapping
commits are serialized with the follow-up and replace the older request for
the same layout; other screen/orientation requests remain independent.
After expiry, unrelated wallpaper changes are ignored. Non-fold and Extras
commits do not enter this path. Existing pre-save records remain intact.

PostApplyWallpaperProbe adds 16 deterministic checks for delayed/repeated ID
updates, frozen screen/orientation, depth retention, overlapping Apply,
independent widget requests, expiry and unavailable IDs. The 1.0.13 corrective
build is installed. After reapplying on the cover, the user confirmed manual
stretch remains applied. A read-only check found the active cover wallpaper ID
and saved clock profile both at 74, with manual stretch, depth and a mask
reference retained. The inner wallpaper remained at 62.

## Signature shim port: 1.0.14

Ported onto upstream d8d6ca8, including its depth-source, widget refresh and
object-capture fixes. Separate screen settings and the bounded post-Apply
wallpaper rebinding are retained. Modern editor ABI selection checks the clock
helper bind signature rather than using weather capability as a proxy. Known
APK hints avoid full DEX scans on the inspected pair while retaining signature
validation and ambiguity rejection.

Widget preview ownership resolves fields by role and checked types. The older
portrait policy is scoped to the active widget preview; an unavailable optional
rotation guide does not disable the widget controls. Placement is registered
before depth sampling so free-position backgrounds use the current frame.

Fold transitions finish pending media-return registrations, release clock depth
suspension and clear artwork widget occlusion before switching profiles. Explicit
per-orientation widget depth choices cannot reimport the retained legacy policy.
Shadow-only legacy clock imports seed both sibling screens before marking the
import complete.

The installed-APK probe resolves editor/widget, clock/font/source, transition,
glass/depth, Now Bar, media and native-profile contracts. This validates native
declarations; visual and hook lifecycle behavior still requires a live reload
and editor/lockscreen testing. Earlier user confirmations above apply to 1.0.13
and earlier, not automatically to this port.

Validation for 1.0.14: Java 17/D8 build and APK signature verification passed;
297 JVM checks, 584 Android checks across the full configured suite and 34
synthetic native-profile checks passed. The installed Samsung APK contract
probe also passed. Installed in place as versionCode 1014 with existing data
retained; scoped processes were restarted. The live editor log confirms the
signature shim installed and clock/widget saves reached the native completion
callback. The user then confirmed Advanced opens on cover and inner screens,
widget selections remain separate and the reapplied cover clock stretch/depth
change survives on the lockscreen. Other devices and every media/fold-animation
timing remain unverified.
