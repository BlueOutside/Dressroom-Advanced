"""Join offline DEX results with APK identities and the module's literal hook inventory."""

import argparse
import csv
import json
import re
from pathlib import Path


def write_csv(path, header, rows):
    with path.open("w", newline="", encoding="utf-8-sig") as output:
        writer = csv.writer(output)
        writer.writerow(header)
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("scan_dir", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    rules = {}
    for line in Path(__file__).with_name("rules.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        role, scopes, kind, owner, method, proto, evidence = line.split("\t")
        if role in rules:
            raise ValueError(f"Duplicate role: {role}")
        rules[role] = (scopes, kind, owner, method, proto, evidence)

    scopes = {
        "com.android.systemui": "systemui",
        "com.samsung.android.app.dressroom": "editor",
        "com.samsung.android.app.aodservice": "provider",
        "com.samsung.android.app.clockpack": "provider",
        "com.samsung.android.wallpaper.live": "wallpaper",
    }
    manifest = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    manifest = [apk for apk in manifest if apk["package"] in scopes]
    unique = {}
    for apk in manifest:
        apk_id = apk["sha256"][:12].lower()
        unique.setdefault(apk_id, apk)
        if unique[apk_id]["sha256"].lower() != apk["sha256"].lower():
            raise ValueError(f"SHA-256 prefix collision: {apk_id}")

    args.output.mkdir(parents=True, exist_ok=True)
    signatures = []
    for apk_id, apk in unique.items():
        expected = {role for role, rule in rules.items() if scopes[apk["package"]] in rule[0].split(",")}
        seen = set()
        for line in (args.scan_dir / f"{apk_id}.tsv").read_text(encoding="utf-8").splitlines():
            role, count, hits = line.split("\t", 2)
            if role not in expected or role in seen:
                raise ValueError(f"Unexpected or repeated result for {apk_id}: {role}")
            seen.add(role)
            status = "no match" if count == "0" else "unique match" if count == "1" else "multiple matches"
            signatures.append((apk_id, apk["package"], apk["version"], role, status, count,
                               *rules[role][1:], hits))
        if seen != expected:
            raise ValueError(f"Missing results for {apk_id}: {sorted(expected - seen)}")

    write_csv(args.output / "apks.csv", ("id", "package", "version", "version code", "sha256", "source file"),
              ((apk["sha256"][:12].lower(), apk["package"], apk["version"], apk["code"],
                apk["sha256"], apk["path"]) for apk in manifest))
    write_csv(args.output / "signatures.csv",
              ("id", "package", "version", "role", "status", "count",
               "kind", "owner filter", "method filter", "prototype filter", "evidence filter", "members"),
              signatures)
    hooks = []
    for file in sorted((repo / "app/src/main/java").rglob("*.java")):
        source = file.read_text(encoding="utf-8-sig")
        for match in re.finditer(r'\.setId\(\s*"([^"\n]+)"', source):
            hooks.append((file.relative_to(repo).as_posix(),
                          source.count("\n", 0, match.start()) + 1, match.group(1)))
    write_csv(args.output / "module-hooks.csv", ("module source", "line", "literal hook ID"), hooks)
    print(f"{len(unique)} APKs, {len(rules)} rules, {len(signatures)} applicable checks, {len(hooks)} hook ID sites")


if __name__ == "__main__":
    main()
