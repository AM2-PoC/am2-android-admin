# AM2 Android Admin

Canonical repository for the native AM2 Android admin application (`com.am2.admin`).

## Verification

```bash
python3 scripts/test_check_log_policy.py
python3 scripts/check_log_policy.py
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Production release remains gated on the production signing key and physical-device validation.

Source previously lived under `APK Admin_Native/` in `AM2-PoC/AM2-Legacy`; history was preserved with `git subtree split`.
