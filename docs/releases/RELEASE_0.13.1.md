# Media return and render hot paths - 0.13.1 alpha

Installed versionCode 94, signed with the existing local module key. Includes 0.13.0 (versionCode 93).

## Native return animation (0.13.0)

The awake full-player collapse now retains Samsung's HEGT spring instead of immediately restoring the final manual font. Pending restoration waits for the native animator to finish, releases its listener afterward, and is cancelled when media expands again. Keep-stretch still holds actual doze/wake transitions; the decision reads the clock's real `t0` doze amount, not the K0 argument which also includes media progress. The user confirmed that the return animation works, but reported lag.

The artwork blur RenderNode is no longer re-recorded solely because the media controls' opacity or position changed. Artwork/cover changes still invalidate its content. Clock-mask coordinate conversion shares the root inverse matrix within each rebuild.

## Quality-preserving CPU work reduction (0.13.1)

- Reflection resolves fields and method overloads once per target class and argument-type signature. ClassValue owns the cache; overload ambiguity, null/primitive matching, inherited access and wrapped native exceptions retain their existing behavior. Cached objects are members, not target instances or field values.
- Depth subtraction rejects disjoint ordinary paths using conservative bounds before approximation, cache scans and path operations. It returns a separate copy, preserves curves exactly, and bypasses the shortcut for inverse-filled paths.
- Mask precision, contour simplification tolerances, blur radius, animation duration and frame count are unchanged.

Android's hardware renderer was already active. The simpleperf sample contains the Vulkan render pipeline as well as CPU path intersections, PathMeasure and reflection work. This release avoids redundant preparation; it does not claim to have moved native font morphing into a new GPU shader.

## Validation

Build and v3 signing passed. Real Android probes passed 77 checks: RenderHotPathProbe 22, ReflectContractTest 6, DepthRegionProbe 16, MediaReturnProbe 17 and NowBarPipelineProbe 16. Geometry cases include rounded curves, disjoint masks, inverse fills, narrow stems, holes and caller mutation isolation.

One warmed, same-process on-phone microbenchmark measured 5,000 inherited method calls at 3.7175 ms with an uncached lookup versus 1.50776 ms cached. This is a specific CPU microbenchmark, not animation FPS or an end-to-end speedup claim. The older simpleperf recording has 1,119 samples and incomplete call chains; it does not isolate the entire collapse animation.

Installed the APK, reloaded SystemUI once and confirmed build 0.13.1 runtime widget hooks. Captured the retained stretched/depth lockscreen and expanded artwork with readable native controls. Logs confirm manual heights restored after media return. The user subsequently confirmed substantially smoother animation; no comparative transition benchmark is claimed. A separate AOD process was not explicitly restarted, so independent AOD-process reload coverage is not asserted.

Temporary USB keep-awake restored to its original value 0. No wallpaper edits were applied or settings changed for testing. The 0.12.10 and 0.13.0 APKs remain available as fallbacks.
