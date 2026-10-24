# Refunds and Daily Reconciliation (Policy Design Draft)

> Status: **Implemented (2026-10-11)**. Three implementation notes against the finalized policy: ① the recharge section counts only real recharges (positive entries whose note is not a refund note) — the deduction from paying by stored value belongs to the collection side and the refund top-up to the refund side; the finalized text left negative rows unsorted, so this narrows it by semantics; ② the unique index on `settlements.order_id` was removed together with "re-collecting after a refund produces a new settlement" (not created on new databases; `SettlementIndexMigration` drops it at startup on old ones), and duplicate-collection prevention is now judged as "the latest settlement is not refunded"; ③ refund flows and reconciliation detail rows join out the display fields `payTypeText`/`customerName`/`staffName`. Test landing points match section 5 (`RefundReconciliationTest` 6 cases + `RefundHttpTest` 2 cases).
> Related: [服务端接口](../server/api.md) settlement group, [数据模型](../server/data-model.md) `settlements`/`recharges`/`card_usages`, the monthly billing report `GET /api/v1/settlement/report/month`, [药房与发药流转](./dispensing.md), [对齐清单](./alignment.md) P1-33.

## 1. Current State and Gap

The billing chain is implemented: settlement-counter collection (`payType` 1 stored value / 2 WeChat / 3 Alipay / 4 cash / 5 visit-card offset, one settlement per order), stored-value recharge flows (`recharges`, including the negative entries from paying by stored value), visit-card write-off flows (`card_usages`), receipt template printing, and the monthly billing report (aggregated by settlement time).

Gaps:

| Stage | Current state |
| ---- | ---- |
| Wrong charge / refund | **Missing** — once a settlement lands there is no reversal of any kind, and a wrongly deducted stored-value balance cannot be recovered |
| Daily reconciliation | **Missing** — the monthly report aggregates by month, so a cashier cannot close the books for a shift or a day |

## 2. Policy (finalized)

1. **Refund target = one completed settlement**. One settlement per order (`order_id` unique), so one refund = refunding the whole order; no partial refunds.
2. **Refunds return the way they came** (by the original `payType`):
   - Stored value (1): the balance is topped back up — `recharges` gets one positive entry with `note` recording 「退单 #结算id」 (refund of settlement #id);
   - Visit card (5): the write-off flows for that order are voided (`card_usages` deleted by order, the card's remaining visits restored) and the refund amount is recorded as 0; the demo policy keeps no trail, the same as deleting stock flows;
   - WeChat / Alipay / cash (2/3/4): the demo has no real payment channel, so only a refund record is written — no money actually moves.
3. **Order rollback: `completed(2) → accepted(1)`**, after which the settlement counter can collect again; the new collection produces a new settlement, and the original refund record is kept.
4. **A settlement can be refunded once only**: the unique index on `refund_logs.settlement_id` is the backstop; refunding an already-refunded settlement returns 400 「该结算已退款」 (this settlement has already been refunded).
5. **Daily reconciliation = a statement per calendar day (by settlement / recharge / refund time)**, no shift handover:
   - Collection side: number of settlements, total actually received (visit-card settlements count in the number with 0 received), `payTypes[]` breakdown;
   - Recharge side: number of recharges and amounts (**listed separately, not counted as revenue** — consistent with the monthly report policy);
   - Refund side: number, amounts, and breakdown by original payment method;
   - Net: `netMoney = received − refunds`; **cash net = cash collections − cash refunds** (for the cashier's cash-box check).
6. The refund amount = the original settlement's `money` refunded in full (0 for visit cards); a reason ≤100 chars is mandatory (audit policy — a refund must be able to answer "why").

### Data

```
refund_logs（退款流水）：
  settlement_id     非空，唯一索引
  order_id          非空，索引
  money             退款金额（元，= 原结算实收；次卡为 0）
  pay_type          原支付方式冗余（1-5）
  staff_id          操作人
  reason            退款理由，≤100 字，非空
  create_time / update_time
```

No soft delete (demo policy keeps no trail); the positive entries refunding stored value are naturally traceable in `recharges`.

### Endpoints (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/settlement/{id}/refund` | Refund: `reason` required, ≤100 chars; validates that the settlement exists / is not already refunded / the order is completed; rolls back the order status, returns the money the way it came, and writes the refund flow |
| GET | `/api/v1/settlement/reconciliation?date=` | Daily statement: `date=yyyy-MM-dd` required (bad format returns 400); collection / recharge / refund sections + net and cash net |

## 3. Non-Goals (explicitly not done in this document)

- No partial or line-item refunds (billing is anchored to service-card order settlement; whole-order policy is enough).
- No cross-day reversals or book edits (refund and re-collect instead).
- No invoices, medical-insurance settlement, or real online-payment refund channels.
- No shift handover / per-shift cash-box report (calendar-day reconciliation comes first).
- No stored-value account cancellation or zeroing.

## 4. Web Landing Points

- New page `/billing/reconciliation` (daily reconciliation, login only): date picker (defaults to today) + stat cards (received / refunds / net / cash net) + a payment-method mix table + two detail tables for recharges and refunds.
- The settlement counter's pending queue does not flag "previously refunded"; re-collecting settles normally; the payment-method column on the order page stays as is.

## 5. Test Landing Points

- `RefundReconciliationTest`: stored-value refund tops the balance back up, visit-card refund restores visits, WeChat / cash only write a record, re-refund 400, the rolled-back order can be collected again, daily-reconciliation three-section aggregation and cash net, recharges not counted as revenue.
- `RefundHttpTest`: refund → read the statement → re-refund 400; `date` missing / invalid 400; unauthenticated 401.
