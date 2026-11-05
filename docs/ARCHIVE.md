# Historical artifacts

This document describes the maintainer's local workspace archive, not files shipped with a Git clone. In that workspace it is at `../../../archive/2026-10-30/` relative to this document's directory, outside the active project. The completed ZIP contains 30,039 verified files and is approximately 953.9 MiB.

| Archive item | Original contents |
| --- | --- |
| `research-work.zip` | Entire historical workspace `work/` tree, retaining that prefix inside the ZIP |
| `research-manifest.json` | Per-file original length and SHA-256, verified against decompressed ZIP content before loose copies were removed |
| `moves.json` | Exact old/new paths for reorganized docs, tests, APKs and earlier deliverables |
| `legacy-project-build/` | The project's old `.build/`, including its older signing key |
| `early-deliverables/` | Loose early APK/source ZIP/research exports formerly under workspace `outputs/` |
| `java-crashes/` | Historical headless-probe JVM crash logs |

Old notes often refer to `work/...`. Those are now ZIP entry names, not active checkout paths. Current regression evidence is copied into `docs/validation/`; current native glyph fixtures are maintained in `tests/native-glyph/`.

The archive contains private signing-key backups, screenshots and device logs. It is local research/recovery material, not a source distribution. Keep the active `.signing/` directory private as well.

Historical APKs are grouped under `artifacts/releases/`. Some are failed or diagnostic builds; use the release/validation history when choosing a rollback. The installed 0.14.0 baseline and newly built cleanup APK are in `artifacts/current/`. `artifacts/index.csv` inventories all 106 preserved APKs with checksums and baseline/cleanup/history labels.
