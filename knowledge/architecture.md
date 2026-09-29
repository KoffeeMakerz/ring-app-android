# Android Architecture

## Application shape

PulseLoop Android uses a coordinator/driver/sync-engine BLE architecture with a shared event bus and Room persistence.

- BLE orchestration: `app/src/main/java/com/pulseloop/ring/RingBLEClient.kt`
- Driver contract: `app/src/main/java/com/pulseloop/ring/WearableDriver.kt`
- Sync orchestration: `app/src/main/java/com/pulseloop/service/RingSyncCoordinator.kt`
- Event bus and decoded event bridge: `app/src/main/java/com/pulseloop/ring/PulseEventBus.kt`, `app/src/main/java/com/pulseloop/ring/RingEventBridge.kt`
- Persistence subscriber and DAOs: `app/src/main/java/com/pulseloop/data/EventPersistenceSubscriber.kt`, `app/src/main/java/com/pulseloop/data/PulseLoopDatabase.kt`

Status: source-confirmed

## BLE module structure

`RingBLEClient` owns scan/connect/discover/subscribe/write sequencing and reconnect policy.

- Scanning entry: `startScanning()`
- Driver installation: `installDriver(...)`
- GATT callbacks: `onConnectionStateChange`, `onServicesDiscovered`, `onCharacteristicChanged`
- Reconnect watchdog and user disconnect semantics are handled inside the client.

`WearableCoordinator` implementations map advertisements to device families and instantiate the correct `WearableDriver` + `RingSyncEngine` pair.

Status: source-confirmed

## Coordinator registry and model resolution

Primary match path:

1. BLE advertisement enters `RingBLEClient`.
2. Coordinators are checked in registry order.
3. `AdvertisementMatcher` reconstructs manufacturer data with company ID prefixes on Android.
4. Chosen coordinator builds a driver and sync engine.

Registry order is behavior-critical for ambiguous names and fallback matches.

- Coordinator + matcher code: `app/src/main/java/com/pulseloop/ring/WearableDriver.kt`
- Model catalog and name patterns: `app/src/main/java/com/pulseloop/wearables/WearableModel.kt`

Status: source-confirmed

## State flow

Typical lifecycle:

1. UI triggers scan in `RingBLEClient`.
2. User selects ring, `connectTo(...)` resolves coordinator/model.
3. MTU request and service discovery run.
4. Required CCCD subscriptions are established (family-specific gate).
5. `RingConnectionState.CONNECTED` is emitted.
6. `RingSyncCoordinator` startup runs per-family initialization and history sync.
7. Decoded events pass through `PulseEventBus` to persistence and UI observers.

Status: source-confirmed

## Data storage

Room tables include device state, measurements, activity daily/buckets, sleep sessions/stages, raw packets/logs, and deletion tombstones.

- Database root: `app/src/main/java/com/pulseloop/data/PulseLoopDatabase.kt`
- Measurement + tombstone handling: `MeasurementDeletion.kt`, `ActivityBucketDeletion.kt`
- Sleep reconciliation and segmentation: `SleepSegmentation.kt`

Status: source-confirmed

## UI interaction with device services

Compose screens consume `RingBLEClient` state and event-backed stores.

- Ring controls invoke `RingSyncCoordinator` operations (spot measurements, find device, workout stream)
- Persistence subscriber keeps longitudinal metrics independent of view lifecycle.

Status: source-confirmed

## Dependencies that matter for BLE behavior

- Android Bluetooth stack: `BluetoothGatt`, `BluetoothGattCharacteristic`, CCCD semantics.
- Kotlin coroutines and shared flows for serialized write queues and event fanout.
- Room for idempotent history writes and recovery after reconnects.

Status: source-confirmed

## Cross-platform differences (Android vs iOS)

- Android requires explicit handling for split manufacturer data fields; iOS receives company ID in raw block.
- Android uses an OS bond allowlist for selected Colmi/Yawell models; iOS does not expose the same pairing model.
- Android reconnect and watchdog behavior is tuned for platform-specific zombie link scenarios.

Status: source-confirmed
