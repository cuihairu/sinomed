# Beauty business extension: model reconciliation and phasing (salon merge)

> 🌐 [中文](./salon-extension.md) · **This page is in English**

> Status: **direction record (unscheduled)**. This page is the pre-implementation model reconciliation after the 2026-10-18 user directive "merge the salon repo into this repo" — mapping each of the four beauty directions onto the existing clinic model, deciding what is reused and what is added. **The hard rule: no second, parallel entity design.** Requirements reference: [beauty business survey](/research/beauty-salon-feature-survey.md) (nine categories + MVP advice; in Chinese).

---

## 1. Background and positioning

- 2026-10-18 user directive: the salon repo merges into this repo and stops being developed separately (todo.md "美容业态扩展" item).
- Positioning: **skincare + TCM beauty, no hairdressing**; shares the **customer records / service cards / follow-up** core with the existing clinic business.
- Regulatory boundary (survey §8.5): the project catalog follows the **life-beauty** scope — no medical-aesthetics charting or diagnosis features; TCM beauty rides on the clinic's existing TCM context.

## 2. Reconciliation summary

| # | Ledger direction | Reused (existing model) | Added | Evidence (file:line) |
| --- | --- | --- | --- | --- |
| a | Project catalog extension (skin care / body care / TCM beauty) | the `items` table as a whole (name/price/description/**duration_minutes**/enabled/cover/sort) | a **category** dimension on `items` (start with a lightweight enum field; split into a dictionary table only if categories proliferate) | `ItemEntity.java` has no category field (grep across all entities: zero hits); the duration field already exists, so booking occupancy and checkout need no change |
| b | Booking resource model (workstation / technician scheduling) | technician = `staffs` (role/title/famous_level already express position and level); scheduling = `staff_shifts` (weekday+start/end weekly template) + `signs` clock-in | a **workstation/room entity** (no resource-level room model exists today — `QueueCallEntity.java:38` `room` is display text on queue calls) + `appointments.room_id`; conflict detection widens from technician-only to technician+workstation | `AppointmentEntity.java` has customer_id/item_id/**staff_id**/start_time/duration/status, no room dimension |
| c | Technician commission rules (distinct from doctors) | performance attribution already anchored: `orders.staff_id` + `order_items` multi-line detail (name/price snapshots) | `commission_rules` (P0 fixed ratio: project × position) + `commission_records` (generated at checkout, summable); doctors and technicians share **one** table distinguished by position — never two | grep for commission across the whole server source: **zero hits** — purely new, no parallel conflict |
| d | Beauty customer tags (skin type / allergy history / care plan) | **allergy history reuses `customer_histories`** (type 0 allergy / 1 history, free text, `CustomerHistoryEntity.java:20`) | skin type/preference = lightweight customer tags; care plan = a customer-record extension (**not** reusing `TherapyTemplateEntity` — that is an external-treatment prescription template: method/points/course, a clinical-orders context) | `CustomerEntity.java` has no tag or skin-type fields today |

> Progress: **a, b, c and d all implemented**. a (2026-10-18, authorized as a standing decision): `items.category` column (integer default 0; ddl-auto adds the column as the migration) + create/update/paged category filtering + admin category select and list annotation, locked down by 4 tests in `ItemCategoryHttpTest`. b (2026-10-18, patrol round): `rooms` workstation table + `appointments.room_id` (nullable) + technician/workstation two-dimension slot conflict (a workstation bucket count added to `ensureSlotFree`) + admin workstation management page and workstation select on the booking form, locked down by 3 tests in `RoomHttpTest` + 5 in `AppointmentRoomConflictHttpTest`; the queue-call `roomOf()` still uses the staff name + "诊室" display text, unchanged. c (2026-10-18, patrol round): `commission_rules` (P0 fixed ratio "card × position", one ratio per position) + `commission_records` (booked at settlement, card-deduction with 0 paid still books, equal negative reversal on refund netting to zero, per-staff totals), locked down by 3 tests in `CommissionRuleHttpTest` + 5 in `CommissionRecordHttpTest`. d (2026-10-18, patrol round): `customer_tags` (unique per customer) + history type 2 care plan (reuses customer_histories; the allergy cross-check still reads type 0 only) + PIPL separate consent (`customers.beauty_consent` flag + time + handling staff, `/consent` endpoint, shipped with d as required; visibility status quo: archive fields visible only in the admin console and the physician workbench, the customer mini program does not expose them — row-level ACL tightening left to P1), locked down by 4 tests in `CustomerTagHttpTest`.

