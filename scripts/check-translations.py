#!/usr/bin/env python3
"""Validate Android string resources and Crowdin translations."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES_DIR = ROOT / "app" / "src" / "main" / "res"
SOURCE_XML = RES_DIR / "values" / "strings.xml"
JAVA_DIR = ROOT / "app" / "src" / "main" / "java"
MANIFEST_XML = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

VALID_NAME_RE = re.compile(r"^[a-z][a-z0-9_]*$")
VALID_QUALIFIER_DIR_RE = re.compile(r"^values-[a-zA-Z0-9+_.-]+$")
VALID_PLURAL_QUANTITIES = {"zero", "one", "two", "few", "many", "other"}

# Java / Android String.format specifier pattern.
# Matches %% (escaped percent), %n (newline), or %[index$][flags][width][.precision]conversion.
FORMAT_SPEC_RE = re.compile(
    r"%(?:%|n|(?:(?P<index>[1-9]\d*)\$)?(?P<flags>[-#+ 0,(<]*)(?P<width>\d+)?(?:\.(?P<prec>\d+))?(?P<conv>[bBhHsScCdoxXeEfgGaAtT]))"
)
JAVA_STRING_REF_RE = re.compile(r"\bR\.string\.([a-zA-Z0-9_]+)\b")
JAVA_PLURAL_REF_RE = re.compile(r"\bR\.plurals\.([a-zA-Z0-9_]+)\b")
MANIFEST_STRING_REF_RE = re.compile(r"@string/([a-zA-Z0-9_]+)")


def element_text(elem: ET.Element) -> str:
    """Concatenate element text including any inline child markup text."""
    return "".join(elem.itertext())


def check_android_quoting(value: str, context: str) -> list[str]:
    """Validate Android XML apostrophe and double-quote escaping rules."""
    errors: list[str] = []
    in_quotes = False
    i = 0
    length = len(value)
    while i < length:
        ch = value[i]
        if ch == "\\":
            if i + 1 >= length:
                errors.append(f"{context}: trailing unescaped backslash")
                break
            i += 2
            continue
        if ch == '"':
            in_quotes = not in_quotes
        elif ch == "'" and not in_quotes:
            errors.append(f"{context}: unescaped apostrophe (use \\' or wrap in double quotes)")
            break
        i += 1
    if in_quotes:
        errors.append(f"{context}: unclosed double quote")
    return errors


def canonical_conversion(conv: str) -> str:
    """Normalize equivalent Java Formatter conversion groups."""
    lower = conv.lower()
    if lower in ("d", "o", "x"):
        return "d"
    if lower in ("e", "f", "g", "a"):
        return "f"
    if lower in ("s", "b", "h"):
        return "s"
    return lower


def extract_placeholders(value: str, context: str, require_positional_multi: bool) -> tuple[dict[int, str], list[str]]:
    """Extract {1-based-index: canonical_conversion} and report malformed percent sequences."""
    errors: list[str] = []
    found: list[tuple[int | None, str]] = []
    pos = 0
    while pos < len(value):
        idx = value.find("%", pos)
        if idx == -1:
            break
        m = FORMAT_SPEC_RE.match(value, idx)
        if not m:
            snippet = value[idx : idx + 10]
            errors.append(f"{context}: invalid or unescaped format specifier near {snippet!r} (use %% for literal %)")
            pos = idx + 1
            continue
        matched = m.group(0)
        pos = m.end()
        if matched in ("%%", "%n"):
            continue
        raw_index = m.group("index")
        conv = canonical_conversion(m.group("conv"))
        found.append((int(raw_index) if raw_index else None, conv))

    if require_positional_multi and len(found) > 1 and any(idx is None for idx, _ in found):
        errors.append(f"{context}: multiple format specifiers must use positional indices like %1$s, %2$d")

    spec_map: dict[int, str] = {}
    implicit = 1
    for idx, conv in found:
        slot = idx if idx is not None else implicit
        if idx is None:
            implicit += 1
        existing = spec_map.get(slot)
        if existing is not None and existing != conv:
            errors.append(f"{context}: conflicting conversion types for argument %{slot}$ ({existing} vs {conv})")
        spec_map[slot] = conv
    return spec_map, errors


def parse_strings_xml(path: Path, is_source: bool) -> tuple[dict[str, dict], dict[str, dict], list[str]]:
    """Parse an Android strings.xml file into string entries, plural entries, and errors."""
    errors: list[str] = []
    strings: dict[str, dict] = {}
    plurals: dict[str, dict] = {}

    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        return strings, plurals, [f"{path}: invalid XML: {exc}"]

    root = tree.getroot()
    if root.tag != "resources":
        return strings, plurals, [f"{path}: root element must be <resources>, got <{root.tag}>"]

    for child in root:
        if not isinstance(child.tag, str):
            continue
        if child.tag == "string":
            name = (child.attrib.get("name") or "").strip()
            ctx = f"{path}: <string name={name!r}>"
            if not name or not VALID_NAME_RE.match(name):
                errors.append(f"{ctx}: invalid resource name")
                continue
            if name in strings:
                errors.append(f"{ctx}: duplicate string key")
                continue
            formatted = child.attrib.get("formatted", "true").lower() != "false"
            translatable = child.attrib.get("translatable", "true").lower() != "false"
            value = element_text(child)
            errors.extend(check_android_quoting(value, ctx))
            specs: dict[int, str] = {}
            if formatted:
                specs, spec_errors = extract_placeholders(value, ctx, require_positional_multi=is_source)
                errors.extend(spec_errors)
            strings[name] = {
                "value": value,
                "formatted": formatted,
                "translatable": translatable,
                "specs": specs,
            }
        elif child.tag == "plurals":
            name = (child.attrib.get("name") or "").strip()
            ctx = f"{path}: <plurals name={name!r}>"
            if not name or not VALID_NAME_RE.match(name):
                errors.append(f"{ctx}: invalid plurals resource name")
                continue
            if name in plurals:
                errors.append(f"{ctx}: duplicate plurals key")
                continue
            items: dict[str, dict] = {}
            for item in child:
                if not isinstance(item.tag, str):
                    continue
                if item.tag != "item":
                    errors.append(f"{ctx}: unexpected child tag <{item.tag}>")
                    continue
                quantity = (item.attrib.get("quantity") or "").strip()
                item_ctx = f"{ctx} <item quantity={quantity!r}>"
                if quantity not in VALID_PLURAL_QUANTITIES:
                    errors.append(f"{item_ctx}: invalid plural quantity")
                    continue
                if quantity in items:
                    errors.append(f"{item_ctx}: duplicate quantity")
                    continue
                value = element_text(item)
                errors.extend(check_android_quoting(value, item_ctx))
                specs, spec_errors = extract_placeholders(value, item_ctx, require_positional_multi=is_source)
                errors.extend(spec_errors)
                items[quantity] = {"value": value, "specs": specs}
            if is_source and "other" not in items:
                errors.append(f"{ctx}: source plurals must define quantity=\"other\"")
            plurals[name] = {"items": items}

    return strings, plurals, errors


def compare_translation(
    source_strings: dict[str, dict],
    source_plurals: dict[str, dict],
    trans_path: Path,
    trans_strings: dict[str, dict],
    trans_plurals: dict[str, dict],
) -> list[str]:
    """Validate format-specifier parity between source and a locale strings.xml."""
    errors: list[str] = []
    for name, entry in trans_strings.items():
        src = source_strings.get(name)
        if src is None:
            continue
        if not src["translatable"]:
            errors.append(f"{trans_path}: <string name={name!r}> is marked translatable=\"false\" in source")
            continue
        if src["formatted"] and entry["formatted"] and entry["specs"] != src["specs"]:
            errors.append(
                f"{trans_path}: <string name={name!r}> placeholder mismatch: expected {src['specs']}, got {entry['specs']}"
            )

    for name, entry in trans_plurals.items():
        src = source_plurals.get(name)
        if src is None:
            continue
        expected_specs = src["items"].get("other", {}).get("specs", {})
        for quantity, item in entry["items"].items():
            for slot, conv in item["specs"].items():
                if slot not in expected_specs:
                    errors.append(
                        f"{trans_path}: <plurals name={name!r}> quantity={quantity!r} references unexpected placeholder %{slot}${conv}"
                    )
                elif expected_specs[slot] != conv:
                    errors.append(
                        f"{trans_path}: <plurals name={name!r}> quantity={quantity!r} placeholder %{slot}$ type mismatch: expected {expected_specs[slot]}, got {conv}"
                    )
    return errors


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def check_code_references(
    source_strings: dict[str, dict],
    source_plurals: dict[str, dict],
    java_dir: Path,
    manifest_xml: Path,
) -> tuple[set[str], set[str], list[str]]:
    """Verify every R.string.*, R.plurals.*, and @string/* reference exists in source strings.xml."""
    errors: list[str] = []
    used_strings: set[str] = set()
    used_plurals: set[str] = set()

    if java_dir.is_dir():
        for java_file in sorted(java_dir.rglob("*.java")):
            content = java_file.read_text(encoding="utf-8")
            for match in JAVA_STRING_REF_RE.finditer(content):
                key = match.group(1)
                used_strings.add(key)
                if key not in source_strings:
                    errors.append(f"{display_path(java_file)}: missing R.string.{key} in values/strings.xml")
            for match in JAVA_PLURAL_REF_RE.finditer(content):
                key = match.group(1)
                used_plurals.add(key)
                if key not in source_plurals:
                    errors.append(f"{display_path(java_file)}: missing R.plurals.{key} in values/strings.xml")

    if manifest_xml.is_file():
        manifest = manifest_xml.read_text(encoding="utf-8")
        for match in MANIFEST_STRING_REF_RE.finditer(manifest):
            key = match.group(1)
            used_strings.add(key)
            if key not in source_strings:
                errors.append(f"{display_path(manifest_xml)}: missing @string/{key} in values/strings.xml")

    return used_strings, used_plurals, errors


def validate_repository(res_dir: Path = RES_DIR, java_dir: Path = JAVA_DIR, manifest_xml: Path = MANIFEST_XML) -> tuple[list[str], str]:
    source_xml = res_dir / "values" / "strings.xml"
    if not source_xml.is_file():
        return [f"Missing source strings file: {source_xml}"], ""

    source_strings, source_plurals, errors = parse_strings_xml(source_xml, is_source=True)
    _, _, ref_errors = check_code_references(source_strings, source_plurals, java_dir, manifest_xml)
    errors.extend(ref_errors)

    locale_count = 0
    for child in sorted(res_dir.iterdir()):
        if not child.is_dir() or child.name == "values" or not child.name.startswith("values-"):
            continue
        if not VALID_QUALIFIER_DIR_RE.match(child.name):
            errors.append(f"{child}: invalid Android resource qualifier directory name")
            continue
        trans_xml = child / "strings.xml"
        if not trans_xml.is_file():
            continue
        locale_count += 1
        t_strings, t_plurals, t_errors = parse_strings_xml(trans_xml, is_source=False)
        errors.extend(t_errors)
        errors.extend(compare_translation(source_strings, source_plurals, trans_xml, t_strings, t_plurals))

    summary = (
        f"Translation checks pass: {len(source_strings)} strings, "
        f"{len(source_plurals)} plurals, {locale_count} locale overlay(s)."
    )
    return errors, summary


def run_self_test() -> int:
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        res = base / "res"
        (res / "values").mkdir(parents=True)
        (res / "values-fr").mkdir(parents=True)
        src = res / "values" / "strings.xml"
        fr = res / "values-fr" / "strings.xml"

        src.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            '  <string name="hello">Hello %1$s (%2$d)</string>\n'
            '  <string name="pct" formatted="false">Bat %</string>\n'
            '  <plurals name="items">\n'
            '    <item quantity="one">%1$d item</item>\n'
            '    <item quantity="other">%1$d items</item>\n'
            "  </plurals>\n"
            "</resources>\n",
            encoding="utf-8",
        )
        fr.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            '  <string name="hello">Bonjour (%2$d) %1$s</string>\n'
            '  <string name="pct" formatted="false">Bat %</string>\n'
            '  <plurals name="items">\n'
            '    <item quantity="one">Un element</item>\n'
            '    <item quantity="other">%1$d elements</item>\n'
            "  </plurals>\n"
            "</resources>\n",
            encoding="utf-8",
        )
        errors, _ = validate_repository(res, base / "no_java", base / "no_manifest.xml")
        assert not errors, f"Expected clean pass, got: {errors}"

        # Escaped apostrophes must pass.
        fr.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            r'  <string name="hello">L\'ami %1$s (%2$d) et d\'autres</string>' + "\n"
            "</resources>\n",
            encoding="utf-8",
        )
        errors, _ = validate_repository(res, base / "no_java", base / "no_manifest.xml")
        assert not errors, f"Escaped apostrophes should pass, got: {errors}"

        # Unescaped apostrophe must fail.
        fr.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            '  <string name="hello">C\'est %1$s (%2$d)</string>\n'
            "</resources>\n",
            encoding="utf-8",
        )
        errors, _ = validate_repository(res, base / "no_java", base / "no_manifest.xml")
        assert any("unescaped apostrophe" in e for e in errors), f"Expected apostrophe error, got: {errors}"

        # Placeholder type mismatch must fail.
        fr.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            '  <string name="hello">Bonjour %1$s (%2$s)</string>\n'
            "</resources>\n",
            encoding="utf-8",
        )
        errors, _ = validate_repository(res, base / "no_java", base / "no_manifest.xml")
        assert any("placeholder mismatch" in e for e in errors), f"Expected mismatch error, got: {errors}"

        # Missing Java reference must fail.
        java_dir = base / "java"
        java_dir.mkdir()
        (java_dir / "Sample.java").write_text("int id = R.string.does_not_exist;", encoding="utf-8")
        fr.unlink()
        s_str, s_plu, _ = parse_strings_xml(src, is_source=True)
        _, _, ref_errs = check_code_references(s_str, s_plu, java_dir, base / "no_manifest.xml")
        assert any("does_not_exist" in e for e in ref_errs), f"Expected missing ref error, got: {ref_errs}"

    print("check-translations.py self-test passed.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate Android string resources and translations.")
    parser.add_argument("--self-test", action="store_true", help="Run built-in validator unit tests.")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    errors, summary = validate_repository()
    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1
    print(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
