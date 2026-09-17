#!/usr/bin/env python3
"""The manifest has to be one the server will actually accept.

WebAdmin/admin_update_validation.php compares key sets rather than reading the
fields it knows: one extra field or one missing field refuses the whole
manifest with "manifest key set is not exact", and the handset is told there is
no update. Nothing on either side says which field was wrong.

That is how the channel died in the first place. The file was written by hand
with three fields while the server had grown to require eight, so every check
answered 404 for weeks while the panel kept announcing a version from the same
file.

The key set below is therefore a copy of a contract that lives in another
repository. It is written out in full, deliberately, so that a change there
fails here by name instead of failing silently on a handset.
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/write_update_manifest.py"
NOTES = ROOT / "app/release-notes.json"

SERVER_REQUIRES = {
    "package", "version_code", "version_name", "update_url",
    "sha256", "signer_sha256", "source_commit", "rollout",
}

COMMIT = "0123456789abcdef0123456789abcdef01234567"
SIGNER = "478c0cb4aa0a3374f152fa4cf90608c42520423c70a561e868a432a5efdcb9a3"


class WriteUpdateManifestTest(unittest.TestCase):
    def build(self, **overrides):
        """Run the script over a synthetic build and return what it wrote."""
        tmp = Path(tempfile.mkdtemp())
        apk = tmp / "admin.apk"
        apk.write_bytes(b"not really an apk, but these are the bytes published")

        (tmp / "apk-badging.txt").write_text(overrides.get("badging",
            "package: name='com.am2.admin.staging' versionCode='412' "
            "versionName='1.1.0-staging' compileSdkVersion='35'\n"
            "sdkVersion:'24'\n"))
        (tmp / "signer-metadata.txt").write_text(overrides.get("signer",
            f"Signer #1 certificate DN: CN=AM2\n"
            f"Signer #1 certificate SHA-256 digest: {SIGNER}\n"
            f"Signer #1 certificate SHA-1 digest: {'a' * 40}\n"))
        (tmp / "build-metadata.txt").write_text(overrides.get("build_metadata",
            f"byte_size=9363722\nsource_commit={COMMIT}\nenvironment=staging\n"))

        out = tmp / "admin_version.json"
        result = subprocess.run(
            [sys.executable, str(SCRIPT),
             "--apk", str(apk),
             "--badging", str(tmp / "apk-badging.txt"),
             "--signer", str(tmp / "signer-metadata.txt"),
             "--build-metadata", str(tmp / "build-metadata.txt"),
             "--release-notes", str(overrides.get("notes", NOTES)),
             "--update-base", overrides.get(
                 "base", "https://staging-webadmin.am2-poc.com/update"),
             "--out", str(out)],
            capture_output=True, text=True)
        return result, out

    def test_the_key_set_is_exactly_what_the_server_accepts(self):
        result, out = self.build()
        self.assertEqual(result.returncode, 0, result.stderr)
        manifest = json.loads(out.read_text())
        self.assertEqual(
            set(manifest) - {"changelog"}, SERVER_REQUIRES,
            "the server compares key sets; anything but an exact match is "
            "refused whole, and the handset is told there is no update",
        )

    def test_every_field_comes_from_the_build_rather_than_from_a_choice(self):
        _, out = self.build()
        manifest = json.loads(out.read_text())
        self.assertEqual(manifest["package"], "com.am2.admin.staging")
        self.assertEqual(manifest["version_name"], "1.1.0-staging")
        self.assertEqual(manifest["version_code"], 412)
        self.assertEqual(manifest["signer_sha256"], SIGNER)
        self.assertEqual(manifest["source_commit"], COMMIT)
        self.assertEqual(
            manifest["update_url"],
            "https://staging-webadmin.am2-poc.com/update/admin.apk",
            "the server rebuilds this from its own base and refuses anything else",
        )

    def test_version_code_is_an_integer_not_the_string_aapt_printed(self):

        _, out = self.build()
        raw = json.loads(out.read_text())
        self.assertIsInstance(raw["version_code"], int)
        self.assertIsInstance(raw["rollout"], int)

    def test_the_digest_is_of_the_apk_being_published(self):
        import hashlib
        _, out = self.build()
        manifest = json.loads(out.read_text())
        expected = hashlib.sha256(
            b"not really an apk, but these are the bytes published").hexdigest()
        self.assertEqual(manifest["sha256"], expected)

    def test_release_notes_carry_every_language_the_panel_renders(self):
        _, out = self.build()
        notes = json.loads(out.read_text())["changelog"]
        self.assertIsInstance(notes, dict)
        self.assertEqual(set(notes), {"id", "en"},
                         "the panel renders both, so both must be published")

    def test_two_signing_certificates_stop_the_build(self):
        other = "b" * 64
        result, _ = self.build(signer=(
            f"Signer #1 certificate SHA-256 digest: {SIGNER}\n"
            f"Signer #2 certificate SHA-256 digest: {other}\n"))
        self.assertNotEqual(result.returncode, 0,
                            "which certificate a handset must trust is not a guess")

    def test_a_build_with_no_signer_stops(self):
        result, _ = self.build(signer="Signer #1 certificate DN: CN=AM2\n")
        self.assertNotEqual(result.returncode, 0)

    def test_a_truncated_commit_stops(self):
        result, _ = self.build(build_metadata="source_commit=0123456\n")
        self.assertNotEqual(result.returncode, 0,
                            "the validator requires a full lowercase SHA-1")


if __name__ == "__main__":
    unittest.main(verbosity=2)
