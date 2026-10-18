# Data model

> 🌐 [中文](./data-model.md) · **This page is in English**

Data tables are auto-created by JPA from the entities (`ddl-auto: update`), and all primary keys are auto-incrementing `id`. Every entity carries the audit fields `create_time` / `update_time` (maintained automatically by JPA Auditing; `card_adjusts` has only `create_time`); there are no foreign-key constraints between entities — they are associated via business id fields at the service layer.

> Scope note: the detailed tables below cover the core business tables; tables such as `acupoints`, `ad_schedules`, `ad_screens`, `dispense_logs`, `formula_items`, `formulas`, `herb_stock_logs`, `herbs`, `prescription_items`, `prescription_template_items`, `prescription_templates`, `recharges`, `review_customers`, `review_staffs`, `therapy_templates` are authoritative in their entities and in [api](/server/api) (`TokenEntity` is dead code and is not counted).

## customers — patient

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| name | String | Full name (not null) |
| age | Integer | Age |
| level | Integer | Member level |
| phone | String | Phone number (unique, mainland China numbering) |
| openid | String | Mini program openid (unique, nullable); the phone card lookup's openid path binds and looks up cards by it — bare-phone-number card lookup is not done (2026-10-14 ruling) |
| gender | Integer | Gender (0 female, 1 male) |
| address | String | Address |
| birthday | Date | Birthday |

Indexes: name, phone number, level, birthday, openid.

## customer_histories — patient medical history (allergy history / past history)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| customer_id | Long | Patient id (not null, business foreign key, indexed) |
| type | Integer | 0 allergy history / 1 past history (not null) |
| content | String | History content (not null, free text, ≤200 characters) |

Records are appended one by one (add/delete only, no editing), and queries return them in reverse id order. The demo seed adds allergy/past-history records for each of the two demo patients — see the patients group in [api](/server/api).

## treats — treatment record (core business table)

TCM treatment records; one patient can have multiple:

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| cid | Long | Patient id (not null, business foreign key) |
| staff_id | Long | Treating physician id (nullable, staffs.id; the signature semantics = which physician performed this treatment; historical records may be unsigned, and the name is echoed back from staffs on read) |
| cdesc | TEXT | Chief complaint |
| inquiry | TEXT | Inquiry |
| observation | TEXT | Inspection |
| palpation | TEXT | Palpation |
| pulse | String | Pulse presentation |
| pulse_left / pulse_right | TEXT | Left / right hand pulse presentation |
| pulse_info | TEXT | Pulse interpretation |
| five_ben_zi_* / five_zi_ben_* | String | Five-phase · mother/child (generation) relations (left/right hand) |
| five_ben_ke_* / five_ke_ben_* | String | Five-phase · restraint relations (left/right hand) |
| five_nan_jin_* (large/small, left/right) | String | Nan Jing (Classic of Difficulties) imagery |
| five_rate_left / five_rate_right | TEXT | Five-phase ratios |
| five_aux_left / five_aux_right | TEXT | Five-phase auxiliaries |
| acupoint_left / acupoint_right | TEXT | Point selection (left/right hand) |
| acu_method / manipulation | String | Acupuncture prescription: needle technique (filiform needle / electroacupuncture / warm needle…), manipulation (reinforcing / reducing / even reinforcing-reducing…), ≤30 characters, blanks treated as empty |
| moxa_method | String | Acupuncture prescription: moxibustion method (mild moxibustion / sparrow-pecking moxibustion / indirect moxibustion…), ≤60 characters, blanks treated as empty |
| retention_minutes | Integer | Acupuncture prescription: needle retention minutes (1-240, nullable) |
| acu_course | TEXT | Acupuncture prescription: course / frequency (free text, ≤100 characters) |
| diagnose | TEXT | Diagnosis |
| plan | TEXT | Conditioning plan |
| diet | TEXT | Dietary advice |
| conditioning | TEXT | Conditioning process |
| review | TEXT | Follow-up record |

Indexes: patient id, creation time.

