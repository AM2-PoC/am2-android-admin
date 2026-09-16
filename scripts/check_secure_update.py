#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]


def executable_text(content: str) -> str:
    output = []
    index = 0
    depth = 0
    state = "code"
    while index < len(content):
        pair = content[index:index + 2]
        char = content[index]
        if depth:
            if pair == "/*":
                depth += 1
                index += 2
            elif pair == "*/":
                depth -= 1
                index += 2
            else:
                index += 1
            continue
        if state in {"string", "char"}:
            output.append(char)
            if char == "\\" and index + 1 < len(content):
                output.append(content[index + 1])
                index += 2
                continue
            delimiter = '"' if state == "string" else "'"
            if char == delimiter:
                state = "code"
            index += 1
            continue
        if char == '"':
            state = "string"
            output.append(char)
            index += 1
            continue
        if char == "'":
            state = "char"
            output.append(char)
            index += 1
            continue
        if pair == "/*":
            depth = 1
            index += 2
            continue
        if pair == "//":
            newline = content.find("\n", index + 2)
            if newline < 0:
                break
            output.append("\n")
            index = newline + 1
            continue
        output.append(char)
        index += 1
    return "".join(output)


checks = {
    "app/src/main/java/com/am2/admin/update/UpdateMetadata.kt": [
        "versionCode",
        "APPROVED_URL",
        "sha256",
        "signerSha256",
    ],
    "app/src/main/java/com/am2/admin/update/UpdateVerifier.kt": [
        "com.am2.admin",
        "APPROVED_UPDATE_SIGNER_SHA256",
        "sha256",
        "apkContentsSigners",
        "delete",
    ],
    "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt": [
        "UpdateVerifier.check",
        "showVerifiedInstallDialog",
        "FileProvider.getUriForFile",
        "ACTION_INSTALL_PACKAGE",
        "canonicalPath",
        "followRedirects(false)",
    ],
    "app/src/main/AndroidManifest.xml": [
        "REQUEST_INSTALL_PACKAGES",
        "usesCleartextTraffic=\"false\"",
        "FileProvider",
    ],
    ".github/workflows/android-ci.yml": [
        "workflow_dispatch",
        "assembleProductionRelease",
        'apksigner" verify',
        "signer-metadata.txt",
        "source_commit",
        "retention-days: 3",
    ],
}

errors = []
for filename, required in checks.items():
    path = root / filename
    if not path.is_file():
        errors.append(f"missing: {filename}")
        continue
    content = executable_text(path.read_text())
    for token in required:
        if token not in content:
            errors.append(f"{filename}: missing {token}")
if __name__ == "__main__":
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print("admin secure updater contract: PASS")
