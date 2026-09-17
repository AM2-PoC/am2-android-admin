#!/usr/bin/env python3
"""Update refusals expose stable reasons and support v2/v3 signers on API 24+."""
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
