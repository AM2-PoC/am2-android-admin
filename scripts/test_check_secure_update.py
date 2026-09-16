#!/usr/bin/env python3
import unittest
from pathlib import Path

from check_secure_update import executable_text

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt"


class SecureUpdateSourceCheckTest(unittest.TestCase):
    def test_comment_cannot_satisfy_executable_call(self):
        source = SETTINGS.read_text()
        without_calls = source.replace("UpdateVerifier.check(", "missingUpdateCheck(")
        without_calls += "\n// UpdateVerifier.check\n"

        self.assertIn("UpdateVerifier.check", without_calls)
        self.assertNotIn("UpdateVerifier.check", executable_text(without_calls))


if __name__ == "__main__":
    unittest.main()
