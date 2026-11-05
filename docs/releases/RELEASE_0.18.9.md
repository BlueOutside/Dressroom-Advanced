# Artwork foreground exclusion - 0.18.9 alpha

Version code 126. Signed build, 152 JVM checks and 434 Android checks pass. Artwork-only follow-up; the clock source/animation code confirmed working in 0.18.8 is unchanged.

The 0.18.8 native card-parent copy still went behind the pill. The bridge returns to the root foreground overlay and retains the complete moving artwork. Its drawing is clipped to the native compact-card region with the current controls-host rectangle explicitly excluded while controls remain visible. The background artwork beneath the controls remains; the foreground bridge cannot paint over them. The bridge renders the same artwork blur as the main layer, rather than painting a raw image strip.

The native moving thumbnail is held transparent through the existing alpha-write guard throughout collapse and released after native cleanup. This preserves geometry/reparenting and restores the latest native alpha instead of a stale snapshot. The live clock blur drawable is untouched.

The Android render case paints controls first and then draws the actual foreground return view above them. It checks that artwork crosses above the pill, retains its complete width, leaves controls pixels intact, stays inside the pill region, and stops excluding controls after they disappear. The user reports the artwork problems remain on this candidate. A subsequent recording captures the actual close; the first short recording ended before the gesture. Do not treat the helper tests as a visual fix.
