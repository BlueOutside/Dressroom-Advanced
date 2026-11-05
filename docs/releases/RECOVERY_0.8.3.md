Current installed build is 0.8.4 with deferred runtime installation; SystemUI/AOD hooks are active. See RELEASE_0.8.4.md. Keep this recovery APK available.

# Historical recovery: 0.8.3 recovery alpha (versionCode 61)

The user confirmed SystemUI is back after installing this build. Runtime hooks in SystemUI/AOD are bypassed entirely in DressroomModule.onPackageReady. Dressroom editor hooks remain enabled. Shared preferences, masks and native settings were not cleared. Runtime custom effects, including depth, are temporarily inactive.

Do not reinstall 0.8.2 or restore runtime hooks as a routine next step. It caused SystemUI startup to stall on this session; startup/root cause must be investigated separately before another phone trial. Downgrading to 0.8.1 and restarting SystemUI did not recover it. AOD was un-stopped and its native UfClockProvider was explicitly started, also without resolving that SystemUI stall. Bypassing this module's runtime hook installation restored native startup: PID 16688 rendered 421 frames in the first observed sample; user confirmed the phone interface returned.

SystemUI never reached runtime clock-provider installation during the failed startups; the module logged “Waiting for runtime clock provider” and dumpsys gfxinfo could not dump its main process. Java stack capture through debuggerd/SIGQUIT was denied by the device. No successful stack trace was obtained; the exact deadlock/blocking cause is unproven. Do not attribute it solely to mask path subtraction. The ClassLoader/provider discovery and startup hook ordering need inspection.

Experimental 0.8.2 sources/APK/docs are preserved in work/source-experimental-082. Earlier source baseline is work/source-before-live-mask-078. There is no git repository.

Work done before the emergency:

- 0.8.0 geometry caching improved AOD, user confirmed noticeably smoother but still some lag.
- Manual hold is established synchronously, keeps exact current heights and clears renderer/staged hold when depth-owned mode is released.
- Full-drawable detection regressed to one leaf. 0.8.1 restores cropped-preview detection and maps the result back into image coordinates for live crop movement.
- 0.8.2 direct Region cuts benchmark at about 1.6 ms vs the earlier 37 ms first-cut operation, with 16 Android geometry checks passing. This microbenchmark did not establish startup safety.
- Duplicate nested bitmap mask passes were removed in experimental 0.8.2; the user's cyan rim/raised-looking flower effect remains unresolved. User states it is caused by depth and remains with Clock blur Off. Matched screenshots are work/depth-rim-on.png and work/depth-rim-off.png. Do not claim it was fixed or simply dismiss it as wallpaper.
- Live pan/zoom and fresh manual-Off → depth-On still need direct verification. Do not mark the full feature task complete.

Recovery restart procedure: avoid force-stopping AOD alongside SystemUI. The packages share a UID on this phone. The AOD stopped flag was restored to false. No phone reboot was performed. No settings database was edited or cleared.
