# 0.17.2-alpha: remove experimental device blur forcing

Remove Force Now Bar blur and Force live widget blur, their hook installation and the unfinished captured-blur backend. Ignore their old saved flags. This restores native background selection after the relevant processes reload. Extras, charging retention, custom text and the established clock/media/widget features remain in place.

The unsuccessful experiment, framework test results and last source revision are preserved in [the research record](../research/FORCED_BLUR_EXPERIMENT.md). It did not establish working blur on unsupported hardware.
