# Lockscreen UI polish on One UI 8.5 and 9.0

## Live cover screen-off ANR, 2026-11-22

A reported SystemUI ANR names LiveCoverController's SCREEN_OFF receiver. The
summary shows heavy CPU load but has no main-thread stack, so the exact blocked
operation is unconfirmed. The receiver previously ran the sleep transition
inline, including a full-resolution TextureView.getBitmap readback. That can
wait synchronously for HWUI/GPU work during the doze transition.

Power broadcasts now enqueue a coalesced update and return. Sleep/doze stills
use the existing codec worker: pause records the playback position before the
full-resolution frame is decoded from the local video. The doze freeze path
uses the same asynchronous route. Native artwork can cover the short wait.
Ordinary awake cover transitions and sampling are unchanged.

Decode completions report failure as well as success, clear pending ownership,
and reject results from an old sleep cycle, track or detached host. A failed
sleep capture is not retried on every draw; a later wake/sleep or track change
can try again. No provider-specific or obfuscated binding is introduced.

The signed 2.0.6-screen-off-fix APK (version code 20006014) builds; all 29 JVM
suites, repository/layout checks and the regenerated inventory pass. Compiled
receiver bytecode confirms that onReceive only updates the pending state and
queues the handler task, with no playback or frame capture inline. The reporter's
full ANR main-thread stack and an affected-device sleep/wake check remain needed
to confirm the ANR is resolved; the summary alone cannot establish that.
The supplied LogFox crash-com-android-systemui-22_11-12-58-46_848 export contains
only the same ActivityManager summary plus app/device metadata, with no thread
stacks. Its app metadata reports SystemUI 17 / SDK 37.

## Lyrics timing and layout, 2026-11-22

Inspected Apple Music's `PlayerLyricsViewFragment` through JADX MCP, renamed its
timing-mode binding and event scheduler, and followed their callers. Apple keeps
line/scroll and word events separate; its timing processor uses native TTML data.
The module's LRCLIB fallback normally provides line timestamps, so it cannot
recover Apple's word timing from plain text.

Scroll anticipation now only moves the list. Singing highlights use the playback
clock, and plain LRC lines end at the next source timestamp instead of guessing
overlaps or instrumental breaks from text length. Enhanced LRC still preserves
word times, explicit stops, simultaneous lines and explicitly timed overlaps.
Cached/search results with a substantially different recording duration are
rejected. A monotonically increasing request generation rejects late responses
after rapid track changes, including A -> B -> A.

Player rebinding now replaces attachment listeners instead of accumulating them,
compares media session tokens, and preserves lyrics through temporary metadata
gaps. Polling is bounded even when playback state is absent. Cancelling the close
animation cannot hide a newly reopened overlay. Line drawing uses one path for
focused and unfocused text, disjoint wrapped-line clips and shaped glyph positions
for the sweep. Vertical row spacing is reduced from 30dp to 15dp for lead lines.

The initial attempt to refresh Samsung's default widget position did not correct
the connected phone. Its current profile uses below-clock docking, not default
attachment. Runtime placement measured the full-width outer complication root
(1440px) while the actual inner grid was 959px wide. Docking and free placement
now use the inner grid's transformed bounds and translate the existing outer
owner. No fixed centering offset or reparenting is used. Default attachment still
belongs to Samsung. Shared lyrics fixes apply to all supported adapters; no
obfuscated hook names were added.

The next pass through Apple's `LineSyncedLyricsAdapter`, `KaraokeLyricsAdapter`
and `LyricsScrollCoordinator` confirmed separate line and word renderers. Plain
LRC now uses whole-line highlighting, with sweeps reserved for real word timing.
Scroll compensation is applied immediately in layout callbacks, and initial
positioning runs before the first lyric draw. A temporarily missing playback
state no longer sends the list back to timestamp zero. The in-memory IPC cache
also distinguishes album/duration, matching the repository's recording checks.

Standalone Apple Music retrieval was tried without credentials. Public iTunes
song search succeeded, but both the `lyrics` and `syllable-lyrics` catalog
relationships returned HTTP 401. No Apple provider, embedded credentials or
sign-in flow has been added. An authenticated integration remains separate work.

