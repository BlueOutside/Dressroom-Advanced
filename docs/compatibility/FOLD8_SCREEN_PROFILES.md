# Fold8 cover and inner screen investigation

## Runtime follow-up: 1.0.9

The user reported that saving three cover widgets replaced the visible inner
selection. A fresh SystemUI dump showed that the native Fold screen record
still held the five original IDs (623, 624, 629, 630, 634), while Bar and primary
held the cover IDs (636, 637, 638). Storage isolation worked; the active inner
ComplicationWidgets item nevertheless contained the three cover IDs.

The runtime has a separate legacy overwrite path:
`AODDataManager.r(String)` copies the global enabled-widget list into its
current screen-scoped item. Also, migration readiness in 1.0.7 was local to the
provider process, while AOD classes are loaded separately in SystemUI.

1.0.9 hooks the direct profile selection/alignment getters, installs the adapter
in the discovered Fold8 runtime classloader, and publishes verified migration
readiness through the module provider. Only the AOD UID may mark a profile
ready; existing trusted readers may query it. After confirmation, the runtime
retains its native screen item instead of replacing it through the legacy
global-list callback. No stored selection is rewritten by this follow-up.

The 21 recovery/settings checks, 34 synthetic native-profile checks and APK
binding probe pass. 1.0.9 is installed and scoped processes reloaded. After the
user reopened the inner editor, they confirmed the selections are separate.
SystemUI's live DualDisplayMain:Landscape:LockScreenOnly item now contains the
original five IDs (623, 624, 629, 630, 634), matching the preserved Fold record;
the cover record retains its three separate IDs. The earlier synthetic tests
did not exercise this legacy runtime overwrite path.

## 1.0.7 implementation

The experimental adapter is now integrated and installed as 1.0.7-fold8-test
(1007). Original provider adapters remain present. The new native profile path
is restricted to SM-F971B/API 37 and AOD 8.9.57.1 (895701000).

OneUi9Fold8Profiles reads selection/alignment from the selected screen record
only after migration has completed. Migration seeds both screens from the
effective shared widget list, retains clock data, uses Samsung's native writer,
and rereads the result before enabling independent reads. A locked or missing
profile retains the native shared policy. Logical profile aliases are resolved
through Samsung's mapping table to the actual database row before choosing a
migration journal.

The journal is an AtomicFile in AOD's own files directory, guarded by a
cross-process file lock. It stores fingerprints and completion state, not
widget content. A pending operation whose expected fingerprint is already
present is finalized without reseeding; an unexpected conflicting record is
left unchanged. This covers interruption between the native write and journal
completion. Native draft cleanup filters IDs still referenced by committed
profiles, and defers deletion when the reference query is unavailable.

ScreenIdentity uses Samsung's fold state and listener, independently of
orientation or Android display number. Clock/widget settings and editor draft
keys now include main/sub identity; runtime caches and clock effects refresh
on fold changes. Legacy preference records are retained and copied into both
screen keys before independent editing. The cover does not inherit an inner
screen's baked depth mask. Existing Extras options remain global.

Validation: 252 JVM checks, 203 targeted Android regression checks, 21 new
settings/recovery checks, 34 synthetic native-profile checks, APK binding
resolution, reflection inventory and diff checks passed. An existing media
drawing assertion failed intermittently during one regression invocation and
passed on the focused rerun. The detached orientation fixture now initializes
the main looper required by Samsung's fold listener.

Installed package version 1007 was confirmed and scoped processes restarted
through root killall. Runtime clock/widget/depth hooks and the AOD screen-profile
adapter report installation. After the user opened the inner editor, the AOD
provider logged `Fold screen widget migration verified` at 20:47:07 and created
the database-row-1 migration journal. Independent editor saves still need user
confirmation; isolated tests are not certification of the live Apply flow.

The sections below retain the investigation and prototype history.

Read-only investigation on 2026-11-07 with module 1.0.6-fold8-test,
Dressroom 3.0.01.20 and AOD 8.9.57.1. No editor changes were saved and no
replacement module was installed during this comparison.

## Findings

Separate layouts and widget selections appear implementable, but there are two
independent scopes to address: the module's own preferences and Samsung's native
widget-list lookup policy. A distinct native item ID alone does not make the
widget selection independent.