## 3. Shared-core confirmation and survey corrections

The ledger sentence "shares the customer-records / cards / follow-up core with the clinic business", checked item by item:

| Shared core | Current state | Conclusion |
| --- | --- | --- |
| Customer records | `customers` (name/phone/openid/level/birthday…) | shared as-is; beauty-specific differences go through tags and record extensions, not the main table |
| Service cards | `customer_cards` (totalTimes/remainingTimes/**validUntil**/sourceOrderId) = per-visit card instance; `recharges` = stored value | the per-visit card model is isomorphic to salon course cards — shared as-is; **principal/gift split** (survey §1.2) to be checked against `recharges` when stored value is implemented, adding fields if needed |
| Follow-up | the `review` series (Review/ReviewCustomer/ReviewStaff) | shared as-is; birthday/follow-up reminders are a trigger layer — add tasks at implementation, not new entities |
| Points | **zero server implementation** (grep for 积分 across `server/src/main/java`: zero hits) | ⚠️ corrects survey §10.1's "consumption points already implemented (1 yuan = 1 point)" — that claim **does not hold** in the clinic model; if P0 needs points it must be scheduled as new work, or demoted to P1 |
| Rooms | `QueueCallEntity.room` is display text on queue calls (the "诊室" in `AdScreenEntity` is ad targeting) | ⚠️ confirms survey §10.1's open question: **no resource-level room model exists** — direction b is genuinely new |

## 4. Compliance baseline (ships with each phase, not retrofitted)

- **Sensitive personal information** (survey §8.1): skin type, allergy history, and care photos are PIPL "medical health" sensitive information — collection needs **separate-consent** records and tightened visibility (only the serving technician and the manager); launch together with the records/tags features, never afterwards. Phase d shipped the separate-consent record (`customers.beauty_consent` + time + handling staff); visibility status quo = visible in the admin console and the physician workbench, not exposed to the customer app — row-level ACL tightening left to P1.
- **Prepaid cards** (survey §8.4): selling stored value falls under single-purpose prepaid-card regulation; the system must at least produce **card issuance and balance reports** to support filing; confirm local enforcement with legal counsel before selling cards publicly.
- **Life-beauty boundary** (survey §8.5): the project catalog must not include medical-aesthetics items (injections, light/energy treatments); marketing copy must not promise medical outcomes (manual process).

## 5. Phasing direction (per survey §10; unscheduled)

- **P0 (opening essentials)**: a catalog categories (**implemented**) + b workstation entity and two-dimensional conflict detection (**implemented**) + c fixed-ratio commission booking (**implemented**) + d tags/allergy (reuse + additions, **implemented**); combined payment with per-visit card redemption, receipts, daily close and shift handover per the survey checklist.
- **P1**: commission rule engine (tiered/multi-party split), consumables BOM, expiry alerts, marketing (coupons/group-buy/referral/win-back), outreach channels.
- **P2**: multi-store (decided non-goal, see todo.md; store fields and aggregation to be defined then), deep POS-terminal adaptation, door access.
- Within each phase, **mobile first** (todo.md ordering basis: 手机端优先).

## 6. Hard rule: no parallel entities

Every extension lands as "add fields to existing entities / add new tables". **Forbidden**: "beauty edition" parallel entities or tables such as beauty customers / items / appointments / scheduling. The reconciliation (§2) gives the reuse anchor for each direction; implementation PRs follow this page — implementations conflicting with this page get corrected back to it.