LRCLIB's current API additionally supplies raw `lyricsfile` YAML and `hasWordSync`.
The client now reads Lyricsfile 1.0 with SnakeYAML's safe constructor, bounded input,
depth and alias expansion, and duplicate-key rejection. Parsing runs in the
provider worker. The normalized timeline, including explicit line/word ends and
overlaps, is carried through cache JSON and IPC; the view prefers it over LRC.
Old cache entries are refreshed once to retrieve that data. Gradle, CLI builds
and both test paths include the same pinned SnakeYAML dependency and its license.
The live LRCLIB response for La Quete parsed as 52 timed lines and 572 words,
and retained its timeline through serialization.

The connected phone also takes the newer clock-page path, while its installed
provider lacks some newer methods. Widget placement now falls back to the visible
clock when page lookup yields no view, instead of abandoning docking altogether.

Signed preview 2.0.6-lyrics-preview builds. All 28 JVM suites pass, including 125
lyrics checks, along with repository and layout checks. Visual confirmation of
lyrics transitions and the widget correction is still needed on the phone.

The user confirmed widget centering after the visible-clock fallback loaded.
The remaining flash occurs in lyric text at a line change, with the cover stable.
A short recording captured normal line advancement. The next renderer pass gives
word-timed rows a stable painted base opacity, with one foreground focus fade,
instead of multiplying a whole-TextView fade by another painted opacity ramp.
It also retains the outgoing line's last displayed fill instead of forcing a
completed white mask at its end timestamp. Line-only rows keep their native
TextView fade. This opacity change still needs visual confirmation.

The user reports that the text flash persists on the display and live scrcpy,
but is absent from a scrcpy recording. Further JADX inspection found that
`CustomTextView.setSmoothScalingPaintFlags(true)` enables `LINEAR_TEXT_FLAG` and
`SUBPIXEL_TEXT_FLAG`; the karaoke adapter calls it while creating scaled word
views. Module lyric rows now configure those flags before layout as well. This
is a rendering change, not a timing adjustment, and still needs visual checking.

The paint flags did not resolve the reported flash. The user narrowed it to the
moment automatic movement toward the next line starts. The old ScrollView path
immediately changed its scroll offset to the final target and compensated every
row with a translation. It now scrolls the viewport continuously using uptime,
with the stagger expressed as a small relative row offset. Retargeting preserves
both the current viewport and row offsets; grabbing the list cancels automatic
scrolling and settles the offsets. Initial positioning still snaps before draw.
The pure motion contracts cover no initial jump, continuous row movement,
retargeting, stagger completion and integer-scroll rounding. Lyrics contracts
pass 438 checks. Visual confirmation of this scroll-path change remains pending.

Continuous scrolling did not stop the flash. A live diagnostic disabling only
the viewport edge mask did: the user confirmed that lyric transitions stopped
flashing with all text highlighting, scaling and scrolling still enabled.
The production renderer now applies the same edge falloff with Canvas.clipShader
instead of a saveLayer followed by DST_IN draws. The gradient is rebuilt only
when the viewport changes size, and the clip is restored after child drawing.
This API is available before the module's minimum API 36. Temporary live probes
are removed. Visual verification with the replacement fade is pending.
Signed preview versionCode 20006005 builds and verifies, passes repository and
layout checks, and is installed on the connected phone with SystemUI reloaded.
The temporary debug.dr.lyrics property has been reset to zero.
The user confirmed that flashing is fully gone with the replacement fade.

## Lyrics focus and screen pin, 2026-11-22

Revisited Apple's line focus animator and its callers through JADX. Renamed
the eligibility wrapper to `refreshLineFocusIfEligible` and the system animation
setting check to `areSystemAnimationsDisabled`. Its active-line fade is separate
from scrolling; the module likewise retains vocal timing while browsing.

