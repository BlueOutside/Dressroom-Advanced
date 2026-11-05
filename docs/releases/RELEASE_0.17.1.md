# 0.17.1-alpha: captured Now Bar blur fallback

The P613 ROM accepts Samsung window-blur requests without rendering the blur. Forcing eligibility alone consequently removed the pill background while choosing dark foreground text.

On the inspected 8.5 provider, force-blur now waits for a wallpaper-only capture when Android reports window blur unavailable. It submits a separate captured-mode Samsung blur info with the native tint, color curve, corners and clip. The capture runs on a worker, is downscaled, and is shared between cards; unchanged crops are reused. The original shared native info is not modified. Capture security policy remains enabled. Native window-blur devices and the reference 9.0 renderer retain their existing path.

This fallback is captured blur, not a live video-wallpaper renderer. The cache refreshes on native rendering requests after its minimum age; it does not continuously capture frames. Widget blur is unchanged in this build. Visual confirmation on the tablet is required.