Treatment-timeline convention (2026-10-12 ruling; the "treatment records" list on the patient record): each prototype entry = item name + course tag + summary (physician · duration · pattern differentiation, treatment method, point selection). **Physician** comes from the `staff_id` signature (echoed as `staffName` on read; unsigned records carry no label); **duration** comes from `retention_minutes` needle-retention minutes (only acupuncture treatments have a value, and no value means no label — visit duration is never fabricated); **course tag** comes from `acu_course`; **item name is not shown** — treatment records have no association fields with items, service cards, or orders anywhere (`treats` has no `item_id`, `orders` has no `treat_id`, `card_usages` has no `treat_id`), and where there is no association nothing is fabricated; the item-name dimension is covered by the "cards held" panel on the same page and the service-card course markers (see the course convention). To attribute "treatment ↔ settlement", a new `orders.treat_id` plus defined attribution semantics would be required — a separate requirement.

`acupoints` (acupoint dictionary): `name` (acupoint name, unique, 1–10 characters), `pinyin` (full-pinyin lowercase search code, not null), `meridian` (meridian affiliation, not null), `location` (body-surface location), `indication` (indications), audit times. This is reference data for meridians and acupoints — the point-selection fields on the treatment record remain free text, and the dictionary only assists point selection (search by acupoint name/pinyin, append on selection); it participates in no other business validation.

## items — service card

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| name | String | Name (not null, unique) |
| price | Integer | Price |
| description | TEXT | Description |
| enabled | Integer | 1 on sale / 0 off sale (the Kiosk shows on-sale items only; existing rows default to 1) |
| cover | String | Cover image relative URL (nullable, hosted under `/media`) |
| duration_minutes | Integer | Duration (minutes, nullable, for display) |
| category | Integer | Category: 0 clinic service (default; existing rows get 0 when the column is added) / 1 skin care / 2 body care / 3 TCM beauty (beauty extension item a, docs/design/salon-extension.md) |
| sort | Integer | Display order, lower values first |

## reviews / review_staffs / review_customers — feedback and retrospectives

| Table | Key fields | Notes |
| ---- | ---- | ---- |
| `reviews` (daily summary) | `day` (unique, normalized to midnight of that day), `good`, `improvement` | Clinic-wide daily summary; saving on the same day overwrites |
| `review_staffs` (staff daily retrospective) | `staff_id`, `name`, `cost` (cost incurred), `done`, `advice`, `day` | Recorded per staff member per day |
| `review_customers` (patient follow-up) | `customer_id`, `name`, `last` (last visit time), `day` (follow-up day), `advice` | Once `day` is due, the entry appears in the daily report reminders |

## orders / recharges

| Table | Key fields | Current state |
| ---- | ---- | ---- |
| `orders` (orders) | `user_id` (patient), `item_id` (service card), `staff_id`, `status`, `price` | Wired up: Kiosk orders and front-desk orders both land as `status=0` and snapshot the service card's current price (front-desk orders also record `staff_id`); `status` semantics: `0` ordered → `1` confirmed → `2` completed, `9` cancelled (`0/1→9`, only `9` can be deleted); admin-console paging and transitions in the orders group of [api](/server/api); settlement info lives in the `settlements` table (one settlement per order), and order paging joins out the payment method |
| `recharges` (stored-value transactions) | `user_id`, `money`, `note` (nullable, ≤100 characters; refunds write back a note like "refund for order #settlement id") | Wired up: top-ups are positive and stored-value payment deductions are negative, so balance = sum of the transactions; stored-value refunds return on the original path as a positive transaction (excluded from top-ups in daily reconciliation); endpoints in the stored-value group of [api](/server/api) |
| `order_item` (order line items) | `order_id`, `item_id`, `name`, `price`, `qty`, `subtotal` | Multi-item orders write one line per item (name/price are snapshots at order time, qty≥1, subtotal = unit price × quantity); order endpoints always join out `items[]`, and for legacy orders without line rows they fall back to a single row with quantity 1 based on `itemId+price` |