The user opened each editor manually. SystemUI's AODDataManager dump reported:

- Folded: `DualDisplaySub`, `Portrait`, `LockScreenOnly`, with item ID
  `complication_widget:0:DualDisplaySub:Portrait:LockScreenOnly:`.
- Unfolded: `DualDisplayMain`, `Landscape`, `LockScreenOnly`, with item ID
  `complication_widget:0:DualDisplayMain:Landscape:LockScreenOnly:`.

The inner screen reported Vertical alignment and five native widget entries.
Its editor screenshot showed the matching battery, calendar, follower, weather
and task widgets. The cover screenshot showed a smaller visible subset; that
does not establish a different saved selection because clipping and available
space can hide entries. Cover widget IDs were not separately captured before
the phone was unfolded, so this investigation does not claim a direct ID-by-ID
comparison between both active screens.

## Samsung storage and lookup

The inspected editor SDK has independent display types `DualDisplayMain` and
`DualDisplaySub`, plus orientation and AOD state. `gi.b` serializes these into
ItemID along with item type, index and profile. `fi.l`/`fi.q` resolve widget
items using the current display and profile. `sd.p` converts the selected
ComplicationWidgets item into the editor's WidgetData and copies edited data
back into that native item.

In the installed AOD APK, ProfileDataMediator maps DualDisplaySub to
`ProfileScreen.Bar` and DualDisplayMain to `ProfileScreen.Fold` (or Tablet on
the corresponding device family). LockProfileBundle has primary,
allStateRelevant, screenRelevant and orientationRelevant data.

However, NormalFoldProfileFieldPolicy (`aod.hw4`) chooses which fields actually
use those stores. An isolated on-device reflection probe of the installed APK
confirmed:

- `d()` (full state): wallpaperClockDataMap.
- `i()` (screen): clock position, scale, font, color and related clock fields.
- The lazy primary-field set includes **enabledWidgetsIdList** and
  **widgetAlignType**, along with visibility, shortcuts and blur fields.

`aod.dl5.c()` reads primary fields from the shared primary record. Consequently,
different screen-specific ComplicationWidgets IDs can still resolve the same
underlying widget selection and alignment. Changing only the native item ID or
adding a module preference suffix would leave this behavior in place.

`dl5.b()` copies edited fields into primary and the current screen/state/
orientation records. Existing dormant records contain older selections, so
enabling screen-specific reads must seed each screen from its effective current
selection rather than silently exposing stale data.

The temporary policy probe only instantiated the APK's policy class and read
its field sets. It did not access app providers, save profiles or call widget
binding APIs. A separate ContentResolver probe could not obtain a provider
because app_process was not a registered app caller; its result is not evidence
about provider data.

## Module scope gaps

- WidgetPreferences uses `wallpaper_<id>_main` plus orientation and scene;
  there is no main/sub dimension.
- EditorWidgets draft and dirty keys use scene plus orientation.
- WidgetEngine caches by orientation and separately checks wallpaper changes;
  a display switch with the same orientation needs its own invalidation.
- Clock SettingsScope also lacks explicit Fold main/sub identity.
- RuntimeClockData and RuntimeClockHooks reject nonzero Android display IDs.
  An Android display number is not the SDK's Fold main/sub identity.
- WidgetEngine rejects WidgetDisplayType.isCover/isInformative. Samsung's
  widget Cover category must be checked against Fold sub behavior before
  changing this guard; it is also used by other device families.

Wallpaper IDs can incidentally separate some preferences, but they do not
replace explicit screen identity. Portrait/landscape alone is insufficient,
even though the observed cover and inner configurations differed in orientation.

## Proposed implementation and validation

1. Carry native main/sub identity through editor drafts, saved layout keys,
   clock/widget reads, depth-mask scope, save snapshots and runtime caches.
   Retain orientation as a separate dimension and keep existing adapters.
2. Add a Fold8-version-scoped native field-policy adapter for independent
   widget selection/alignment. Seed both screens from their effective current
   state before enabling independent reads, and preserve the sibling record
   during a save. Do not modify visibility or shortcuts incidentally.
