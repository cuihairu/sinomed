# Pharmacy and Dispensing Flow (Policy Design Draft)

> Status: **Implemented (2026-10-11)**. Two deviations from the finalized policy: ① on the auto-derived path (decoction service / paste 「已取」) the flow entry's operator is left empty and the note records the source (the finalized text said "take the acting operator") — the automatic path has no human confirmation, so leaving it empty is more honest; ② workbench search was merged into a single `customerKeyword` parameter (name and phone matched by the same fuzzy match). Test landing points match section 5 (`DispenseTest` 5 cases + `DispenseHttpTest` 1 case).
> Related: [服务端接口](../server/api.md) prescription group, [数据模型](../server/data-model.md) `prescriptions`/`decoction`/`paste`, the herb stock in/out ledger `/api/v1/herb-stock`, [对齐清单](./alignment.md) P1-32.

## 1. Current State and Gap

The Chinese-medicine prescription chain is implemented up to "prescription written + decocted and ready + patient picks it up":

| Stage | Current state |
| ---- | ---- |
| Prescribing (herbs / dosages / decoction method / review / pricing) | Implemented: `prescriptions` + `prescription_items` |
| Decoction-service pickup (to-decoct → ready → picked up) | Implemented: `decoction_status` |
| Paste pickup (to-prepare → ready → picked up) | Implemented: `paste_status` |
| Pharmacy dispense confirmation | **Missing** — the "patient took it" step had no database action, no operator, no time |
| Pharmacy workbench | **Missing** — the only way was paging through the prescription query page one by one |

The herb stock in/out ledger (`/api/v1/herb-stock`) is implemented but is **manual-entry policy**, not linked to prescribing; this document does not change that.

## 2. Policy (finalized)

1. **Dispensing = the pharmacy's single out-of-stock confirmation of a prescription**, recording "who handed it out, when"; it has nothing to do with money (refunds / collection live in settlement and [退费与日对账](./reconciliation.md)).
2. **State machine: `to-dispense(0) → dispensed(1)`, one-way**. Dispensing again returns 400; no undo is offered (the demo policy matches decoction/paste pickup — one-way flow, no rollback), and a wrong dispense is handled in the demo environment by deleting the prescription outright.
3. **Two existing sub-flows derive the dispense automatically**, so the pharmacy does not click the button again:
   - A decoction-service prescription (`decoction_status` advanced to `picked up(3)`) → automatically set to dispensed, with one flow entry `action=1, note=代煎领取自动发药`;
   - A paste (`paste_status` advanced to `picked up(3)`) → same, with `note=膏方领取自动发药`;
   - Both automatic entries are **irreversible**: "picked up" cannot be walked back, so neither can "dispensed".
4. **Ordinary herbal prescriptions** (no decoction service, not a paste) are dispensed by clicking "Dispense" in the pharmacy workbench, producing a manual `action=1` flow entry.
5. **Dispensing deducts no stock and stores no amount snapshot**: consistent with the existing pricing policy (prices are trial-computed in real time; herb names missing from the catalog are not billed); the ledger stays manual.
6. **The pharmacy workbench handles Chinese-medicine prescriptions only**: Western / patent-medicine prescriptions are not built and are out of this document's scope.

### Data

```
prescriptions 加三列：
  dispense_status   0 待发药 / 1 已发药（默认 0）
  dispense_time     发药时间（待发药为 NULL）
  dispense_by       发药操作人 staff_id（自动带出时取做操作用户）

dispense_logs（发药流水，单向追加）：
  prescription_id   非空，索引
  action            1 发药（本稿只有一种动作）
  staff_id          操作人
  note              ≤100 字（自动带出时写明来源）
  create_time / update_time
```

Indexes: `dispense_logs` prescription_id, `prescriptions` dispense_status. No soft delete (demo policy keeps no trail).

### Endpoints (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/prescription/{id}/dispense` | Dispense: `to-dispense → dispensed`; an already-dispensed prescription returns 400 「处方已发药」 (prescription already dispensed); a missing prescription returns 400 |
| GET | `/api/v1/prescription/dispense/page` | Pharmacy workbench paging: `dispenseStatus` optional (0 to-dispense / 1 dispensed), `customerName`/`phone` fuzzy, id descending; joins patient name and phone plus decoction / paste status |

## 3. Non-Goals (explicitly not done in this document)

- No Western / patent-medicine prescriptions or drug catalog (the features.md "Prescription (Western/patent medicine)" row stays unimplemented).
- No purchase documents, stocktaking, damage write-offs, or multiple warehouses (herb inventory stays on the in/out-ledger policy).
- No stock-deduction linkage between dispensing and prescribing.
- No refund / collection linkage (owned by [退费与日对账](./reconciliation.md)).
- No barcode / scan-to-dispense, dose-split packaging, or granule-machine integration.

## 4. Web Landing Points

- Implemented `/prescription/dispensary` (pharmacy workbench, login only): a to-dispense / dispensed / all three-state toggle with counts, patient name / phone search, list = prescription number / patient (name + masked phone) / type / doses / herb count / decoction / paste / dispense status and time / prescribed-at time, and a "Dispense" row action (dialog with an optional note ≤100 chars, one-way confirm; dispensed rows are read-only).
- The prescription query page's detail drawer gained dispense status and time plus a "Confirm dispense" shortcut button (dispensing with a note goes through the workbench); advancing a decoction / paste to "picked up" dispenses automatically — no repeat click.

## 5. Test Landing Points

- `DispenseTest`: manual dispense, repeat dispense 400, not-found 400, decoction pickup auto-derives dispense, paste pickup auto-derives dispense, flow order and note.
- `DispenseHttpTest`: dispense → workbench paging filters (dispenseStatus/customerName) → an already-dispensed prescription cannot be dispensed again; unauthenticated 401.
