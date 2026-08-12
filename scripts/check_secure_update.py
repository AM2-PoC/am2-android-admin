#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
required = {
    "app/src/main/AndroidManifest.xml": [
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "androidx.core.content.FileProvider",
        'android:usesCleartextTraffic="false"',
    ],
    "app/src/main/res/xml/network_security_config.xml": [
        'cleartextTrafficPermitted="false"',
        'src="system"',
        '@raw/isrg_root_x1',
    ],
    "app/src/main/java/com/am2/admin/update/UpdateVerifier.kt": [
        "com.am2.admin",
        "APPROVED_UPDATE_SIGNER_SHA256",
        "sha256",
        "signingCertificateHistory",
        "delete",
    ],
    "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt": [
        "UpdateVerifier.verify",
        "FileProvider.getUriForFile",
        "ACTION_INSTALL_PACKAGE",
    ],
}
errors = []
for relative, tokens in required.items():
    path = root / relative
    if not path.is_file():
        errors.append(f"missing {relative}")
        continue
    text = path.read_text()
    for token in tokens:
        if token not in text:
            errors.append(f"{relative}: missing {token}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("admin secure updater contract: PASS")
