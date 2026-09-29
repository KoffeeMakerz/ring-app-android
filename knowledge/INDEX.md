# PulseLoop Android Knowledge Index

Read this file first.

## Where to look

- Architecture and state flow: `knowledge/architecture.md`
- BLE protocols and packet formats: `knowledge/ble-protocols.md`
- Sensor paths (spot/live/history): `knowledge/sensors.md`
- Find Device and vibration paths: `knowledge/haptics.md`
- Device capability matrix: `knowledge/devices.md`
- Build/test/debug workflows: `knowledge/testing.md`
- Established decisions: `knowledge/decisions.md`
- High-value protocol discoveries: `knowledge/discoveries.md`

## Certainty labels

- `Status: source-confirmed` — directly supported by repository source.
- `Status: hardware-verified` — explicit real-device evidence exists in repo docs/captures.
- `Status: unverified` — plausible in source, but not confirmed on hardware.

## Highest-priority unknowns

1. Whether CRP R11 full Colmi UART exposure can depend on OS bond in specific firmware mixes.
2. RWfit JieLi unbind command parity vs legacy unbind behavior on real hardware.
3. CRP stress availability on R100 variants (source shows unanswered queries in documented captures).
4. Model-specific haptic behavior differences for Find Device commands (vibrate pattern, duration, intensity).
5. Which families can support repeatable or custom vibration patterns beyond fixed vendor commands.

## Fast orientation files

- BLE lifecycle: `app/src/main/java/com/pulseloop/ring/RingBLEClient.kt`
- Driver abstraction and advertisement matching: `app/src/main/java/com/pulseloop/ring/WearableDriver.kt`
- Sync orchestration: `app/src/main/java/com/pulseloop/service/RingSyncCoordinator.kt`
- Model catalog: `app/src/main/java/com/pulseloop/wearables/WearableModel.kt`
- Diagnostics export/masking: `app/src/main/java/com/pulseloop/diagnostics/DiagnosticsExporter.kt`, `app/src/main/java/com/pulseloop/diagnostics/DiagnosticsRedactor.kt`
