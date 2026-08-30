#!/usr/bin/env python3
"""Signing out ends the session, and an ended session is recognised as one.

Two faults on the same path, reported from the field as "GAGAL memperbarui
fitur" after the app had been left alone for a few hours.

The server keeps a PHP session for session.gc_maxlifetime, which is 1440
seconds. After that it is gone. The app still holds the cookie and the CSRF
token in its preferences, still answers true to isLoggedIn(), and sends both.
am2_csrf_require() then finds no stored token, answers 403, and writes plain
text unless the request said it accepts JSON -- which this client never did. So
Retrofit could not parse the refusal either, and every switch the operator
touched failed with a message about the feature rather than about the session.

And signing out was not durable:

    fun logout() { prefs.edit().clear().apply() }

apply() writes in the background, and the next line finished the task. A write
that had not landed left the session behind, so an administrator who signed out
was still signed in at the next launch. The client app fought exactly this and
settled it with commit() plus a caller that refuses to move on when the write
fails.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SESSION = ROOT / "app/src/main/java/com/am2/admin/data/pref/SessionManager.kt"
BASE = ROOT / "app/src/main/java/com/am2/admin/ui/BaseActivity.kt"
CLIENT = ROOT / "app/src/main/java/com/am2/admin/data/api/RetrofitClient.kt"


def code(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


class SessionLifecycleContractTest(unittest.TestCase):
    def setUp(self):
        self.session = code(SESSION.read_text(encoding="utf-8"))
        self.base = code(BASE.read_text(encoding="utf-8"))
        self.client = code(CLIENT.read_text(encoding="utf-8"))

    def test_signing_out_is_durable_before_the_task_ends(self):
        # Whether it is a block or a single expression.
        logout = self.session[self.session.index("fun logout("):]
        cut = min(x for x in (logout.find("\n    }"), logout.find("\n    fun "), len(logout))
                  if x > 0)
        logout = logout[:cut]
        self.assertIn(
            ".commit()", logout,
            "logout writes with apply(), so finishing the task can outrun it and "
            "leave the administrator signed in",
        )
        self.assertNotIn(
            ".apply()", logout,
            "logout still has an asynchronous write on the path that ends the task",
        )

    def test_a_failed_sign_out_does_not_pretend_to_have_worked(self):
        # The answer has to be read, whichever way round it is written.
        self.assertRegex(
            self.base,
            r"(if|when)\s*\(\s*!?\s*sessionManager\.logout\(\)|"
            r"val\s+\w+\s*=\s*sessionManager\.logout\(\)",
            "nothing checks whether signing out actually landed",
        )

    def test_leaving_for_the_login_screen_clears_the_task(self):
        self.assertIn(
            "FLAG_ACTIVITY_CLEAR_TASK", self.base,
            "the login screen is launched into the task that is being finished, "
            "so which of the two wins is not defined",
        )

    def test_the_client_says_it_can_read_a_json_refusal(self):
        self.assertRegex(
            self.client, r'"Accept"[\s\S]{0,60}?application/json',
            "the server writes plain text to a client that does not ask for "
            "JSON, so a refusal arrives as an unparseable body",
        )

    def test_an_expired_session_is_recognised_rather_than_reported_as_a_feature(self):
        self.assertRegex(
            self.client, r"403",
            "nothing notices the status the server uses to say the session is gone",
        )
        self.assertRegex(
            self.client, r"(sessionExpired|SessionExpiry|onSessionExpired)",
            "a 403 is not turned into anything the screens can act on",
        )
        # And something must actually listen, from every screen, or the
        # announcement is a fix nobody hears.
        self.assertRegex(
            self.base,
            r"override fun onCreate[\s\S]{0,300}?observeSessionExpiry\(\)",
            "the expiry observer is never attached, so no screen acts on it",
        )
        self.assertRegex(
            self.base, r"SessionExpiry\.expired\.observe[\s\S]{0,400}?goToLogin\(\)",
            "an expired session is noticed and the operator is left on the screen "
            "that cannot work",
        )


if __name__ == "__main__":
    unittest.main()