## settlements — settlement (billing)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key (settlement number, displayed in reverse chronological order) |
| order_id | Long | Order id (not null; the original unique index was removed along with the refund convention — after a refund, re-billing one order can produce multiple settlements, and duplicate-charge prevention is now backstopped by a "most recent un-refunded" check) |
| user_id | Long | Patient id (not null, denormalized from the order for per-patient reconciliation) |
| pay_type | Integer | Payment method: `1` stored value / `2` WeChat / `3` Alipay / `4` cash / `5` visit-card write-off |
| money | Integer | Amount actually received (CNY, taken from the order price snapshot; visit-card write-off records 0) |
| create_time / update_time | Date | Audit times |

Indexes: `user_id` (the `order_id` unique index was removed along with the refund convention; a startup migration DROPs legacy indexes left in old databases). The settlement action moves the order `0/1 → 2` completed; stored-value payment also writes one negative transaction to `recharges`; visit-card write-off deducts 1 from `customer_cards` remaining visits. WeChat/Alipay are demo-convention (the method is only recorded, with no real payment channel) — see the billing group of [api](/server/api).

## customer_cards — patient card holdings (visit cards)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| customer_id | Long | Patient id (not null, indexed, customers.id) |
| item_id | Long | Service card id (not null, items.id) |
| total_times / remaining_times | Integer | Total visits / remaining visits (issuing a card grants the full amount; write-off deducts from the remaining count) |
| status | Integer | `1` active / `0` suspended (column default 1; suspended cards do not participate in write-off) |
| source_order_id | Long | Source order of the card issue (nullable; empty for cards issued manually at the front desk) |
| valid_until | Date | Valid until (nullable = no expiry); expired cards do not participate in write-off |
| create_time / update_time | Date | Audit times |

Indexes: patient. Write-off picks the **earliest** card matching "same patient + same service card, active, unexpired, with remaining visits" and deducts 1 visit. Endpoints in the visit-card group of [api](/server/api).

Course convention (2026-10-12 ruling): **a course = a service card** — the Nth card of the same item (by `create_time` purchase order) is the Nth course, naturally derived from the card-purchase transactions; **no** "patient × service card completed-visits" ledger table is created; without a service card no course is tagged (item-level visit history does not exist, so first visit / follow-up is never fabricated). Scheduling cells and the patient strip on treatment records display per this convention; batch card retrieval goes through `GET /api/v1/card/customers`.

