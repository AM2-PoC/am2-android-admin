#!/usr/bin/env python3

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

REQUIRED_KEYS = sorted([
    "package", "version_code", "version_name", "update_url",
    "sha256", "signer_sha256", "source_commit", "rollout",
])


def badging(path: Path) -> dict:
    """package, versionCode and versionName, as aapt reported them."""
    text = path.read_text(errors="replace")
    line = next((l for l in text.splitlines() if l.startswith("package:")), "")
    found = dict(re.findall(r"(\w+)='([^']*)'", line))
    for field in ("name", "versionCode", "versionName"):
        if not found.get(field):
            raise SystemExit(f"{path}: aapt reported no {field}")
    return found


def signer_digest(path: Path) -> str:
    """The signing certificate's SHA-256, as apksigner printed it.

    Signer #1 is the one the handset checks. A rotated key prints more than one
    block and the first is still the certificate in force, but a manifest that
    silently picked among several would be guessing, so more than one distinct
    digest stops here rather than publishing a choice nobody made.
    """
    digests = {
        m.group(1).replace(":", "").lower()
        for m in re.finditer(
            r"certificate SHA-?256 digest:\s*([0-9a-fA-F:]+)",
            path.read_text(errors="replace"),
        )
    }
    if not digests:
        raise SystemExit(f"{path}: apksigner printed no certificate digest")
    if len(digests) > 1:
        raise SystemExit(
            f"{path}: {len(digests)} different signing certificates; "
            "which one the handset must trust is not something to guess"
        )
    digest = digests.pop()
    if not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise SystemExit(f"{path}: {digest!r} is not a SHA-256 digest")
    return digest


def source_commit(path: Path) -> str:
    text = path.read_text(errors="replace")
    match = re.search(r"^source_commit=([0-9a-fA-F]{40})$", text, re.MULTILINE)
    if not match:
        raise SystemExit(f"{path}: no full source_commit recorded")
    return match.group(1).lower()


def release_notes(path: Path) -> dict:
    """Notes in every language the panel can render them in.

    An object rather than a string because the panel is bilingual and this is
    the one string on it that cannot live in the catalogue -- notes are written
    per release, not per key. WebAdmin/i18n.php reads the reader's locale out
    of it and still accepts a plain string, so an older manifest keeps working.
    """
    notes = json.loads(path.read_text())
    if not isinstance(notes, dict) or not notes:
        raise SystemExit(f"{path}: release notes must be an object keyed by locale")
    for locale, text in notes.items():
        if not isinstance(text, str) or not text.strip():
            raise SystemExit(f"{path}: the {locale} note is empty")
    return notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--badging", type=Path, required=True)
    parser.add_argument("--signer", type=Path, required=True)
    parser.add_argument("--build-metadata", type=Path, required=True)
    parser.add_argument("--release-notes", type=Path, required=True)
    parser.add_argument(
        "--update-base", required=True,
        help="the server's update directory, e.g. https://webadmin.am2-poc.com/update",
    )
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    apk = badging(args.badging)
    base = args.update_base.rstrip("/")

    manifest = {
        "package": apk["name"],
        "version_code": int(apk["versionCode"]),
        "version_name": apk["versionName"],

        "update_url": f"{base}/admin.apk",
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "signer_sha256": signer_digest(args.signer),
        "source_commit": source_commit(args.build_metadata),

        "rollout": 100,
        "changelog": release_notes(args.release_notes),
    }

    if sorted(k for k in manifest if k != "changelog") != REQUIRED_KEYS:
        raise SystemExit("the manifest key set no longer matches what the server accepts")

    args.out.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    print(f"{args.out}: {manifest['package']} {manifest['version_name']} "
          f"({manifest['version_code']}) -> {manifest['update_url']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
