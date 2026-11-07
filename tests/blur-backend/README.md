# Manual blur backend comparison

This diagnostic Activity displays a checkerboard in its Activity window and a transparent child window with four columns: plain, Samsung window blur, Samsung canvas blur, and an Android RenderEffect positive control. Instrumentation argument `sameCanvas=true` adds a checker inside the canvas-blur column. Without that argument the canvas column must sample the separate underlying window. Comparing both configurations distinguishes local canvas blur from cross-window blur.

Build the Activity and manifest with the project SDK into `.build/tests/blur-activity/` using javac, d8 and aapt, then sign with a local test key. This is a separate test APK, never part of the module. Install `probe.apk` and run `am instrument -w --no-hidden-api-checks dev.denver.blurprobe/.BlurActivity\$Runner` through an explicitly selected ADB device. Escape the dollar sign for the remote shell. Add `-e sameCanvas true` before the component for the local-canvas configuration. The hidden-API option applies only to this instrumentation run.

The panel appears after attachment and logs its coordinates under DressroomBlurProbe. Capture it while visible; it closes after 35 seconds and instrumentation finishes after 38 seconds. Remove `dev.denver.blurprobe` afterward. It does not change wallpaper, module preferences, accessibility settings or global blur flags. The screen may wake and the Activity temporarily covers the lockscreen.

On the P613 port on 2026-11-02, local canvas blur and RenderEffect rendered. Neither window blur nor canvas blur sampled the separate checker window. This does not certify another device or ROM.