## refund_logs — refund transactions (refunds)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| settlement_id | Long | The refunded settlement (not null, **unique index** — one settlement can be refunded only once) |
| order_id | Long | Order id (not null, indexed, used to roll back the order status) |
| money | Integer | Refund amount (CNY, = the original settlement's amount actually received; 0 for visit cards) |
| pay_type | Integer | Redundant copy of the original payment method: `1` stored value / `2` WeChat / `3` Alipay / `4` cash / `5` visit-card write-off |
| staff_id | Long | Operator staff id (nullable) |
| reason | String | Refund reason (≤100 characters, not null — a refund must be able to answer "why") |
| create_time / update_time | Date | Audit times |

Refund = the whole order returned on the original path, with no soft delete (demo convention leaves no trace); the positive transaction for a stored-value refund is naturally recorded in `recharges` (note "refund for order #settlement id", excluded from top-ups in daily reconciliation); a visit-card refund voids that order's `card_usages` and restores the remaining visits on `customer_cards`; after a refund the order goes `2 → 1` and can be re-billed. Convention documented in [reconciliation.md](../design/reconciliation.md).

## card_usages — visit-card write-off transactions

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| card_id | Long | Card-holding id (not null, indexed, customer_cards.id) |
| order_id | Long | Order that triggered the write-off (nullable — backfilled legacy cards / historical migrations have no order) |
| staff_id | Long | Serving / operating staff (nullable, taken from the order's staffId) |
| times_used | Integer | Which visit number this consumption is (1-based = total visits − remaining after the deduction) |
| create_time / update_time | Date | Audit times |

Append-only: every settlement write-off (`payType=5`) writes one row in the same transaction and rolls back with the settlement; there is no manual deduction. Seed data backfilled historical write-offs (with no order number) for the demo cards. Endpoints in the visit-card group of [api](/server/api).

## card_adjusts — card-holding adjustment transactions (treatment-course card marketing: bonus visits / transfer card)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| card_id | Long | Card-holding id (not null, indexed, customer_cards.id) |
| type | Integer | 1 bonus visits / 2 transfer card |
| times | Integer | Visits added (1-99); fixed 0 for transfer card |
| customer_id | Long | Target patient id for the card transfer (required for type=2; empty for bonus visits) |
| reason | String | Adjustment reason (not null, ≤100 characters, shown in the front end as a trail) |
| staff_id | Long | Operating staff (nullable; the server takes the staff matching the current logged-in account) |
| create_time | Date | Audit time |

Append-only: bonus visits add N to both `total/remaining` (in the same transaction); transfer card only swaps `customer_id` (remaining visits and validity stay as-is); suspended cards cannot be operated on (expiry does not block — you can adjust the validity first and then use it). Endpoints in the visit-card group of [api](/server/api).

## appointments — appointments (front desk / clinic manager creates bookings; mini program self-service slot booking → check-in on arrival → convert to treatment)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| customer_id | Long | Patient id (not null, customers.id) |
| item_id | Long | Booked service card id (nullable = decided on arrival) |
| staff_id | Long | Check-in staff id (nullable = assigned on arrival) |
| room_id | Long | Workstation id (nullable = no workstation axis; beauty bookings occupy one, rooms.id) |
| start_time | Date | Start time of the booked slot (not null) |
| duration | Integer | Duration (minutes; self-service slot booking is fixed at 60) |
| status | Integer | `0` not yet arrived → `1` checked in (converted to treatment); `0→9` cancelled; only `9` can be deleted |
| remark | String | Remarks (symptoms / needs); self-service slot booking records the soft marker "mini program self-service" — there is no source column |
| create_time / update_time | Date | Audit times |

Indexes: patient, service card, workstation, time slot. Endpoints in the appointments group of [api](/server/api); same-slot conflict check: a booking is rejected when the same physician already has a `status 0/1` appointment on the same day and hour (one booking per slot; the empty-`staffId` group is mutually exclusive on its own); a non-empty `roomId` is mutually exclusive on the workstation axis too (technician + workstation two dimensions, beauty phase b, locked by `AppointmentRoomConflictHttpTest`).

## commission_rules / commission_records — commission (beauty phase c)

| Table | Fields | Notes |
| ---- | ---- | ---- |
| `commission_rules` (rules) | `item_id` (not null), `title` (position, exact match on `staffs.title`), `ratio` (integer percent 0–100) | P0 fixed ratio "card × position", one ratio per position (unique constraint item_id+title); physicians and technicians share one table, never a parallel one; rule engine is P1 |
| `commission_records` (records) | `order_id`, `settlement_id`, `order_item_id` (all not null), `staff_id` (not null), `item_name`/`subtotal`/`ratio` (snapshots), `amount` (yuan, negative on refund reversal), `remark` | Generated automatically when a settlement completes (hooked in SettlementServiceImpl): attribution = `orders.staff_id`, booked per `order_items` line subtotal × ratio (card-deduction settlements with 0 paid still book; integer yuan rounded down); insert-only, refunds write equal negative reversals (remark 「退单冲销 #结算id」) netting to zero; legacy orders without item lines are not booked |

Indexes: rule item_id + unique (item_id, title); record order_id, settlement_id, staff_id. Endpoints in the commission group of [api](/server/api).

## rooms — workstations / rooms (beauty phase b)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| name | String | Workstation name (not null, unique) |
| enabled | Integer | 1 in use, 0 disabled (existing rows default to 1; the booking form only lists workstations in use) |
| create_time / update_time | Date | Audit times |

Index: name. The resource axis of appointments — beauty bookings occupy a workstation, clinic bookings do not (`appointments.room_id` is nullable); unrelated to the queue display text `queue_calls.room` (a display field, not a bookable resource). See the workstations group of [api](/server/api).

## prescriptions / prescription_items — herbal prescriptions

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key (prescription number) |
| treat_id | Long | Treatment record id (nullable, treats.id) |
| customer_id | Long | Patient id (not null, customers.id) |
| staff_id | Long | Prescribing physician id (nullable, staffs.id) |
| doses | Integer | Doses (number of packets, not null, default 7) |
| decoction_status | Integer | Decoction service status: 0 no decoction service needed / 1 pending decoction / 2 ready for pickup / 3 picked up (checking decoction service when writing the prescription sets 1; the flow pending → ready for pickup → picked up advances one-way only) |
| decoction_bags | Integer | Number of decoction-service bags (= doses; empty when no decoction service is needed) |
| prescription_type | Integer | Prescription type: 0 decoction (default) / 1 paste |
| paste_status | Integer | Paste pickup status: 0 not a paste / 1 pending preparation / 2 ready for pickup / 3 picked up (writing a paste prescription sets 1; the flow pending preparation → ready for pickup → picked up advances one-way only) |
| craft | String | Finishing method (pastes only: refined honey / clear paste / sugar paste / ejiao finishing, etc., nullable; always empty for decoctions) |
| dispense_status | Integer | Dispensing status: 0 pending dispensing (default) / 1 dispensed (writing the prescription sets 0, one-way, no rollback; advancing decoction service / paste to "picked up" automatically sets 1) |
| dispense_time | Date | Dispensing time (empty while pending dispensing) |
| dispense_by | Long | Dispensing operator staff_id (left empty on the auto-populated path) |
| review_staff_id | Long | Pharmacist reviewer staff_id (nullable) |
| check_staff_id | Long | Dispensing / double-check staff_id (nullable) |
| delivery_type | Integer | Pickup method: `0` self pickup (default) / `1` same-city delivery (available when decoction service is checked, nullable) |
| usage / take_method | String | Decoction method (decoct in water, one dose per day, etc.) / administration (warm in divided morning/evening doses, etc., nullable) |
| remark | String | Remarks |
| create_time / update_time | Date | Audit times |

`prescription_items` (herbs): `prescription_id` (not null, indexed), `herb` (herb name, not null, free text), `weight` (grams per dose, Double, not null), `special` (special decoction method, nullable), `process` (processing, nullable), `sort` (order, not null).

`herbs` (herb dictionary): `name` (unique; price comparison requires an exact same-name match with the prescription herb), `price` (price per gram in fen, int, >0), `stock_min`/`stock_max` (stock lower/upper limits, grams, nullable = no alerting — the balance endpoint flags `warnStock` when below the lower limit or above the upper limit; stockroom lower/upper-limit alert convention), `dose_max` (grams-per-dose upper limit, g/dose, nullable = no alerting — the prescription-time dose review `POST /prescription/dose-check` compares grams per dose against this, and `weight` must strictly exceed the limit to trigger; entered by the clinic itself, same convention as the stock limits; there is no authoritative Pharmacopoeia dose data source in the repo, so nothing is hardcoded), audit times. Prescription pricing is a **live estimate** against the dictionary (not persisted, no snapshot; herb names not in the dictionary are not billed).

`dispense_logs` (dispensing transactions, one-way append): `prescription_id` (not null, indexed) + `action` (1 dispensing — this path has only one action type) + `staff_id` (operator, left empty when auto-populated) + `note` (≤100 characters; the auto-populated path writes the source, e.g. "decoction-service pickup auto-dispensed") + audit times. Duplicate dispensing returns 400 and cannot be undone; dispensing does not deduct stock and writes no amount snapshot — see [pharmacy and dispensing flow](../design/dispensing.md).

`herb_stock_logs` (processed-herb stock-in/stock-out transactions): `herb_id` (not null, indexed) + `type` (1 stock-in / 0 stock-out) + `quantity` (grams, positive integer) + `expiry` (batch expiry, may be attached on stock-in, always empty on stock-out) + `supplier` (≤50 characters) + `note` (≤100 characters) + audit times. **Stock = stock-in total − stock-out total (no balance column; recomputed from the transactions)**; stock-out consumes batches first-expiry-first-out by expiry (FEFO), and the balance endpoint reports "earliest unconsumed batch expiry + expiry alert (default 30 days)" on that basis; ledger convention — no procurement documents or settlement involved; see the processed-herb inventory group of [api](/server/api).

`prescription_templates` / `prescription_template_items` (syndrome templates, structure same as below): the template side adds suggested values `doses`/`decoction`/`usage`/`remark` and an `enabled` listing flag. `formulas` / `formula_items` (formula library): `formulas` has `name` (formula name, unique, 1–20 characters), `pinyin` (full-pinyin lowercase search code, not null), `source` (source), `indication` (functions and indications); `formula_items` has `formula_id` (not null, indexed) + the four herb fields `herb`/`weight`/`special`/`sort`. Both are reference data for writing prescriptions — templates are applied by syndrome, formulas are searched by formula name/pinyin and pull up the complete formula; after being pulled up they can be freely added to or removed from the prescription and are never written back; formulas follow the literature's common-dose convention and do not participate in pricing or prescription review.

`therapy_templates` (external-therapy prescription templates): `name` (template name, unique, 1–20 characters, indexed) + `method` (external-therapy method, not null, ≤10 characters, indexed) + `target` (suggested point selection / body area, ≤100 characters, nullable) + `course` (suggested course / frequency, ≤100 characters, nullable) + `note` (precautions, ≤100 characters, nullable) + `enabled` (0 disabled / 1 enabled) + audit times. A single table with no line items (an external-therapy combination is free text of "method + body area + course", unlike formulas which have per-herb dosages). The treatment page's "apply external-therapy template" takes only the three suggested values `method`/`target`/`course` and fills them into the acupuncture-prescription area and the point-selection text; afterwards they are freely editable with the treatment record and never written back to the template.

Indexes: `prescriptions` patient, treatment record; `prescription_items` prescription; `herb_stock_logs` herb, type. Decoction-service pickup and paste pickup flows are implemented (two independent one-way chains: pending decoction → ready for pickup → picked up; pending preparation → ready for pickup → picked up); pharmacy dispensing is implemented (`dispense_status` 0→1 one-way + `dispense_logs` transactions, auto-populated when decoction service / paste reaches "picked up"); processed-herb stock-in/stock-out transactions are implemented (balance recomputed from the transactions); compatibility review (the eighteen incompatibilities / nineteen antagonisms) is a static rule comparison (not persisted as a table) — see the prescriptions group of [api](/server/api).

## staffs — staff (login identities; clinic operations)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| name | String | Full name (not null) |
| account | String | Login account (unique) |
| password | String | Password (BCrypt) |
| phone | String | Phone number (unique) |
| avatar | String | Avatar URL |
| age | Integer | Age |
| gender | Integer | Gender (0 female, 1 male) |
| address | String | Address |
| birthday | Date | Birthday |
| role | Integer | Role: `role == 0` returns the `admin` permission set, `1-9` is management (the `MANAGER` permission set), `>= 10` is ordinary staff (`USER`, default 99); method-level `@PreAuthorize` decides on this basis |
| title | String | Job title (free text, nullable): the title semantics and roster data source beyond the permission tier role, e.g. physician / acupuncture physician / front desk; the scheduling page's "physician roster" = employed (status=1) with a title containing "医师" |
| famous_level | Integer | Famous-doctor level: 0 ordinary (default), 1-3 famous doctor; affects only list sorting (descending, famous doctors first) and the marker badge — no tiered / time-slot differential pricing |
| status | Integer | Status: 0 resigned, 1 employed, 2 on leave (`<= 0` cannot log in) |

Indexes: name, account, phone number.

## signs — attendance clock-ins (clinic operations)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| staff_id | Long | Staff id |
| type | Integer | 1 clock-in / 0 clock-out |
| create_time | Date | Clock time |

Attendance statistics are computed live at query time (no scheduled job): on the same day the earliest clock-in and the latest clock-out are taken, and the duration is counted in whole hours.

## staff_leaves — staff leave (clinic operations)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| staff_id | Long | Staff id (staffs.id) |
| leave_type | Integer | Type: 0 sick leave, 1 personal leave |
| reason | String | Reason (not null) |
| start_time / end_time | Date | Start/end time (end not earlier than start) |
| create_time / update_time | Date | Audit times |

Indexes: staff id. Convention: leave is record-only — it does not change the staff login status and has no approval flow (demo convention).

## staff_shifts — staff recurring shift table (clinic operations)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| staff_id | Long | Staff id (staffs.id) |
| weekday | Integer | Day of week (1 Monday … 7 Sunday) |
| start_time / end_time | String | HH:mm slot (start < end) |
| create_time / update_time | Date | Audit times |

Indexes: staff id, day of week. Convention: one shift per staff member per weekday, repeating weekly; the shift table is only a scheduling reference — attendance clock-ins still defer to signs (clock-in records are never written back).

## ad_materials / ad_screens / ad_schedules — advertising screens (tablet display)

These back the Kiosk side of [tablet/](/design/tablet); see the [tablet display design](/design/tablet). The files themselves are not stored in the database: material uploads land on disk under `data/ads/yyyyMM/` (configurable via `sinomed.ads.upload-dir`), and the tables store relative URLs.

**ad_materials — materials**: `name`, `type` (1 image, 2 video), `url`, `duration_ms` (carousel dwell time in milliseconds), `enabled`, `sort` (order, lower values first); overlay copy fields `category` (category), `kicker` (eyebrow label), `title` (headline), `copy` (body text), `price` (price, all nullable).

**ad_screens — screens**: `code` (unique; the tablet opens it with `?screen=code`), `name`, `location`, `enabled`, `last_seen_at` (a heartbeat piggybacked when the tablet pulls its playlist; the admin console shows an online badge based on a 2-minute threshold).

**ad_schedules — schedules**: `screen_id` / `material_id` (business foreign keys), `weekdays` (`1=Monday…7=Sunday`, comma-separated, empty = every day), `start_time` / `end_time` (`HH:mm`, empty = all day), `enabled`.

## queue_calls — queue calling records (tablet display)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key (also serves as the tablet pull cursor `since`) |
| screen_id | Long | Targeted screen (business foreign key; empty = broadcast to all screens) |
| number | String | Number (e.g. `08`) |
| room | String | Treatment room name |
| patient_masked | String | Masked patient name (e.g. `张*`, privacy by default) |
| status | Integer | 0 pending call / 1 called (current writes are always 1) |
| called_at | Date | Queue calling time |

## card_lookup_logs — phone self-service lookup audit (card lookup + appointment lookup + openid binding)

The table name carries over from the card-lookup audit; `/api/v1/kiosk/my-appointments` (cross-device lookup of one's own not-yet-arrived appointments), `/api/v1/kiosk/appointment-cancel` (self-service cancellation of one's own not-yet-arrived appointments, decided on the user's behalf 2026-10-16) and `/api/v1/kiosk/openid-bind` (openid self-service binding) share this same table with card lookup, under the same retention convention — patient self-service lookups after identity verification never leave gaps.

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| phone | String | Phone number of the patient whose cards were looked up (backfilled after an openid-path hit; empty on a miss) |
| operator | String | Identity of the person performing the lookup (who did it): openid path and openid binding = openid; SMS path = phone number |
| mode | String | Effective mode (off / openid_only / sms_verified) |
| customer_id | Long | Matched patient id (empty on a miss) |
| result | String | Result: `ok / disabled / missing_openid / no_binding / missing_code / bad_code / expired_code / used_code / no_customer / bind_ok / bind_conflict` (the last two are for the openid binding path) |
| create_time | Date | Time |

Indexes: phone, operator. Convention: every attempt (including rejections) writes one row; the card lookup / appointment lookup / openid binding paths share the same convention; see [kiosk.md](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定).

## sms_codes — SMS verification codes (SMS path for phone card lookup)

| Field | Type | Meaning |
| ---- | ---- | ---- |
| id | Long | Primary key |
| phone | String | Phone number (not null) |
| code | String | 6-digit numeric code |
| expires_at | Date | Expiry time (5 minutes after the code is issued) |
| used | Integer | 0 unused / 1 used (one-time, `integer default 0`) |
| create_time | Date | Code issue time |

There is no real SMS gateway: code issuance is a server-side stub, and the code only lands in the database and the log (`SMS_CODE_DEMO phone=… code=…`); tests fetch the code directly from `SmsCodeRepository`.
