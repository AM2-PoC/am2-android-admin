# AM2 Android Admin

Android administration application (`com.am2.admin`).

## Requirements

- JDK 17
- Android SDK 35
- Build Tools 35.0.0

## Verify

```bash
python3 scripts/test_check_log_policy.py
python3 scripts/check_log_policy.py
./gradlew --no-daemon :app:testDevDebugUnitTest :app:assembleDevDebug
```

Compatibility matrices and signed artifact generation run through approved GitHub Actions lanes. Production publication additionally requires signer continuity, install-over verification against the active release, affected physical-device acceptance, explicit approval, and rollback evidence. Do not run Android builds or dependency resolution on a runtime host.
