# Android Architectural Decisions

This log records decisions already visible in code/docs. It is not a speculative roadmap.

## 2026-07-15 to 2026-07-19 — OS bond is model allowlist, not support bit blanket

Context:

- Vendor behavior bonds whenever capability bit reports BLE pairing support.
- This caused UX regressions on models that do not need OS bond.

Decision:

- Require `requiresOsBond` model allowlist plus capability checks.

Reasoning:

- Preserve stable UX and avoid unnecessary OS pairing prompts.

Consequences:

- New models needing OS bond must be added intentionally by name.

Status: source-confirmed

## 2026-07 — Do not delete stored history on connect

Context:

- Connect-time clears caused repeated user data loss patterns.

Decision:

- Preserve stored history on connect; rely on idempotent upsert/reconcile.

Reasoning:

- Ring buffers are limited and cannot reconstruct full historical store.

Consequences:

- Retention/deletion policy must be explicit and separate from connection lifecycle.

Status: source-confirmed

## 2026-07 — Android manufacturer data normalization before matching

Context:

- Android BLE API separates company ID from manufacturer payload bytes.

Decision:

- Reconstruct manufacturer bytes with company ID prefix via `AdvertisementMatcher` before coordinator matching.

Reasoning:

- Prevent dead fallback matchers and cross-platform mismatch.

Consequences:

- New manufacturer-based rules must use on-air layout assumptions.

Status: source-confirmed

## 2026-07 — Spot measurement completion should honor ring verdict semantics

Context:

- Some families log spot measurement into their own history and provide completion outcomes.

Decision:

- Reconcile toward ring-owned history copy where appropriate rather than forcing app-derived final values.

Reasoning:

- Avoid duplicate or contradictory measurements and align with device truth source.

Consequences:

- Deletion/tombstone logic and spot-history reconciliation stay coupled to protocol semantics.

Status: source-confirmed

## Template for future decisions

### YYYY-MM-DD — Decision title

Context:

Decision:

Reasoning:

Consequences:
