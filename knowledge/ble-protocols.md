# Android BLE Protocols

This file summarizes family-specific BLE wiring and framing implemented in source.

## Family overview

- jring — `Status: source-confirmed`
- YCBT — `Status: source-confirmed`
- Colmi/QRing — `Status: source-confirmed`
- CRP (Moyoung/Da Rings) — `Status: source-confirmed`
- LuckRing — `Status: source-confirmed`
- RWfit (Legacy + JieLi) — `Status: source-confirmed`

## Discovery and family assignment

Main code paths:

- Coordinator matching and build: `app/src/main/java/com/pulseloop/ring/WearableDriver.kt`
- Scan/connect orchestration: `app/src/main/java/com/pulseloop/ring/RingBLEClient.kt`
- Model catalog patterns: `app/src/main/java/com/pulseloop/wearables/WearableModel.kt`

Android-specific advertisement note:

- `AdvertisementMatcher` restores company ID to manufacturer payload prefixes before coordinator matching.
- This prevents false negatives relative to iOS CoreBluetooth payload layout.

Status: source-confirmed

## CRP protocol

Key files:

- `app/src/main/java/com/pulseloop/ring/CRPProtocol.kt`
- `app/src/main/java/com/pulseloop/ring/CRPDriver.kt`
- `app/src/main/java/com/pulseloop/ring/CRPDecoder.kt`
- `app/src/main/java/com/pulseloop/ring/CRPSyncEngine.kt`

GATT topology:

- Service: `fdda`
- Write: `fdd2`
- Notify: `fdd3` (command replies), plus additional family channels where available

Frame layout:

- Byte 0: `0xFD`
- Byte 1: `0xDA`
- Byte 2: `0x10` variant/length-high flag bit
- Byte 3: payload length low bits
- Byte 4: group
- Byte 5: command
- Bytes 6..N: payload

Representative commands:

- `1/9` spot heart rate
- `1/10` spot HRV
- `1/11` spot SpO2
- `1/14` spot stress
- `1/32` spot temperature
- `2/15,2/16,2/17,2/47,2/22` all-day history queries (family-specific)
- `3/3` firmware string
- `3/7` wear state
- `9/2` find device

Protocol quirks:

- Frame reassembly is required for chunked notifications.
- Startup runs timing config and history requests over the same command channel.

Status: source-confirmed

## Colmi/QRing protocol

Key files:

- `app/src/main/java/com/pulseloop/ring/ColmiProtocol.kt`
- `app/src/main/java/com/pulseloop/ring/ColmiDriver.kt`
- `app/src/main/java/com/pulseloop/ring/ColmiDecoder.kt`
- `app/src/main/java/com/pulseloop/ring/ColmiSyncEngine.kt`

GATT topology (two-profile variants appear in source):

- Services include `6e40fff0` and `de5bf728` variants.
- Write and notify characteristics follow family-specific UART-style channels.

Frame layout (normal command path):

- 16-byte command blocks
- Content bytes followed by checksum byte (sum of content bytes modulo 256)

Representative commands:

- `0x01` set date/time
- `0x03` battery
- `0x16` auto HR interval settings
- `0x69` spot measurement start
- `0x6A` measurement stop
- `0x73` spontaneous device events
- `0x77`/`0x78` sport session + telemetry
- `0xBC` big-data history requests

Quirks:

- Some models require full field population for all-day monitor settings.
- Bond behavior for selected models is intentionally allowlisted by model, not capability bit alone.

Status: source-confirmed

## YCBT protocol

Key files:

- `app/src/main/java/com/pulseloop/ring/YCBTProtocol.kt`
- `app/src/main/java/com/pulseloop/ring/YCBTDriver.kt`
- `app/src/main/java/com/pulseloop/ring/YCBTDecoder.kt`
- `app/src/main/java/com/pulseloop/ring/YCBTSyncEngine.kt`

GATT topology:

- Service: `be940000-7333-be46-b7ae-689e71722bd5`
- Command channel: `...0001`
- Stream/history channel: `...0003`

Frame layout:

- Byte 0: type
- Byte 1: command
- Byte 2-3: total length (little-endian)
- Payload
- Final 2 bytes: CRC16/CCITT-FALSE

Representative commands:

- `0x02/0x01` capability bitmap query
- `0x03/0x2F` spot measurement mode start
- `0x04/0x0E` measurement completion verdict
- `0x05/*` and `0x06/*` history/stream transfer families

Quirks:

- CRC and terminal-block validation are essential for robust history imports.
- Completion-verdict semantics affect spot-measure settle behavior.

Status: source-confirmed

## jring protocol

Key files:

- `app/src/main/java/com/pulseloop/ring/JringDriver.kt`
- `app/src/main/java/com/pulseloop/ring/JringHistorySync.kt`

GATT topology:

- Service `000056ff` family with UART-like channels

History behavior:

- Day offset semantics are load-bearing (`0x10` request byte 1).
- One-day-at-a-time sequencing plus settle windows prevent transfer truncation.

Status: source-confirmed

## LuckRing protocol

Key files:

- `app/src/main/java/com/pulseloop/ring/LuckRingProtocol.kt`
- `app/src/main/java/com/pulseloop/ring/LuckRingDriver.kt`
- `app/src/main/java/com/pulseloop/ring/LuckRingEncoder.kt`
- `app/src/main/java/com/pulseloop/ring/LuckRingDecoder.kt`

Topology and framing:

- Service includes `f618` family identifiers.
- Fixed-size packet chunks with family-specific pagination rules.

Notable commands:

- Pairing completion and find-device control are explicit command paths.

Status: source-confirmed

## RWfit protocol

Key files:

- `app/src/main/java/com/pulseloop/ring/RWfitProtocol.kt`
- `app/src/main/java/com/pulseloop/ring/RWfitDriver.kt`
- `app/src/main/java/com/pulseloop/ring/RWfitEncoder.kt`
- `app/src/main/java/com/pulseloop/ring/RWfitDecoder.kt`

Two variants in source:

1. Legacy framing with XOR-style checksum behavior.
2. JieLi framing with CRC16/ARC behavior.

Driver selection:

- Chosen after service discovery by observed service topology.

Status: source-confirmed

## Cross-platform notes

- Android and iOS share family coverage and broad command intent.
- Android contains explicit advertisement manufacturer reconstruction logic not needed on iOS.
- Do not assume OS-level pairing/bond behavior is transferable between platforms.

Status: source-confirmed
