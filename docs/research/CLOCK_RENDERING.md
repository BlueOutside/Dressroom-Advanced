# Clock rendering and shader optimization

Inspected the reference One UI 9 AOD 8.9.66.1 through JADX MCP and a local DEX
reference scan. These findings concern the normal stretchable digital clock.
They do not describe every motion-clock style or the vendor compositor internals.

## Rendering path

`ClockNonePaddingTextView.onDraw` draws a shadow pass and a foreground pass when
its paint has a shader. `NonePaddingTextView.onDraw` generates a glyph path with
`Paint.getTextPath` when blur-path mode is enabled.

`getPathForBlur` returns an offset copy of the stored glyph path, or constructs a
path from the current text paint. The combined blur strategy copies and transforms
the visible glyph paths, merges weather geometry, builds `SemBlurInfo`, and calls
`semSetBlurInfo` on the native blur host. The clock's background blur is submitted
through that Samsung API, rather than loaded from an editable AGSL asset here.

The APK also contains AGSL weather assets, SESL effects, and Now Bar media
lighting/masking shaders. Motion-clock styles have separate GLSL assets. A shader
being present in the APK does not establish that the current clock uses it.

## Existing optimizations

`GlyphDrawReuse` already reuses identical outlines within one native draw call.
Its memo is reset before and after the draw. `GlyphFontCache` skips identical
native typeface requests. Changing stretch axes still requires fresh geometry.

## Candidates to measure

- Retain an exact outline cache across unchanged frames, with explicit font and
  configuration invalidation. Do not reuse outlines after text/metric/axis changes.
- Combine path scaling and translation to avoid intermediate paths and matrices.
- Check duplicate blur submissions and native blur-host coverage with a frame
  trace before attempting to reduce compositor work.

No CPU/GPU timings were collected during this inspection. The allocation and
copying sites are confirmed; their share of animation cost is not. Do not cache
blur submissions across media/doze transitions without proving all invalidation
paths, since stale compact-clock blur previously caused visible artifacts.

## Framework continuation

The connected S26 Ultra's `framework.jar` contains the rest of the Java blur
implementation in `View`. `SemBlurInfo` selects window (0), captured bitmap (1),
or canvas (2) blur. These paths must not be treated as interchangeable.

- Window blur updates `BackgroundBlurDrawable` and its compositor region data.
  Radius/color-curve setters avoid some unchanged updates, but clip-rectangle
  and clip-path setters notify the aggregator unconditionally.
- Canvas blur records a background `Picture`, discards/re-records a render node,
  and applies a filter before clipping the output. Expanded capture and optional
  downsampling have different edge behavior. A smaller capture needs enough
  background padding for the blur kernel.
- On the newer image-filter backend, `RenderEffectImageFilter` uses
  `ShaderAssembler`. This is where framework AGSL color/effect code is assembled;
  it is not an AOD shader asset. The ordinary `BlurFilter` itself uses
  `RenderEffect.createBlurEffect`, with the supplied radius divided by 2.25.
- The assembler already caches its render effect and checks whether size or
  parameters changed. A call to `build()` does not establish recompilation on
  every frame. Shader rewriting needs evidence beyond those call sites.
- `SemBlurInfo.scaleDownRadius()` mutates its stored radius. Reusing or resubmitting
  that object under canvas downsampling needs care to avoid repeated scaling.

This establishes additional optimization and visual-consistency candidates, not
a measured diagnosis of GPU cost. Window blur's lower renderer implementation is
beyond these Java classes. No framework or rendering hooks were changed here.
