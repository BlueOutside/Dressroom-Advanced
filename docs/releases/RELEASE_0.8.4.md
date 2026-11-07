# 0.8.4 alpha - deferred runtime installation

Current installed build: versionCode 62. SystemUI, AOD main and AOD provider processes have completed runtime hook installation. Recovery bypass is removed. Saved preferences were not cleared or rewritten by the update.

## Startup correction

Previously the ClassLoader.loadClass hook called installProvider synchronously while class loading was still on the stack. installProvider takes its own monitor and loads additional provider classes while installing hooks. This creates a plausible class-loader lock inversion during multithreaded startup. A denied Java stack dump means the exact previous blocked stack is not proven.

The discovery callback now only enqueues a class loader through DeferredDiscovery. Reflection and hook installation run on the main Handler after the loading callback returns. Duplicate/reentrant offers for the same loader are suppressed, failed installation can be retried, and bootstrap unhooking happens after deferred installation. The broad depth/shadow drawing hooks are installed only when the provider is available, rather than before provider discovery during SystemUI package startup.

0.8.4 retains the 0.8.0-0.8.2 manual-hold, live crop tracking, restored cropped-preview extraction, bounded geometry caching, direct Region cuts for detailed masks, and single bitmap-mask pass changes. It is not a feature rollback.

## Validation

- Signed APK compiled and installed; v3 signature verified, versionCode 62.
- 66 JVM checks passed: the previous 54 plus 12 discovery checks covering deferred execution, deduplication, recursive offers, retry and concurrent offers.
- The existing 16 native Android geometry checks cover unchanged depth geometry/matrix code.
- SystemUI PID 21032 rendered within the first startup check. Logs reached deferred installation and all provider hooks installed, followed by saved clock effects and independent widget ownership.
- Screen-off reached Dozing; wake returned to rendering. A fresh frame sample during that interval reported median 9 ms, 95th percentile 23 ms and 99th 57 ms. Later mixed activity reported 95th 34 ms and 99th 101 ms. These are observed samples, not a controlled FPS benchmark or a guarantee of no jank.
- Selective AOD process reload (no force-stop) loaded 0.8.4 in main PID 23345 and provider PID 23409; both log successful runtime hook installation.
- The user initially suspected a feature rollback, then clarified it was a small post-update bug. Existing profiles/masks remained present. During native Apply, wallpaper ID changed from 23 to 25 while the bridge still exposed the previous ID's profile; a brief visual/profile mismatch was observed. Do not claim every save lifecycle case is fixed.

Still unresolved/unconfirmed: the cyan rim/raised-looking flower effect (user says it persists with Clock blur Off), detailed live pan/zoom verification, and the fresh manual-Off → depth-On transition without Apply/reopen. Current boot success supports the deferred-install correction but does not establish safety across every restart/device version. Keep the 0.8.3 recovery APK available; do not force-stop AOD together with SystemUI.

SystemUI hooks are needed because that process hosts the lockscreen/full-screen AOD renderer. Restarting the process only reloads module code; it does not itself implement a visual effect.
