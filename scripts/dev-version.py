"""Versioning, changelog generation, and pruning helpers for dev pre-releases."""

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path


REPO_URL = "https://github.com/BlueOutside/Dressroom-Advanced"
DEV_TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)-dev\.(\d+)$")
VERSION_NAME_RE = re.compile(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)(?:-[^"]+)?"')


def parse_base_version(gradle_text: str) -> tuple[int, int, int]:
    match = VERSION_NAME_RE.search(gradle_text)
    if not match:
        raise ValueError("Missing SemVer versionName in app/build.gradle.kts")
    return int(match.group(1)), int(match.group(2)), int(match.group(3))


def version_code_for(major: int, minor: int, patch: int, build: int) -> int:
    return major * 10_000_000 + minor * 100_000 + patch * 1_000 + build


def compute_dev_version(
    base_version: tuple[int, int, int], existing_tags: list[str]
) -> dict[str, object]:
    major, minor, patch = base_version
    tag_re = re.compile(rf"^v{major}\.{minor}\.{patch}-dev\.(\d+)$")
    max_n = 0
    for raw in existing_tags:
        match = tag_re.match(raw.strip())
        if match:
            max_n = max(max_n, int(match.group(1)))
    next_n = max_n + 1
    if next_n > 998:
        raise ValueError(
            f"Dev build number {next_n} exceeds 998 for {major}.{minor}.{patch}; "
            "bump patch/minor/major versionName first."
        )
    version_name = f"{major}.{minor}.{patch}-dev.{next_n}"
    return {
        "tag": f"v{version_name}",
        "versionName": version_name,
        "versionCode": version_code_for(major, minor, patch, next_n),
        "stableVersionCode": version_code_for(major, minor, patch, 999),
        "devNumber": next_n,
    }


def _git(project: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(project), *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )


def previous_ancestor_tag(project: Path) -> str | None:
    res = _git(project, "describe", "--tags", "--abbrev=0", "HEAD")
    if res.returncode == 0 and res.stdout.strip():
        return res.stdout.strip()
    return None


def collect_changelog_entries(
    project: Path, prev_tag: str | None, max_commits: int = 50
) -> list[str]:
    rev_range = f"{prev_tag}..HEAD" if prev_tag else "HEAD"
    fmt = "--pretty=format:- %s (`%h`)"
    res = _git(project, "log", rev_range, "--no-merges", fmt)
    lines = [line.strip() for line in res.stdout.splitlines() if line.strip()]
    if not lines:
        res = _git(project, "log", rev_range, "--first-parent", fmt)
        lines = [line.strip() for line in res.stdout.splitlines() if line.strip()]
    if not lines:
        return ["- No commit messages found."]
    if len(lines) > max_commits:
        extra = len(lines) - max_commits
        lines = lines[:max_commits] + [f"- ... and {extra} more commit(s)"]
    return lines


def format_changelog(meta: dict[str, object], entries: list[str]) -> str:
    lines = [
        f"## Pre-release {meta['tag']}",
        "",
        f"Automated `dev` build from commit `{meta['shortSha']}`.",
        "",
    ]
    prev_tag = meta.get("prevTag")
    lines.append(f"### Changes since {prev_tag}" if prev_tag else "### Changes")
    lines.append("")
    lines.extend(entries)
    if prev_tag:
        lines.extend(["", f"**Full Changelog**: {REPO_URL}/compare/{prev_tag}...{meta['tag']}"])
    return "\n".join(lines) + "\n"


def append_apk_digest(apk_path: Path, changelog_path: Path) -> str:
    digest = hashlib.sha256()
    with apk_path.open("rb") as handle:
        while chunk := handle.read(65536):
            digest.update(chunk)
    sha = digest.hexdigest()
    existing = changelog_path.read_text(encoding="utf-8") if changelog_path.exists() else ""
    if existing and not existing.endswith("\n"):
        existing += "\n"
    addition = f"\n### Artifact\n- File: `{apk_path.name}`\n- SHA-256: `{sha}`\n"
    changelog_path.write_text(existing + addition, encoding="utf-8", newline="\n")
    return sha


def select_prunable_tags(releases: list[dict[str, object]], keep: int = 10) -> list[str]:
    if keep < 0:
        raise ValueError("keep must be non-negative")
    dev_entries: list[tuple[tuple[int, int, int, int], str]] = []
    for entry in releases:
        if not isinstance(entry, dict) or not entry.get("prerelease"):
            continue
        tag = entry.get("tag_name")
        if not isinstance(tag, str):
            continue
        match = DEV_TAG_RE.match(tag.strip())
        if not match:
            continue
        key = (
            int(match.group(1)),
            int(match.group(2)),
            int(match.group(3)),
            int(match.group(4)),
        )
        dev_entries.append((key, tag.strip()))
    dev_entries.sort(key=lambda item: item[0], reverse=True)
    return [tag for _, tag in dev_entries[keep:]]


def run_self_test() -> int:
    checks = 0

    def check(cond: bool, msg: str) -> None:
        nonlocal checks
        checks += 1
        if not cond:
            raise AssertionError(msg)

    check(parse_base_version('versionName = "2.0.0"') == (2, 0, 0), "parse 2.0.0")
    check(parse_base_version('versionName = "2.1.3-rc.1"') == (2, 1, 3), "parse suffixed")

    first = compute_dev_version((2, 0, 0), [])
    check(first["tag"] == "v2.0.0-dev.1", "first dev tag")
    check(first["versionName"] == "2.0.0-dev.1", "first dev versionName")
    check(first["versionCode"] == 20000001, "first dev versionCode")
    check(first["stableVersionCode"] == 20000999, "stable versionCode")

    bumped = compute_dev_version(
        (2, 0, 0),
        ["v2.0.0-dev.1", "v2.0.0-dev.9", "v2.0.0-dev.10", "v1.1.5", "v2.0.1-dev.3"],
    )
    check(bumped["tag"] == "v2.0.0-dev.11", "next dev tag increments numeric max")
    check(bumped["versionCode"] == 20000011, "next dev versionCode")

    max_dev = compute_dev_version((2, 0, 0), ["v2.0.0-dev.997"])
    check(max_dev["versionCode"] < max_dev["stableVersionCode"], "dev < stable code")
    check(max_dev["stableVersionCode"] < version_code_for(2, 0, 1, 1), "stable < next patch dev")

    try:
        compute_dev_version((2, 0, 0), ["v2.0.0-dev.998"])
        raise AssertionError("expected overflow guard")
    except ValueError:
        checks += 1

    releases: list[dict[str, object]] = [
        {"tag_name": "v2.0.0", "prerelease": False},
        {"tag_name": "v2.0.0-rc.1", "prerelease": True},
    ]
    for i in [5, 1, 12, 3, 9, 2, 11, 4, 8, 10, 6, 7]:
        releases.append({"tag_name": f"v2.0.0-dev.{i}", "prerelease": True})
    pruned = select_prunable_tags(releases, keep=10)
    check(pruned == ["v2.0.0-dev.2", "v2.0.0-dev.1"], f"unexpected prune list: {pruned}")

    print(f"dev-version self-test: {checks} checks passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="Run built-in unit checks")
    parser.add_argument("--json-out", help="Write computed dev version metadata JSON to path")
    parser.add_argument("--changelog-out", help="Write or append Markdown changelog to path")
    parser.add_argument("--append-apk", help="Append APK filename and SHA-256 to --changelog-out")
    parser.add_argument("--prune-list", help="JSON file from GitHub releases API to prune")
    parser.add_argument("--keep", type=int, default=10, help="Newest dev pre-releases to keep")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if args.prune_list:
        data = json.loads(Path(args.prune_list).read_text(encoding="utf-8-sig"))
        for tag in select_prunable_tags(data, keep=args.keep):
            print(tag)
        return 0

    if args.append_apk:
        if not args.changelog_out:
            parser.error("--append-apk requires --changelog-out")
        append_apk_digest(Path(args.append_apk), Path(args.changelog_out))
        return 0

    project = Path(__file__).resolve().parents[1]
    gradle_text = (project / "app/build.gradle.kts").read_text(encoding="utf-8-sig")
    base = parse_base_version(gradle_text)
    tags_res = _git(project, "tag", "--list", f"v{base[0]}.{base[1]}.{base[2]}-dev.*")
    if tags_res.returncode != 0:
        raise RuntimeError(f"git tag failed: {tags_res.stderr}")
    meta = compute_dev_version(base, tags_res.stdout.splitlines())
    sha_res = _git(project, "rev-parse", "HEAD")
    short_res = _git(project, "rev-parse", "--short", "HEAD")
    prev_tag = previous_ancestor_tag(project)
    meta["sha"] = sha_res.stdout.strip()
    meta["shortSha"] = short_res.stdout.strip()
    meta["prevTag"] = prev_tag

    if args.changelog_out:
        entries = collect_changelog_entries(project, prev_tag)
        changelog = format_changelog(meta, entries)
        out = Path(args.changelog_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(changelog, encoding="utf-8", newline="\n")

    rendered = json.dumps(meta, indent=2) + "\n"
    if args.json_out:
        out = Path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered, encoding="utf-8", newline="\n")
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