Inactive rows now gain progressive distance blur up to 4dp, easing between cached
quarter-dp RenderEffects. Blur layers are limited to rows near the viewport.
Browsing reduces the blur and raises inactive text to 50% opacity, while the
current line keeps its brighter, sharper treatment and slightly larger scale.
The edge fade continues using clipShader, preserving the confirmed flash fix.

The compact header has matching vector Pin and Close controls with 44dp touch
targets. Pin toggles View.keepScreenOn only while the lyrics page is open and
visible. Closing, hiding or detaching the page releases it, including the
controller's immediate-hide path. Pin selection is kept for reopening the page
during the same session; it does not prevent an explicit power-button sleep.
Visual checks of blur, browse focus and the screen timeout are pending.
Signed preview versionCode 20006006 passes the build, signature verification,
repository/layout checks and diff whitespace checks. Installed on the connected
phone and reloaded SystemUI for the visual checks.

## Lyrics backdrop handoff, 2026-11-22

Fullscreen artwork previously switched its entire draw path using the lyrics
visibility boolean. Clearing that flag on Close exposed the sharp player cover
while the lyrics overlay was still fading. Live artwork also added or removed
its full-radius blur in one step.

The 8.5 and 9.x fullscreen paths now share a reversible backdrop transition:
300ms opening, 210ms closing. The sharp cover stays underneath while a dedicated
RenderNode fades the existing blurred cover over it. That fade has its own node
so the controls-band recording cannot overwrite it during the same frame.
Tint and controls blur use the same mix. Live video ramps through cached blur
strengths, and newly attached video hosts inherit the current mix. Transition
ticks invalidate the surfaces without unnecessarily rerecording static artwork.
Reopening during Close retains the local backdrop's current opacity instead of
resetting the page to transparent. Visual verification is pending.
Signed preview versionCode 20006007 builds and verifies. All 28 JVM suites pass,
including 438 lyric checks, plus repository/layout and diff whitespace checks.

## Original DIY Outer Glow, 2026-11-22

JADX AOD 8.9.66.1: renamed the shader base/controller to `AgslShaderView` and
`AgslShaderController`, plus size/parameter updates and animation start/stop/frame
methods. Xrefs show the native shape-animation callback calling Outer Glow's
parameter update each frame. That update calls setLayoutParams and logs its full
configuration. The shader base also recreates the RuntimeShader RenderEffect,
blur effect and their chain inside onDraw on dirty frames.

`DiyOuterGlowShader` loads the installed AOD/Clock Style provider's named
`agsl_now_bar_roundrect` raw resource, without shipping extracted Samsung code or
using obfuscated class/field names. It draws the unchanged first-pass shader with
a Paint and attaches the equivalent 7.5px Gaussian blur once, matching the native
OuterGlowView's .015 * 500 override. Size uniforms retain the native thickness,
corner radius, segment calculation and tail-length settings. The palette follows
the two Studio color choices; the opposite trail reverses their order.

Geometry and colors update only when changed; animation ticks update iTime.
Runtime and Studio share this renderer. Hidden windows/ancestors stop its frame
loop. Providers without the original resource retain the earlier fallback, and
an incompatible pipeline is reported rather than evaluated with guessed passes.
The source has two enabled passes; its exported blur radius differs from the
native runtime override and is intentionally not used.

Signed 2.0.6-glow-preview versionCode 20006008 builds and verifies. All 28 JVM
suites pass (including 145 DIY checks), plus repository/layout checks. Original
appearance and interaction smoothness still require visual confirmation.

The user reports that the trail shape/softness and possibly motion still differ
from the older effect; colors are correct. Do not treat this preview as visually
approved. The installed AOD's raw shader JSON matches the reference byte for byte.
A read-only app_process probe resolved raw/agsl_now_bar_roundrect to 0x7d120001,
compiled the native shader and successfully created the installed module's helper.
Offscreen GPU frames at identical settings compare Paint+blur with Samsung's
RuntimeShader RenderEffect chain: mean absolute RGBA differences are approximately
0.035/0.022/0.004/0.003 of 255. The draw path itself therefore appears equivalent
for that fixed frame. Reference frames also show the large visual difference
between the .015 runtime blur override (7.5px) and exported .09 blur (45px).
Those probes and PNGs live under .build/outer-glow-reference, outside Git.
After comparing the rendered references, the user selected the sharper travelling
trails (7.5px runtime preset) and confirmed that expanding, collapsing and swiping
are now smooth. Keep that preset in versionCode 20006008; no further visual change
was made after the comparison. The static native shader source remains unchanged.

