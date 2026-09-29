# Android Haptics and Find Device

This file tracks ring-side feedback commands and current evidence level.

## Entry points

- UX action path: `app/src/main/java/com/pulseloop/service/RingSyncCoordinator.kt` (`findDevice()`)
- Family execution path: per-family `RingSyncEngine.findDevice()` and encoder/protocol commands

Status: source-confirmed

## Family command mapping

### Colmi / QRing

- Path: `RingSyncCoordinator.findDevice()` -> `ColmiSyncEngine.findDevice()` -> `ColmiEncoder.findDevice()`
- Command bytes: `0x50 0x55 0xAA`
- Evidence:
  - Command path exists in source.
  - Physical output pattern (vibration duration/intensity) is not parameterized in app.

Status: source-confirmed

### CRP (Moyoung)

- Path: `RingSyncCoordinator.findDevice()` -> `CRPSyncEngine.findDevice()` -> `CRPProtocol.findDevice(enable)`
- Frame intent: group `0x09`, cmd `0x02`, payload `[0x01]` or `[0x00]` for enable/disable
- Evidence:
  - Command framing exists.
  - Device-specific motor behavior is not configurable in source.

Status: source-confirmed

### YCBT

- Path: `RingSyncCoordinator.findDevice()` -> `YCBTSyncEngine.findDevice()` -> `YCBTEncoder.findDevice()`
- Command family: `0x03 0x00` app control find-device variant
- Evidence:
  - Source path exists.
  - No app-level knobs for intensity/repeat.

Status: source-confirmed

### LuckRing

- Path: `RingSyncCoordinator.findDevice()` -> `LuckRingSyncEngine.findDevice()` -> `LuckRingEncoder.findDevice()`
- Command family: dedicated find-device data type (`0x0B` family in encoder/protocol naming)
- Evidence:
  - Source path exists.
  - Pattern and pulse semantics are ring firmware-defined.

Status: source-confirmed

### RWfit

- Path exists but family behavior is currently limited/no-op in active app support paths.

Status: unverified

### jring

- Capability is represented in family support metadata; implementation behavior depends on model/firmware variant.

Status: unverified

## Ring alarms and reminder-style motor behavior

- The app has protocol-level settings for measurements/notifications and find-device, but does not expose a generalized programmable haptic pattern API.
- No cross-family abstraction currently defines fields for explicit vibration intensity, pulse width, repeat count, or schedule for ad hoc haptic patterns.

Status: source-confirmed

## Notifications sent to the ring that may cause feedback

- Family-specific message/reminder/notification commands exist in multiple protocols.
- Not all notification opcodes are mapped to verified physical outputs.

Status: source-confirmed

## Custom vibration patterns

Current state:

- No app-level, cross-family custom vibration pattern builder is implemented.
- Existing find-device/reminder commands appear firmware-defined and mostly fixed-format.

Status: unverified

## Cross-platform difference note

- iOS includes richer UI-side haptic interactions for phone feedback, but ring-side command semantics are broadly parallel.
- Android and iOS both rely on ring firmware behavior for actual motor profile after command delivery.

Status: source-confirmed

## Haptic capability questions to verify on physical hardware

1. For each family, does find-device always map to vibration, or can it be tone/LED only on some firmware?
2. Can repeated find-device commands be chained safely to create repeatable patterns without disconnect risk?
3. Are there hidden command payload variants that alter vibration duration or intensity on YCBT/Colmi/CRP?
4. Does CRP `findDevice(enable=false)` reliably stop an in-progress motor sequence on all tested rings?
5. For LuckRing and RWfit, is there any vendor-supported pattern API beyond fixed alerts?
6. Are any reminder/alarm commands equivalent to programmable motor patterns?
