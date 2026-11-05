# 0.18.3-alpha: orientation follow-up and widget depth scope

The user confirmed both 0.18.2 layouts work in Clock Style after Apply from landscape, but portrait required manually enabling depth and saving from portrait was unreliable. Recheck native ScreenSize at Apply and after configuration changes, including when Samsung reuses the clock instead of recreating it. Keep date identity tied to the preview orientation and clear old preview effects when switching layouts. Widget Reset retains its target orientation.

Refine legacy migration: an unconfigured second orientation inherits the original preferences and source-space subject, with the baked screen mask removed. Automatic editor detection can reuse that subject and generate the new crop. Existing configured orientation profiles are untouched. This preserves depth being enabled without copying landscape geometry into portrait.

Widgets gain a Depth applies to choice: Separate per orientation, Portrait only, Landscape only, or Both orientations. Follow clock / On / Off remains the behavior choice. Off with a shared scope disables widget depth in both. A wallpaper-wide policy shares only those depth choices; each layout retains its own mask, size and placement. Draft policy changes follow tab/layout switches and join the native Apply transaction.

The signed build passes 117 JVM checks and 14 Android suites (394 checks), including 33 orientation/migration/policy checks. The original 0.18.2 native binding validation still covers the same native methods. Installed on the P613 with scopes reloaded. Portrait-first Apply/reopen and new policy visuals require user validation; other devices and foldable display transitions remain unverified.
