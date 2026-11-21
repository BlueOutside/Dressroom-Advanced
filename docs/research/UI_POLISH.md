# Lockscreen UI polish on One UI 8.5 and 9.0

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
