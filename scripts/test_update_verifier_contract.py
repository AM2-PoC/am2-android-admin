#!/usr/bin/env python3
"""A refused update names the check that refused it, and reads the right API.

Build 57 refused build 63 with "identitas APK tidak valid" about an APK whose
identity was correct. Two faults, both already fixed once in the field client
and never carried across:

    val flags = GET_SIGNING_CERTIFICATES or GET_SIGNATURES
    ...
    val signingInfo = archive.signingInfo ?: return false      // API >= P
    archive.signatures?.toList().orEmpty()                     // API < P

GET_SIGNATURES reads the v1 JAR signature. The admin APK the staging channel
serves has no v1 signature at all -- META-INF holds no .RSA, only the v2/v3
signing block -- so on API 24 to 27, which minSdk 24 admits, that branch finds
nothing and the update is refused however correct the certificate is. Above P a
null signingInfo returns outright, ignoring the signatures flag that was asked
for on the same line.

And ten different checks answered with one Boolean, which the screen rendered
as one sentence. This screen already carries a comment about that exact shape
from the last time it happened for a different reason: an update refused for
its own build settings, reported as a bad artifact.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFIER = ROOT / "app/src/main/java/com/am2/admin/update/UpdateVerifier.kt"
SETTINGS = ROOT / "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt"


def code(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


class UpdateVerifierContractTest(unittest.TestCase):
    def setUp(self):
        self.verifier = code(VERIFIER.read_text(encoding="utf-8"))
        self.settings = code(SETTINGS.read_text(encoding="utf-8"))

    def test_no_check_refuses_without_naming_itself(self):
        self.assertNotIn(
            "return false", self.verifier,
            "a check refuses without saying which one it was",
        )

    def test_the_reasons_are_distinct(self):
        reasons = re.findall(r'Refused\(\s*"([a-z0-9_]+)', self.verifier)
        self.assertGreaterEqual(
            len(reasons), 7, "fewer refusal reasons than there are checks: %r" % (reasons,))
        self.assertEqual(
            len(reasons), len(set(reasons)),
            "two different checks refuse with the same reason: %r" % (reasons,))

    def test_a_v2_only_apk_is_not_refused_for_having_no_v1_signature(self):
        modern = self.verifier[self.verifier.index("GET_SIGNING_CERTIFICATES"):]
        self.assertNotIn(
            "signingInfo ?: return", modern,
            "a null signingInfo refuses the update although GET_SIGNATURES was "
            "requested on the same line",
        )
        self.assertRegex(
            modern, r"signingCertificateHistory|apkContentsSigners",
            "the v2 signers are never read, and the served APK has no v1 "
            "signature for the legacy accessor to find",
        )

    def test_the_operator_is_told_which_check_refused(self):
        self.assertNotIn(
            'IllegalStateException("identitas APK tidak valid")', self.settings,
            "ten different failures still reach the operator as one sentence",
        )
        self.assertRegex(
            self.settings, r"(reason|Refused)",
            "the update screen never surfaces the refusal reason",
        )


if __name__ == "__main__":
    unittest.main()
