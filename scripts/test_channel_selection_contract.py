#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/am2/admin/ui/channels/ChannelsActivity.kt"


class ChannelSelectionContractTest(unittest.TestCase):
    def test_selection_sync_does_not_replace_the_select_all_listener(self):
        source = SOURCE.read_text()
        dialog = source[source.index("private fun showManageAccessDialog"):]
        dialog = dialog[:dialog.index("\n    private fun saveAccess")]

        self.assertNotIn("Wait, recursion?", dialog)
        self.assertNotIn("Fix the reference", dialog)
        self.assertEqual(dialog.count("cbSelectAll.setOnCheckedChangeListener {"), 1)
        self.assertIn("syncingSelectAll", dialog)
        self.assertIn("if (!syncingSelectAll)", dialog)


if __name__ == "__main__":
    unittest.main()
