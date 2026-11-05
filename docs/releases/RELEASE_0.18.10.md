# Collapse blur and inherited opacity - 0.18.10 alpha

Version code 127. Signed build, 152 JVM checks and 438 Android checks pass. The user-confirmed 0.18.8 clock source fix is unchanged. The user confirms artwork no longer drops behind the pill. They identified the remaining flash as early shortcut reveal and compact title overlap during return; those are handled separately.

A 0.18.9 capture contains the physical close roughly 28.7 seconds after recording started. Closing frames were extracted on the device using MediaMetadataRetriever and saved only as ignored local evidence. The first 30-second recording ended before the user's gesture and is not closing evidence.

The foreground exclusion previously looked at only each controls view's own alpha. The expanded-content ancestor can already be transparent while those values remain 1, leaving the foreground copy cut out over the pill until native cleanup. Visibility now uses cumulative ancestor alpha, with a 1% visibility threshold, so invisible controls cannot keep excluding that area. The Android view test covers an opaque parent, a fully faded parent, combined parent/child opacity, and restoration.

The controls blur band was recalculated every collapsing frame against the native controls' window position, even though the cover was shrinking in a different space. It now keeps its last measured cover-relative band through collapse. A valid measured band is required before drawing it; blur still uses the same RenderEffect radius and resolution. The band and foreground exclusion remain separate: the former follows artwork; the latter follows currently visible controls.

The complete foreground cover, native moving-thumbnail alpha hold and latest-alpha restoration from 0.18.9 remain. No clock animation, stretching, settings or depth changes are included.
