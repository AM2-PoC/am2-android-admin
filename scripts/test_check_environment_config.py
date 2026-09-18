#!/usr/bin/env python3
import re
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
        verifier = (ROOT / "app/src/main/java/com/am2/admin/update/UpdateVerifier.kt").read_text()
        self.assertIn("BuildConfig.BASE_URL", retrofit)
        self.assertIn("BuildConfig.UPDATE_APK_URL", update)
        self.assertIn("BuildConfig.SELF_UPDATE_ENABLED", verifier)
        self.assertIn("BuildConfig.APPLICATION_ID", verifier)
        self.assertNotIn('EXPECTED_PACKAGE = "com.am2.admin"', verifier)

    def test_no_source_file_hardcodes_an_endpoint(self):

        offenders = sorted(
            path.relative_to(ROOT).as_posix()
            for path in (ROOT / "app/src/main/java").rglob("*.kt")
            if re.search(r'"https?://', path.read_text())
        )
        self.assertEqual([], offenders)

    def test_the_published_url_matches_the_url_the_app_asks_for(self):

        gradle = GRADLE.read_text()
        workflow = WORKFLOW.read_text()
        for base in re.findall(r"--update-base\s+(\S+)", workflow):
            expected = base.rstrip("/") + "/admin.apk"
            self.assertIn(
                f'"{expected}"', gradle,
                f"CI publishes {expected} but no flavour asks for it; the "
                "handset would download from somewhere the manifest never named",
            )

    def test_ci_has_bounded_staging_candidate_contract(self):
        text = WORKFLOW.read_text()
        self.assertIn("- staging", text)
        self.assertIn("name: staging-artifact", text)
        self.assertIn("github.event.inputs.lane == 'staging'", text)
        self.assertIn("assembleStagingDebug", text)
        self.assertIn("am2-admin-staging-debug.apk", text)
        self.assertIn("SHA256SUMS", text)
        self.assertIn("environment=staging", text)
        self.assertIn("variant=StagingDebug", text)
        self.assertIn("api_range=24+", text)
        self.assertIn("am2-admin-staging-debug-${{ github.sha }}", text)
        self.assertIn("retention-days: 3", text)
        staging_job = text[text.index("name: staging-artifact"):text.index("name: release-artifact")]

        self.assertNotIn(
            "vars.AM2_APPROVED_SIGNER_SHA256", staging_job,
            "the production signer reaches the staging lane, so a staging build "
            "would trust production-signed APKs",
        )
        self.assertIn(
            "steps.approved_signer.outputs.digest", staging_job,
            "staging trusts no signer at all, so every update it downloads is "
            "refused and reported as the APK's identity being wrong",
        )
        self.assertNotIn("assembleProductionRelease", text[text.index("name: staging-artifact"):text.index("name: release-artifact")])

    def test_ci_preserves_production_signing_boundary(self):
        text = WORKFLOW.read_text()
        self.assertIn("github.event_name != 'pull_request'", text)
        self.assertIn("github.event.inputs.lane == 'release'", text)
        self.assertIn("startsWith(github.ref, 'refs/tags/admin/v')", text)
        self.assertIn('tags: ["admin/v*"]', text)
        self.assertNotIn("refs/tags/v'", text)
        self.assertEqual(2, text.count("actions/upload-artifact@"))
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
