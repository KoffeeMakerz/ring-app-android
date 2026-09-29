# Android Device Capability Matrix

Legend:

- `Y` implemented in source
- `N` not implemented in source
- `?` uncertain or partial
- Status labels describe certainty of capability behavior

## Family matrix

| Family | Connect | Battery | HR | HRV | PPG raw | ACC raw | SpO2 | Stress | Sleep | Find Device | Alarms/Reminder cmds | Vibration | Custom vibration |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| jring | Y | Y | Y | ? | N | N | Y | N | Y | ? | ? | ? | N |
| YCBT / TK5 / SmartHealth | Y | Y | Y | Y | N | N | Y | Y | Y | Y | Y | ? | N |
| Colmi / QRing | Y | Y | Y | Y | N | N | Y | Y | Y | Y | Y | ? | N |
| CRP (R11/R100 family) | Y | Y | Y | Y | N | N | Y | ? | Y | Y | Y | ? | N |
| LuckRing | Y | Y | Y | Y | N | N | Y | Y | Y | Y | Y | ? | N |
| RWfit | Y | Y | Y | ? | N | N | ? | ? | Y | ? | ? | ? | N |

## Notes by capability

- `PPG raw` and `ACC raw`: no stable cross-family pipeline is exposed to app-level measurement storage.
  - Status: unverified
- `Vibration`: command paths exist for find-device/reminder in several families, but physical motor profile is firmware-specific.
  - Status: source-confirmed for command availability, unverified for pattern details
- `Custom vibration`: no source-confirmed API for user-defined intensity/duration/repeat sequences.
  - Status: unverified

## Model-level highlights

- `COLMI_R09`, `COLMI_R11`, `YAWELL_R11` are explicitly marked for OS bond requirement in model metadata.
  - Source: `app/src/main/java/com/pulseloop/wearables/WearableModel.kt`
  - Status: source-confirmed
- R100 stress behavior appears absent/inconsistent in documented capture notes despite protocol support surfaces.
  - Status: hardware-verified for specific reported captures, unverified as universal rule

## Cross-platform difference note

- Family coverage is broadly aligned with iOS, but Android has platform-specific scan payload normalization and bond policy that affect practical capability reliability.

Status: source-confirmed
