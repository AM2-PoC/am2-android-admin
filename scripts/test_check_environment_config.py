#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle.kts"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"


class EnvironmentConfigTest(unittest.TestCase):
    def test_exact_environment_identity_and_endpoints(self):
        text = GRADLE.read_text()
        for token in (
            'flavorDimensions += "environment"',
            'create("dev")',
            'applicationIdSuffix = ".dev"',
            'create("staging")',
            'applicationIdSuffix = ".staging"',
            'create("production")',
            "https://dev-webadmin.am2-poc.com/",
            "https://staging-webadmin.am2-poc.com/",
            "https://webadmin.am2-poc.com/",
            'buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "false")',
            'buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "true")',
        ):
            self.assertIn(token, text)

    def test_runtime_endpoints_come_from_build_config(self):
        retrofit = (ROOT / "app/src/main/java/com/am2/admin/data/api/RetrofitClient.kt").read_text()
        update = (ROOT / "app/src/main/java/com/am2/admin/update/UpdateMetadata.kt").read_text()
        settings = (ROOT / "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt").read_text()
        verifier = (ROOT / "app/src/main/java/com/am2/admin/update/UpdateVerifier.kt").read_text()
        self.assertIn("BuildConfig.BASE_URL", retrofit)
        self.assertIn("BuildConfig.UPDATE_APK_URL", update)
        self.assertIn("BuildConfig.BASE_URL", settings)
        self.assertIn("BuildConfig.SELF_UPDATE_ENABLED", verifier)
        self.assertIn("BuildConfig.APPLICATION_ID", verifier)
        self.assertNotIn('EXPECTED_PACKAGE = "com.am2.admin"', verifier)

    def test_ci_uploads_only_release_artifacts(self):
        text = WORKFLOW.read_text()
        self.assertIn("github.event_name != 'pull_request'", text)
        self.assertIn("github.event.inputs.lane == 'release'", text)
        self.assertIn("startsWith(github.ref, 'refs/tags/v')", text)
        self.assertEqual(1, text.count("actions/upload-artifact@v4"))
        self.assertIn("retention-days: 3", text)
        self.assertIn("AM2_APPROVED_SIGNER_SHA256", text)
        self.assertIn('aapt" dump badging', text)
        self.assertIn("Production release requires AM2_APPROVED_SIGNER_SHA256", GRADLE.read_text())

    def test_compatibility_uses_accelerated_emulator_and_explicit_readiness(self):
        text = WORKFLOW.read_text()
        helper = ROOT / "scripts/run_emulator_compatibility.sh"
        self.assertIn('KERNEL=="kvm", GROUP="kvm", MODE="0666"', text)
        self.assertIn("disable-linux-hw-accel: false", text)
        self.assertIn("disable-animations: false", text)
        self.assertIn('script: sh scripts/run_emulator_compatibility.sh "com.am2.admin.dev"', text)
        self.assertTrue(helper.is_file())
        helper_text = helper.read_text()
        self.assertIn("sys.boot_completed", helper_text)
        self.assertIn("cmd package list packages", helper_text)
        self.assertIn("settings get global device_provisioned", helper_text)
        self.assertIn("adb install --no-streaming", helper_text)
        self.assertIn("adb shell monkey", helper_text)
        self.assertIn("adb shell am instrument", helper_text)


if __name__ == "__main__":
    unittest.main()
