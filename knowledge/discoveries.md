# Android Technical Discoveries

This file stores durable, expensive-to-rediscover findings.

## Android manufacturer data must be reconstructed for coordinator fallback matching

Finding:

- Android scan API splits manufacturer company ID from manufacturer payload bytes.
- Matching logic that assumes iOS-style raw payload fails unless company ID is restored.

Where:

- `app/src/main/java/com/pulseloop/ring/WearableDriver.kt` (`AdvertisementMatcher`)

Why it matters:

- Without reconstruction, manufacturer-prefix fallback matchers can silently never trigger.

Status: source-confirmed

## Colmi/Yawell OS bond behavior must remain model-curated

Finding:

- Blanket bonding by capability bit fixed one model and regressed others.

Where:

- `app/src/main/java/com/pulseloop/wearables/WearableModel.kt`
- `docs/qring-ble-adoption.md`

Why it matters:

- Bond policy is a UX and stability control, not a one-bit protocol switch.

Status: source-confirmed

## jring history day byte is day offset, not day count

Finding:

- Misinterpreting request byte semantics prevents requesting current-day sleep in some flows.

Where:

- `app/src/main/java/com/pulseloop/ring/JringHistorySync.kt`
- `AGENTS.md` issue #73 section

Why it matters:

- Wrong request semantics yield partial history that appears plausible at a glance.

Status: source-confirmed

## YCBT history transfer integrity should trust terminal byte count + CRC, not header packet estimate alone

Finding:

- Header-estimated packet counts can disagree with actual transfer while CRC and payload are valid.

Where:

- `app/src/main/java/com/pulseloop/ring/YCBTHistoryTransfer.kt`
- `AGENTS.md` issue #69 section

Why it matters:

- Strict header-count checks can drop complete records, including SpO2-containing bundles.

Status: source-confirmed

## CRP wear-state signals can explain apparent spot measurement failures

Finding:

- Wear/contact state frames can precede no-reading outcomes and should gate error messaging.

Where:

- `app/src/main/java/com/pulseloop/ring/CRPDecoder.kt`
- `AGENTS.md` issue #29 section

Why it matters:

- Distinguishes protocol failure from contact failure and shortens failed-measure UX.

Status: hardware-verified

## Deleting regenerable metrics requires tombstones, not only row deletes

Finding:

- History rows can reappear on next sync unless suppression is persisted as protocol-aware tombstones.

Where:

- `app/src/main/java/com/pulseloop/data/MeasurementDeletion.kt`
- `app/src/main/java/com/pulseloop/data/ActivityBucketDeletion.kt`

Why it matters:

- Prevents user-facing reappearance of deleted measurements/activity buckets.

Status: source-confirmed
