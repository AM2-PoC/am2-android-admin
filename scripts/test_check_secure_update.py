#!/usr/bin/env python3
import unittest
from pathlib import Path

from check_secure_update import executable_text

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt"


class SecureUpdateSourceCheckTest(unittest.TestCase):
    def test_comments_cannot_satisfy_executable_call(self):
        source = SETTINGS.read_text().replace("UpdateVerifier.check(", "missingUpdateCheck(")
        source += "\n// UpdateVerifier.check\n"
        source += "\n/* outer /* nested */ UpdateVerifier.check */\n"

        self.assertIn("UpdateVerifier.check", source)
        self.assertNotIn("UpdateVerifier.check", executable_text(source))

    def test_comment_markers_inside_strings_remain_code(self):
        source = 'val mime = "*/*"\nval url = "https://example.invalid"\nUpdateVerifier.check()'
        cleaned = executable_text(source)

        self.assertIn('"*/*"', cleaned)
        self.assertIn('"https://example.invalid"', cleaned)
        self.assertIn("UpdateVerifier.check()", cleaned)


if __name__ == "__main__":
    unittest.main()