## Native effect selection and Studio previews, 2026-11-22

AI Border Travel and Guiding Light already had runtime effect IDs 5 and 6. The
former was labelled AI Mesh Warp (MVC), while Studio displayed custom AGSL
approximations for both. Those IDs and existing selections are retained. The
labels now identify Border Travel directly, with short visual descriptions.

Studio hosts the installed provider's AIPresenceView/BORDER_TRAVEL or native
GuidingLightEffect rather than those approximations. It loads classes through
the installed provider's package context and reuses the structural bindings,
adding no obfuscated class-name lookup. The NUDGE preset initializer is shared
with runtime. Palette and size updates are cached, start/stop follows visibility,
and detach releases the native effect. Unsupported previews are identified instead
of displaying an imitation. Native binding probes resolve both contracts on the
connected reference provider. All 28 JVM suites passed after updating the existing
effect-label expectation; repository/layout checks and diff whitespace checks pass.

Studio visibly renders AI Border Travel. Guiding Light initially instantiated and
started but produced no light. Two preview-specific requirements were identified:
its native renderer decodes the light-map texture through the host view's Context,
so that host must use the provider's resources; and an empty display list can be
culled before a RuntimeShader produces output. The dedicated provider-context
surface records an explicit transparent draw. The configuration is obtained from
the same native pill accessor used at runtime, rather than the generic config
constructor. The user confirms Guiding Light already works on the lockscreen;
runtime behavior is retained. The signed preview build (version code 20006012)
was installed, and Studio now visibly renders Guiding Light with its native
subtle edge lighting. The user confirmed the preview is fixed. No test-card
changes were saved during verification.

JADX aliases include GuidingLightEffect, GuidingLightRenderEffect and
VibeRenderEffectBase for the inspected native pipeline. An app_process View probe
failed because ApplicationSharedMemory was not initialized; offscreen RenderNode
checks still run in that harness and confirmed the empty-display-list behavior.

## Cover loading, 2026-11-21

`MediaArtworkUncap` waited up to 3.5 seconds for each image download or catalog lookup inside Samsung's URI/result loading hooks. Those hooks now return cached or native artwork immediately. Downloads run separately, and a successful upgrade asks the native `onNotificationAdded(String, StatusBarNotification)` callback to reload the latest matching media notification. Requests are coalesced, rechecked against the current session track/URI, and discarded after notification removal. The named callback signatures were checked in the saved 8.0, 8.5 and 9.0 SystemUI APKs. The saved 9.5 source moves notification entry into `MediaCarouselInteractor`; that and the alternative `LegacyMediaDataManagerImpl` are supported as well.

Static and live cover workers now retain only the newest queued request while deduplicating running requests. Skipped pending tracks complete without starting their downloads. Live lookups share an album key, prefetch the current track while the fullscreen player is collapsed (respecting the live-art, Wi-Fi and power settings), and keep a prepared video running for another track on that same album. A cold live-cover lookup starts fading away the previous cover after 450 ms instead of holding it until the network search finishes. Download resolution and file-size limits are unchanged. On-device song-switch and native artwork-refresh behavior still need confirmation.

The signed `dressroom-advanced-2.0.0-cover-loading-preview.apk` builds. Live-cover contracts pass 49 checks, including pending-request replacement, deduplication, failure and retry; media contracts pass 109 checks. Installed on the connected S26 Ultra and reloaded SystemUI. The fresh process reports asynchronous artwork refresh and the native media source binding installed, with no related error in the targeted startup messages. Visual song-switch confirmation remains pending.

## Live widget blur in Dressroom