3. Preserve native AppWidget IDs and binding ownership across saves. Removing
   a widget from one screen must not delete a binding still used by the other;
   inspect Samsung's widget cleanup and editor draft paths before enabling it.
4. On fold/unfold, select the correct screen's native widget item and module
   settings, invalidate geometry/depth caches, and discard no unsaved draft.
5. Verify two screens in the same orientation, distinct widget selections,
   add/remove isolation, cancel/reopen, process reload, fold/unfold and depth
   rendering. Also retain all existing horizontal/vertical placement checks.

These are implementation requirements, not tested support in 1.0.6. No screen
separation hooks or settings migrations were enabled during this investigation.

## Follow-up: native save and serialization experiment

`tests/integration/Fold8ScreenProfileProbe.java` now exercises the installed AOD
APK's real profile builders, Fold policy and Samsung serializer with synthetic
widget lists. It passed **27 checks** on the Fold8 on 2026-11-07. This is an
isolated experiment, not a replacement module or an installed hook.

The candidate changes enabledWidgetsIdList and widgetAlignType from primary
fields to screenRelevant fields. In the probe, this changes only one newly
constructed policy object's cached sets. No live AOD policy is modified.

The experiment establishes that:

- Stock native getters reproduce shared widget selection and alignment.
- Moving those two properties to screen scope makes both getters independent,
  including when both screens use Portrait and after switching orientation.
- The existing native copy/save builder retains the sibling screen record.
- Widget edits preserve each screen's clock scale and all unrelated primary
  policy fields. A discarded copy does not mutate the original bundle.
- Emptying the inner list does not repopulate it from the cover list.
- Samsung's serializer retains distinct screen records and their widget IDs.
- IDs still referenced by the sibling remain in the combined screen records
  when one screen removes a shared widget.
- An unseeded sibling exposes its dormant/default selection after the policy
  change. Migration is necessary; a policy-only hook would be incomplete.

The test deliberately does not allocate, bind, remove or display any real
AppWidget. Its ID-preservation checks concern the native data records, not
observed live host deletion behavior. It does not certify fold/unfold refresh,
the editor's native Apply pipeline, or the module's own preference separation.

### Widget cleanup trace

The inspected `aod.j54.a()` gathers widget references from primary,
allStateRelevant, screenRelevant and orientationRelevant records. The host's
profile-change cleanup (`aod.f64`) and controller termination (`aod.y94`) use an
all-profiles enabled-ID list. This supports retaining a widget while another
screen still references its ID; a blanket block on native deletion is not
warranted.

There are also immediate deletion APIs. `y94.clearComplicationTiles(orgList,
widgetList)` deletes IDs present in the second list but absent from the first,
through `v01.e()` / `tb1`. The inspected editor `ic.b` save preparation calls it
for Mode entries. Package-removal and tile-removal paths can delete IDs too.
Those cases need explicit coverage before claiming complete selection isolation,
particularly for cancellation, Modes and concurrently held editor drafts.

### Integration order

Implement a version-scoped policy adapter together with migration and module
screen identity, rather than shipping the policy change on its own. Seed both
screen records from their effective current selection, preserve distinct clock
data, and record migration completion only after persistence is verified. A
restart between the native save and a separate migration marker must not cause
the next start to overwrite an already independent selection.

Native empty profile IDs resolve to database profile 0 in ProfileDataMediator.
Migration identity must canonicalize those aliases; orientation must remain a
separate dimension from Fold main/sub identity. All processes using the AOD APK
need the same policy and migration state.

Run the probe using the same separate-Dex build flow as Fold8BindingProbe, but
use `app_process` under the established system-UID harness:

```sh
adb -s <serial> push <probe-classes.dex> /data/local/tmp/dressroom-screen-profile-probe.dex
adb -s <serial> shell su 1000 -c 'CLASSPATH=/data/local/tmp/dressroom-screen-profile-probe.dex app_process / dev.denver.dressroomadvanced.Fold8ScreenProfileProbe <installed-aod-apk-path>'
```

The probe initializes a detached framework Application for Samsung's feature
constants. It never constructs AODApplication or runs an application onCreate,
and calls no content provider, database or widget-host API. Plain dalvikvm lacks
the Android system-property native methods needed by those constants.
