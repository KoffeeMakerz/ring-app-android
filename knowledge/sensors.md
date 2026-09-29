# Android Sensor Paths

This file tracks how measurements start, stop, parse, and persist.

## Shared flow

1. Sync coordinator triggers family-specific start command.
2. Driver ingests notify data and decoder emits `RingDecodedEvent`.
3. Event bridge publishes `PulseEvent`.
4. `EventPersistenceSubscriber` writes to Room and updates derived state.

Core paths:

- `app/src/main/java/com/pulseloop/service/RingSyncCoordinator.kt`
- `app/src/main/java/com/pulseloop/ring/RingEventBridge.kt`
- `app/src/main/java/com/pulseloop/data/EventPersistenceSubscriber.kt`

Status: source-confirmed

## Heart rate (HR)

Start/stop:

- Triggered from `RingSyncCoordinator.measureHeartRate()`.
- Family engine sends protocol-specific start and stop commands.

Realtime/history:

- Spot mode and workout streams publish live HR samples.
- History imports are upserted with deterministic IDs.

Notable behavior:

- Spot sample settling uses dedicated window logic before storing one measurement.

Status: source-confirmed

## HRV

Start/stop:

- Family-specific commands where available; often history-dominant.

Realtime/history:

- Usually imported from history timing frames.
- May be absent or unverified for specific models despite protocol stubs.

Status: source-confirmed

## SpO2

Start/stop:

- `RingSyncCoordinator.measureSpO2()` invokes engine-specific commands.

Realtime/history:

- Spot measurements for supported families.
- All-day/history depends on family and monitor-state support.

Model limitations:

- Some models/families show sparse or absent SpO2 history in documented captures.

Status: source-confirmed

## Stress

Start/stop:

- Supported in select families via spot or all-day protocols.

History and derivation:

- Real stress history is preferred when available.
- Derived stress from HRV exists when hardware stress appears unavailable.

Status: source-confirmed

## Temperature

Start/stop:

- Spot temperature in families with direct command support.

History format:

- CRP timing history is frame-based with multi-frame day pagination.
- Little-endian tenths-of-degree values are parsed in CRP/YCBT paths.

Status: source-confirmed

## Sleep

Ingest:

- Family decoders emit staged sleep records.
- Reconciliation merges and upserts by waking-day/session rules.

Important behavior:

- Connect-time bulk deletion is intentionally disabled; history preservation is mandatory.

Status: source-confirmed

## Steps and activity

Realtime/history:

- Device pushes and history pulls both feed activity stores.
- Daily ratchet logic and bucket tombstones prevent deleted buckets from reappearing.

Status: source-confirmed

## Battery

Paths:

- Family-specific battery reads/notifications plus periodic checks.
- Persisted in battery samples and surfaced to UI state.

Status: source-confirmed

## Raw PPG and accelerometer

Raw sensor streams:

- No stable, cross-family raw PPG or 3-axis accelerometer pipeline is exposed as primary app data paths.

Status: unverified

## Cross-platform differences

- Android has explicit reconnect/watchdog and bond interactions that can affect sensor startup success.
- iOS has the same family-level intent but different CoreBluetooth lifecycle constraints.

Status: source-confirmed