Dressroom 2.9.60.50, 2.9.60.51 and 3.0.10.38 all have
`preview_single_view_root` and a `WallpaperPreviewContainer` inside it.
The full-size `blending_effect_view` is a separate sibling of the editor
preview container. `EditorBlurCapture` currently caches one crop view for
the whole decor root and clips to that view's bounds. It has no association
between an individual widget surface and its particular preview frame.

Samsung's canvas blur records the window when the blur view draws. Moving a
widget with translation can reuse its display list, and the editor currently
resubmits blur on size changes. The existing movement watcher is attached for
depth or media clipping, so a plain blurred widget has no equivalent refresh.
Find the wallpaper within the target's preview frame, intersect the capture
with that frame, and invalidate the canvas blur when the widget or wallpaper
transform changes. The editor hook now does this for image previews and keeps
Samsung's rounded widget clip. Surface-backed wallpapers need a separate
source contract.

Widgets and Now Bar surfaces using window blur fade tint changes on their
existing drawable. Samsung's `BackgroundBlurDrawable.setColor(int)` lets the
fade reuse the current radius, clip and opacity.

## Artwork resolution and framing

The expanded media worker receives Samsung's artwork Icon, stores the source
bitmap, and can bind a separate square crop to the framed player. The 8.5
fullscreen code reads the ImageView drawable. The 9.0 code already prefers
the source field when bound. The 9.0 SystemUI media loader can also limit URI
decodes using its artwork width and height before AOD receives the Icon.

| Provider | Worker | Source field |
| --- | --- | --- |
| AOD 8.9.32.95 | QR.run | YR.w0 |
| AOD 8.9.36.23 | XR.run | fS.w0 |
| Clock Style 8.9.35.60 | MK.run | TK.n0 |
| AOD 8.9.66.1 | u92.run | fa2.G0 |

The 8.5 fullscreen layer now prefers the native source bitmap and keeps the
existing drawable as a fallback. The binding looks for a bitmap write on the
holder from the worker that loads the artwork Icon and scales the framed
drawable. If upstream art is still small, a larger decode should be scoped
to the active fullscreen track and available media URI. It should keep the
current cover visible while decoding, discard results from old tracks and
cap the decode to the viewport. A Fit option with a soft backdrop would also
avoid severe crops of square sleeves on tall phones.

JADX aliases added on AOD 8.9.66.1 are `fa2.G0 → sourceArtworkBitmap`,
`fa2.C() → getArtworkTargetSize`, and `j92.g() → getArtworkIcon`.

## Clock handoff and fade

The visible clock, seconds view, AOD container, measurement instance and
glyph-shaped blur have different owners. On full-screen wallpaper AOD,
Samsung can already reuse the lockscreen clock for the ambient state.
Theme clocks and black AOD can have separate containers. See
[Clock layer handoffs](CLOCK_LAYER_HANDOFFS.md).

The native page transition now hands off the shared blur host independently
of the seconds-specific fade. The binding matches the page's calls to both
transition methods, which read the same source and target and attach or
remove them through the holder. The AOD and Clock Style APK samples from
8.5 and 9.0 share that method shape. The hook runs on AOD builds where the
native blur owner is mapped. On the 9.0 Fold build that fills the target
after configuration, the seconds return picks it up at attachment.

Samsung still uses both views for geometry matching and the seconds layout.
Doing the same handoff for the actual glyphs needs a check of the native fade
and the paths that skip the animation. Verify quick reversal, AOD wake and
sleep, and the return from seconds on-device.

Use Samsung's doze fraction to fade secondary elements on wallpaper AOD.
Start with widget glass and contents as a pair, then date/weather or
shortcuts when Samsung already hides those elements. Preserve native
eligibility and fade endpoints, including the no-wallpaper and cover-display
paths. Test panel scroll and focus in portrait and landscape alongside these
changes.

When depth differs between AOD and the lockscreen, limit any feather or
motion blur to the clock pixels crossing the subject edge. Drive the effect
from the native progress so it can reverse cleanly and finish with the normal,
sharp mask. The live glyphs and Samsung's clock blur need the same handoff
before adding that effect.
