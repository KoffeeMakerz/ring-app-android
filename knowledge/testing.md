# Android Build, Test, and BLE Debugging

## Build and test commands

From repository root:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew jacocoTestDebugUnitTestReport
```

Optional release build (requires signing setup):

```bash
./gradlew assembleRelease
```

Status: source-confirmed

## Bluetooth permissions and prerequisites

Manifest paths:

- `app/src/main/AndroidManifest.xml`

Core permissions:

- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- Older Android: location permission for BLE scan behavior
- Foreground service permissions for health/workout execution paths

Status: source-confirmed

## Device prerequisites for protocol validation

1. Real ring with known family/firmware.
2. Fresh install and cleared prior pairing assumptions when validating discovery logic.
3. For bond-allowlisted models, ensure OS bond prompt flow is completed.
4. Keep screen active during first verification pass for easier log capture.

Status: source-confirmed

## Useful test suites

Representative protocol and integration tests:

- `app/src/test/java/com/pulseloop/ring/CRPProtocolTest.kt`
- `app/src/test/java/com/pulseloop/ring/CRPDecoderTest.kt`
- `app/src/test/java/com/pulseloop/ring/ColmiDecoderTest.kt`
- `app/src/test/java/com/pulseloop/ring/YCBTHealthRecordsTest.kt`
- `app/src/test/java/com/pulseloop/ring/YCBTHistoryTransferTest.kt`
- `app/src/test/java/com/pulseloop/ring/JringHistorySyncTest.kt`
- `app/src/test/java/com/pulseloop/ring/RWfitDriverTest.kt`
- `app/src/test/java/com/pulseloop/ring/AdvertisementMatcherTest.kt`

Status: source-confirmed

## Logging and diagnostics

Key files:

- `app/src/main/java/com/pulseloop/diagnostics/DiagnosticsExporter.kt`
- `app/src/main/java/com/pulseloop/diagnostics/DiagnosticsRedactor.kt`
- `app/src/main/java/com/pulseloop/diagnostics/LogcatCapture.kt`
- `app/src/main/java/com/pulseloop/data/entity/WearableLogEntity.kt`

Recommended workflow:

1. Reproduce with diagnostics capture enabled.
2. Export diagnostics JSON.
3. Inspect raw packet streams by family and direction.
4. Cross-check decoded event kinds vs expected parser path.

Status: source-confirmed

## BLE inspection strategy

1. Confirm advertised name, manufacturer payload, service UUIDs.
2. Confirm matched coordinator and driver.
3. Verify required CCCD subscriptions before first command write.
4. Trace startup writes and responses in order.
5. Verify history pagination and completion markers.

Status: source-confirmed

## Recommended process for onboarding a new ring

1. Capture advertisement packet details and identify likely family by UUID/manufacturer/name.
2. Attempt matching with existing coordinators in dry-run logging mode.
3. Validate connect + subscribe sequence and first startup roundtrip.
4. Verify battery + HR + steps baseline.
5. Validate one spot measurement and one history sync window.
6. Validate find-device/haptic command safely.
7. Add/adjust matching tests and parser tests before broad rollout.

Status: source-confirmed

## Licensing and attribution checks

- Preserve upstream LICENSE files and existing attribution obligations.
- Decompiled vendor artifacts used for reverse-engineering references do not remove licensing duties.

Status: source-confirmed
