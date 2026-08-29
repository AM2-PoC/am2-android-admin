#!/usr/bin/env python3
"""Admin must be installable over its own previous build.

Android permits an install over an existing app only when the new package
carries the *same* signature. It does not care whether the key is called debug
or release -- a debug keystore holds a real private key. What matters is
continuity, and this module has never had any: every APK is built on a runner
that generates a debug key and discards it, so no Admin build can be installed
over the one before it. Each round of field testing costs an operator their
local state, and the Client repository already fixed exactly this.

Two keys, deliberately not one.

The staging key has to live in CI to be of any use. The upload key must not:
collapsing them would put the application's permanent Play identity on every
runner that builds a staging APK. Losing the upload key is recoverable through
Play; losing signature continuity for every sideloaded handset is not.

Unconfigured stays legitimate. A developer without either key still builds and
runs. What must never happen is *half* configured -- hand Gradle a keystore
path with no password and it attaches no signing config at all, so the release
artifact comes out signed with the debug key: it builds, it installs, and it is
not a release. Nothing in the output says otherwise.

Assertions are booleans so a failure prints its reason, not the file.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle.kts"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"

RELEASE_PROPS = (
    "AM2_KEYSTORE_FILE", "AM2_KEYSTORE_PASSWORD",
    "AM2_KEY_ALIAS", "AM2_KEY_PASSWORD",
)
STAGING_PROPS = (
    "AM2_STAGING_KEYSTORE_FILE", "AM2_STAGING_KEYSTORE_PASSWORD",
    "AM2_STAGING_KEY_ALIAS", "AM2_STAGING_KEY_PASSWORD",
)


class SigningContinuityContractTest(unittest.TestCase):
    def setUp(self):
        self.gradle = GRADLE.read_text()
        self.workflow = WORKFLOW.read_text()

    def test_release_signing_properties_are_read(self):
        missing = [p for p in RELEASE_PROPS if p not in self.gradle]
        self.assertEqual(
            missing, [],
            "the release key cannot reach Gradle; without these the production "
            f"artifact is unsigned or debug-signed forever: {missing}",
        )

    def test_staging_signing_properties_are_read(self):
        missing = [p for p in STAGING_PROPS if p not in self.gradle]
        self.assertEqual(
            missing, [],
            "no staging key means every runner invents its own, and no Admin "
            f"build can be installed over the one before it: {missing}",
        )

    def test_half_configured_signing_stops_the_build(self):
        self.assertIn(
            "half configured", self.gradle,
            "a keystore path with no password attaches no signing config at all "
            "and the artifact comes out debug-signed while looking like a "
            "release; that state has to fail loudly where it is set",
        )

    def test_release_never_falls_back_to_the_debug_config(self):
        release_block = self.gradle[self.gradle.index("buildTypes {"):]
        self.assertRegex(
            release_block,
            r"signingConfig\s*=\s*if\s*\(signingConfigured\)",
            "release must take the release config or none; silently using debug "
            "produces something that installs and is not a release",
        )

    def test_staging_key_signs_the_staging_flavour(self):
        # staging is a product flavour on the debug build type, so
        # assembleStagingDebug signs with the debug config.
        block = self.gradle[self.gradle.index("signingConfigs {"):]
        self.assertRegex(
            block, r'getByName\("debug"\)',
            "the staging key must override the debug config, because "
            "assembleStagingDebug is what reaches a handset",
        )

    def test_ci_restores_both_keystores_from_secrets(self):
        for secret in ("AM2_STAGING_KEYSTORE_BASE64", "AM2_UPLOAD_KEYSTORE_BASE64"):
            self.assertIn(
                secret, self.workflow,
                f"{secret} is never read, so the key never reaches the build",
            )

    def test_passwords_do_not_travel_on_the_command_line(self):
        # -P puts the value in the process list, readable by anything else on
        # the machine. ORG_GRADLE_PROJECT_* is read as a project property too.
        for prop in RELEASE_PROPS[1:] + STAGING_PROPS[1:]:
            self.assertNotRegex(
                self.workflow, rf"-P{prop}=",
                f"{prop} is passed with -P and lands in the process list",
            )

    def test_the_release_lane_is_gated_by_the_workflow_not_an_environment(self):
        # `environment: android-release` carried a branch policy allowing main
        # and v*, and the job condition accepted any ref for lane=release -- so
        # the policy was the only thing stopping a release build, the one job
        # that decrypts the upload key, from running off an arbitrary branch.
        # GitHub ignores environment protection rules outright on a private
        # repository under Free: no error, no warning. The guard moves into the
        # condition, which is read from the workflow file at the commit.
        header = self.workflow[self.workflow.index("  release-artifact:"):]
        header = header[:header.index("\n    steps:")]
        self.assertNotIn("environment:", header,
                         "a declaration that is ignored reads as a guard and is not one")
        self.assertIn("refs/heads/main", header,
                      "a dispatched release must be restricted to main")

    def test_the_release_lane_proves_the_signer_it_declares(self):
        """A hand-set digest is a second copy of a fact the key already carries.

        The release lane takes AM2_APPROVED_SIGNER_SHA256 from a repository
        variable and checks that it is sixty-four hex characters. Shape, not
        truth: a value of the right shape and the wrong content passes, and the
        build that ships trusts a signer that never signed anything here.

        The consequence is worse in production than anywhere else. The digest
        is compiled into the APK, so a handset carrying a wrong one refuses
        every update it will ever be offered, and the only repair is a manual
        install on each unit -- which is the cost the update channel exists to
        avoid.

        apksigner already reads what actually signed the artifact. Comparing
        the two is the whole check, and staging has done it since the day its
        own signer was derived rather than declared.
        """
        block = self.workflow[self.workflow.index("release-artifact:"):]
        self.assertIn(
            "signer-metadata.txt", block,
            "the release lane does not record what signed the artifact",
        )
        self.assertRegex(
            block, r"AM2_APPROVED_SIGNER_SHA256[\s\S]{0,3000}?SIGNED_BY",
            "the declared signer is never compared with the one that signed, so "
            "a wrong variable ships and is only discovered on a handset",
        )

    def test_the_release_lane_refuses_to_ship_unsigned(self):
        self.assertNotIn(
            "am2-admin-production-unsigned", self.workflow,
            "the release lane still publishes an unsigned artifact; Play "
            "refuses it and a sideload cannot be updated in place",
        )

    def test_the_version_name_is_declared_once_where_ci_can_read_it(self):
        # It was the literal "1.1.0" beside a versionCode that already came from
        # CI, so nothing tied the version a handset reports to the version the
        # panel announces in admin_version.json. They matched by hand.
        #
        # The manifest generator has to read this string. A quoted literal in a
        # Kotlin build script is not something another job can read, which is
        # why the assertion is that it is NOT one.
        self.assertNotRegex(
            self.gradle, r'versionName\s*=\s*"',
            "versionName is a literal; CI cannot read it to write the manifest",
        )
        declared = (ROOT / "app/version.properties")
        self.assertTrue(declared.is_file(), "app/version.properties is missing")
        self.assertRegex(
            declared.read_text(), re.compile(r"^versionName=\S+$", re.MULTILINE),
            "version.properties must declare exactly one versionName",
        )

    def test_the_version_name_carries_the_build_it_was_made_from(self):
        # Two builds of one release read identically. version.properties holds
        # "1.1.0" and a human leaves it there for a release or ten, so build 51
        # and build 52 both call themselves 1.1.0-staging and an operator
        # reading a version off a handset cannot say which one it is.
        #
        # Semantic Versioning has a slot for exactly this: everything after a
        # '+' is build metadata, it identifies the artifact, and it MUST be
        # ignored when comparing versions. Putting the build in the PATCH
        # component instead -- 1.1.52 -- would claim fifty-two backward
        # compatible bug fixes, which is what that component means.
        #
        # Every lane carries it, production included, because this app is
        # sideload-only: docs/explanation/which-key-signs-what.md gives the Play
        # listing to the Client alone, so there is no store page to keep tidy
        # and every APK that reaches a handset should be able to name itself.
        suffixes = re.findall(r'versionNameSuffix\s*=\s*"([^"]*)"', self.gradle)
        self.assertEqual(
            len(suffixes), 3,
            f"expected one versionNameSuffix per flavour, found {len(suffixes)}",
        )
        for suffix in suffixes:
            self.assertIn(
                "+$", suffix,
                f"the {suffix!r} lane produces a version name that names no build",
            )

        declared = (ROOT / "app/version.properties").read_text()
        self.assertNotRegex(
            declared, r"versionName=.*\+",
            "the build belongs to the artifact, not to the release a human declared",
        )

    def test_an_update_is_never_offered_to_a_build_that_cannot_install_it(self):
        """SELF_UPDATE_ENABLED is the first line of UpdateVerifier.verify().

        On staging it is false, so verify() returns false before it has looked
        at the file. The settings screen checks anyway, offers the update,
        downloads the whole APK, and then reports "identitas APK tidak valid"
        -- about an APK whose identity is correct. The build simply is not
        permitted to install anything, and says so by blaming the artifact.

        It surfaced only once the staging update channel started answering.
        Before that the check threw "metadata update tidak tersedia" and the
        dialog was never reached, so a path that had always been broken looked
        like a path that worked.
        """
        settings = (ROOT / "app/src/main/java/com/am2/admin/ui/settings/SettingsActivity.kt").read_text()
        check = settings[settings.index("private fun checkUpdate()"):]
        check = check[:check.index("\n    private fun")]
        # The guard, not the word. The comment above it says SELF_UPDATE_ENABLED
        # too, and an earlier version of this assertion passed against a build
        # where the guard had been deleted and only the prose remained.
        self.assertRegex(
            check, r"if\s*\(!\s*BuildConfig\.SELF_UPDATE_ENABLED\s*\)",
            "an update is offered without asking whether this build may install "
            "one, so the refusal arrives after the download and blames the APK",
        )

    def test_staging_may_install_what_staging_publishes(self):
        # Otherwise the update path can only ever be exercised in production,
        # which is the one place a first attempt should not happen.
        block = self.gradle[self.gradle.index('create("staging")'):]
        block = block[:block.index("\n        }")]
        self.assertIn(
            'buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "true")', block,
            "staging cannot install its own updates, so nothing exercises the "
            "update path until production does it for the first time",
        )

    def test_the_staging_lane_bakes_in_the_signer_it_will_be_asked_to_trust(self):
        # The second blocker, underneath the first: even permitted, the
        # approved digest is the empty string its default provides, so the
        # signer check refuses. Confirmed against the shipped APK -- 664f8217..
        # appears nowhere in its classes.dex.
        job = self.workflow[self.workflow.index("staging-artifact:"):]
        self.assertIn(
            "AM2_APPROVED_SIGNER_SHA256", job,
            "the staging build is never told which signer to trust",
        )
        self.assertRegex(
            self.workflow, r"keytool[^\n]*-list",
            "the trusted signer is stated rather than read off the signing key",
        )

    def test_the_version_code_comes_from_ci(self):
        # It was the literal 2 in every Admin APK ever produced. The device
        # decides an update exists by comparing version codes, so an unchanging
        # one makes the channel permanently answer "already current" -- and
        # leaves neither end able to name the build actually installed.
        self.assertNotRegex(
            self.gradle, r"versionCode\s*=\s*\d+",
            "versionCode is a literal; every build claims to be the same one",
        )
        self.assertIn(
            "AM2_VERSION_CODE", self.gradle,
            "the build must take its identity from CI",
        )
        for lane in ("assembleStagingDebug", "assembleProductionRelease"):
            idx = self.workflow.index(lane)
            window = self.workflow[max(0, idx - 400):idx + 200]
            self.assertIn(
                "AM2_VERSION_CODE", window,
                f"{lane} does not receive a version code, so it falls back to 1",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
