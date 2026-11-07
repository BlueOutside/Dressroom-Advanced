# Fold8 compatibility adapter

Targets the inspected SM-F971B API 37 / One UI 90000 APK pair:

- Dressroom 3.0.01.20, version code 300120000.
- Active AOD 8.9.57.1, version code 895701000.

This adapter preserves the reference 3.0.10.38 and 8.5 paths. It does not enable arbitrary Dressroom versions.

## Inspected differences

The editor controller is `so.x`, its clock repository use case is `mf.l0`, weather operation is `mf.f0`, and the native toggle coroutine is `so.h` with model `so.j`. Weather support comes from `pc.b.d()` and ClockManager, rather than ClockTypeData. Wallpaper notifications use `o6.e0.b1(EditorData)`. Position reconciliation takes screen before device.

The provider uses `AbsClockView.R0` for font data, `C0` for font ID, `e0(ClockFontData)` for rendering, `b0(ShadowStyle)` for shadow refresh, and static `aod.an1.c0(int, ClockFontData, List)` for height normalization. Its measurement callers differ from the reference provider. Native blur uses combined strategy `aod.jx0`, options `aod.qb0`, and static clip helper `aod.x60.a(Builder, Path)`. The strategy owner remains field b with the clock in owner.o; its d()/e(ViewGroup) methods resolve the output host and screen origin.

The editor retains the `dc.p.Y` save request and `androidx.work.impl.utils.c` callback constructor. Inspection shows discriminator 5 with the save controller in field c. Advanced settings continue to commit after the native callback, not on a generic button press.

## Extended widgets, Now Bar, blur and media adapter

Version 1.0.3 enables the widget toolbar on this editor version. The Fold8 preview is `lp.l` with model F, inner host G and preview root K. Its repositories are `sd.p` (restore), `sd.g` (grid), `sd.n` (metrics) and `sd.l` (placement). Rotation uses `cg.u`; immediate drag binding uses static `u2.a.V`. The native portrait trim is scoped during widget creation so custom orientation survives. The reference-only rotation guide binding is not hooked on Fold8.

Widget host sizing uses static `aod.x89.a`, preserving the existing native and reference `aod.v99` paths. Now Bar stack mappings use `kx4`/`sj0`, animation manager `cy6`, stack-size callback `px4`, keys `ng3.C`, and media wave update `w0`.

The provider blur adapter adds `sp0`, `qb0`, retry `op0`, wallpaper tone via `i16`/`ev2.O`/`q39`, and the Fold8 clock fields. AOD height transitions use `G0` with controller U0. Fullscreen media uses holder `u92`, coordinator `wf4`, expansion callback `a82`, animation update `k8`, and pill bounds `dp1.z`. The existing feature engines retain their behavior; these mappings only select inspected Fold8 contracts. Provider detection is cached outside reflection discovery on draw paths.

## Validation status

The module compiles and all 12 JVM suites pass. The extended `tests/integration/Fold8BindingProbe.java` passes on the physical Fold8 against both installed APKs, covering editor/save, widgets, Now Bar stack, blur, AOD stretch, and media declarations. The probe does not construct Samsung views or save settings.

The user confirmed that clock changes save and survive reopening. Depth appeared only in the editor with 1.0.1; the committed profile contained depthEnabled=true and mask references. After installing 1.0.2 and rebooting, the user confirmed depth works on the lockscreen. The 1.0.3 combined update is installed in place with module data retained. After reboot, fresh logs confirm widget-grid/native-metrics hooks, Now Bar stack repair/rear layers, blur controls/glyph sync, AOD height transitions, fullscreen media source, and native depth clipping install successfully. Live widget, Now Bar and media interaction is awaiting user confirmation. Seven targeted Android suites pass: MediaReturnProbe (65), NowBarPipelineProbe (16), OrientationProfileProbe (33), ClockBlurSyncProbe (47), DepthSafetyProbe (18), RootLayoutWatchProbe (5), and EditorScrollProbe (8). These are contract checks, not visual certification of every Samsung animation. Fold/unfold profile switching and cover-screen support remain outside this adapter, as in the existing module.

Compile the integration probe separately from the module, dex it, then run it on Android with the module dex on CLASSPATH and the editor/provider APK paths as its two arguments. Never package the probe in the module APK. The original provider and version probes remain unchanged.

## Widget-depth background flicker follow-up

The user confirmed Widgets and Extras open, and vertical widget changes apply. Widget backgrounds flickered with widget Subject depth On and became stable with it Off while clock depth remained enabled. That isolates the failure to the widget-depth rendering path.

Version 1.0.4 keeps the existing native blur-submission mask, but moves the content outline clip from the whole widget subtree onto each container's content/dim children. The blur surface keeps Samsung's rounded outline. This Fold8-only path avoids clipping the blur surface twice, and stationary content no longer requests outline updates on every frame. Other provider paths are unchanged.

The new WidgetDepthClipProbe passes seven checks on the Fold8, covering separate content/blur outlines, stationary reuse, movement and restoration when depth is disabled. All eight targeted Android suites pass, as does the extended APK binding probe. Version 1.0.4 is installed; reboot and live flicker verification with widget depth On are pending.

## Vertical free-position draw ordering

The user later narrowed the remaining artefacting to vertical widgets with free positioning, including while the lockscreen is stationary. Version 1.0.5 reorders the root and nested widget-depth pre-draw listeners after the placement listener for that configuration. The old registration order sampled subject/blur geometry before the placement transform was applied in the same frame. Other placement modes retain their callback order.

WidgetPlacementOrderProbe reproduces that stale sample with the pre-fix implementation and passes with the reordered callbacks. It also checks stationary convergence and restoration of the native translation. Nine targeted Android suites (203 checks) and the APK binding probe pass. Version 1.0.5 is installed with saved settings preserved; live stationary-flicker verification after a soft reboot is pending. The earlier unexpected hard reboot remains unconfirmed in cause; the recovered phone reported a staged-install rollback boot reason and was stable during subsequent read-only monitoring with widget depth Off.

## Background restoration and default vertical position

The user confirmed that 1.0.5 stopped flickering, but lower widget backgrounds disappeared. Version 1.0.6 leaves native SemBlurInfo unchanged for Fold8 widget subject depth, applying the subject outline to each content/background surface instead. Media cover clipping retains its existing path. The widget root is not outline-clipped as a whole, and placement still precedes depth geometry sampling. Existing provider adapters keep their native blur-submission behavior.

The user also reported Samsung default positioning placed vertical widgets below the clock. For Fold8 custom vertical grids, requested anchor 0 now resolves to the existing right-of-clock docking placement (anchor 4) in both editor and runtime. Horizontal/default, explicit docking/free anchors, and other providers retain their existing behavior. Saved anchor preferences are not rewritten.

All 12 JVM suites (252 checks), nine targeted Android suites (203 checks), and the APK binding probe pass. Version 1.0.6 is installed in place; visible stable backgrounds and default vertical right-side placement await verification after a soft reboot.

Independent cover/main layouts and different widget lists are not implemented in this port. Samsung's inspected widget store indexes items by profile/display/orientation, but the module's widget preference scope currently uses a main-screen key plus wallpaper/orientation. Complete cover support needs explicit Fold main/sub identity and per-display widget-list integration; it must not be inferred from portrait/landscape profiles.
