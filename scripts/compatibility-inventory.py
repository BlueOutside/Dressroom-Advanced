"""List literal native lookups for APK comparison. This is not a hook resolver."""

import argparse
import json
import re
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / "app/src/main/java"
OUTPUT = PROJECT / "docs/compatibility/reflection-inventory.json"
PATTERNS = {
    "class": re.compile(r'Class\.forName\(\s*"([^"\n]+)"'),
    "declared-method": re.compile(r'getDeclaredMethod\(\s*"([^"\n]+)"'),
    "public-method": re.compile(r'(?<!Declared)getMethod\(\s*"([^"\n]+)"'),
    "declared-field": re.compile(r'getDeclaredField\(\s*"([^"\n]+)"'),
    "resource": re.compile(r'getIdentifier\(\s*"([^"\n]+)"'),
}


def inventory():
    entries = []
    files = sorted(SOURCE.rglob("*.java"))
    for path in files:
        source = path.read_text(encoding="utf-8-sig")
        for kind, pattern in PATTERNS.items():
            for match in pattern.finditer(source):
                entries.append({
                    "file": path.relative_to(PROJECT).as_posix(),
                    "line": source.count("\n", 0, match.start()) + 1,
                    "kind": kind,
                    "target": match.group(1),
                })
    entries.sort(key=lambda entry: (entry["file"], entry["line"], entry["kind"]))
    return {
        "scope": "Static literal lookups only. No APK or runtime capability verification.",
        "omissions": [
            "Names held in constants, arrays, concatenations or variables",
            "Reflect.call/field/set object graphs and implicit inherited members",
            "Constructors, method signatures, enum values and behavioral contracts",
            "Resource names matched by traversal rather than getIdentifier literals",
        ],
        "sourceFiles": len(files),
        "entries": entries,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if the committed inventory is stale")
    args = parser.parse_args()
    data = inventory()
    rendered = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != rendered:
            parser.exit(1, "Inventory is stale. Run python scripts/compatibility-inventory.py\n")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"{len(data['entries'])} literal lookups across {data['sourceFiles']} production files")


if __name__ == "__main__":
    main()
