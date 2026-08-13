#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
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
        "UpdateVerifier.verify",
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
    content = path.read_text()
    for token in required:
        if token not in content:
            errors.append(f"{filename}: missing {token}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("admin secure updater contract: PASS")
