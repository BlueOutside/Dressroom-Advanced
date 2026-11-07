# 0.6.6 - fully covered widget blur surfaces

The user confirmed this build working on 2026-10-26.

The 0.6.5 logs verified that the native owner selected type 2/font 803 with depth enabled and clipped both widget blur submissions. The lockscreen capture still showed two blurred boxes over the mountain while their contents were hidden. Sampling the saved subject mask across both captured widget background rectangles showed full coverage.

The remaining case was an empty visible blur path after subject subtraction. Submitting blur info with that empty clip left Samsung's rectangular blur surface visible. Version 0.6.6 submits null blur info for a fully covered background instead. The native original stays retained separately, so geometry changes can restore a fresh partial clip or the original unmasked background without accumulating cutouts.

Java compilation, D8, signed APK v3 verification, and on-phone native path/outline/blur copying checks passed. A new platform check covers full-occlusion removal followed by partial-visibility restoration while preserving the native original.

Installed as versionCode 48. SystemUI was restarted to load the update. Logs reported `Fully covered complication blur surface removed` for both backgrounds. A confirmed awake lockscreen capture showed the mountain cleanly where the boxes had been. A settled Dozing/AOD capture showed the retracted widgets returning where visible, with the overlapping part behind the mountain. The user then confirmed the result.

Evidence in the workspace: `work/widget-depth-065.png` (before), `work/widget-depth-066-awake-fast.png` (Awake), `work/widget-depth-066-aod-settled.png` (Dozing). The confirmed native widget owner and saved manual-stretch restoration remain in place. Imperfect subject extraction and the deferred Dressroom main preview remain separate known limits.
