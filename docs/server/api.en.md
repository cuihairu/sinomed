# REST API Inventory

> 🌐 [中文](./api.md) · **This page is in English**

All business endpoints share the unified prefix `/api/v1`; the paths in the tables below are listed in full. The admin console dev proxy forwards `/api` to the server (default `127.0.0.1:2347`), and in production Nginx forwards `/api` to the server.

## Authentication Conventions

- **Permitted paths**:
  - Login and docs: `/api/v1/user/login`, `/doc.html`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-resources/**`, `/webjars/**`
  - **Unauthenticated business endpoints (intranet device conventions, see the corresponding design docs)**:
    - `/api/v1/kiosk/**` — Kiosk browsing and ordering for patients + mini program self-service scheduling + openid self-service binding (read-only plus three write entry points: ordering, self-service scheduling, and binding; see [kiosk.md](/design/kiosk) and [app-booking.md](/design/app-booking))
    - `/api/v1/ads/playlist` — tablet pulls the schedule (read-only + screen code validation; see [tablet.md](/design/tablet))
    - `/api/v1/calls/latest` — tablet polls for queue calling (read-only + screen code validation; see [tablet.md](/design/tablet))
    - `/api/v1/print/templates` — print template delivery (blank layouts with no business data; see [desktop.md](/design/desktop) D7)
    - `/media/**` — advertising media static resources (hosted by Nginx)
  - `/actuator/**` — ops monitoring
- All other endpoints require `Authorization: Bearer <JWT>`; unauthenticated requests return 401 (including `POST /error` frontend error reporting — reports made while not logged in get swallowed by the 401; this works normally after login).
- Note that `POST /api/v1/user/register` (registration) is not on the permitted list — an account must already exist before first use.
- Staff management write endpoints (`POST`/`PUT`/`DELETE` `/api/v1/staff`) have method-level authorization enabled via `@PreAuthorize("hasRole('ADMIN')")`; non-admins attempting them get 403.
- On login, the `Authorization` response header returns the access token and `Refresh` returns the refresh token (both already carry the `Bearer ` prefix); the access token is valid for 1 day and the refresh token for 7 days. The refresh endpoint is not implemented yet, so renewing means logging in again.

## User Authentication `/api/v1/user`

| Method | Path | Purpose | Auth |
| ---- | ---- | ---- | ---- |
| POST | `/api/v1/user/login` | Username/password login; returns a token and the permission flag (`admin` / `user`) | Public |
| POST | `/api/v1/user/register` | Register a staff account (username / password 2-20 characters); returns a JWT directly | Login required |
| POST | `/api/v1/user/logout` | Log out (currently only the client discards the token) | Login required |
| GET | `/api/v1/user/current` | Get the currently logged-in staff member's information | Login required |

## Patient `/api/v1/customer`

| Method | Path | Purpose |
| ---- | ---- | ---- |
| GET | `/api/v1/customer/page` | Paged query (`current` / `pageSize` required; optional `name` / `age` / `phone` / `startTime` / `endTime`) |
| GET | `/api/v1/customer/{id}` | Query by id |
| GET | `/api/v1/customer/phone/{phone}` | Query by phone number |
| GET | `/api/v1/customer/name/{name}` | Query by name (may return multiple records) |
| POST | `/api/v1/customer/` | Create a patient record (gender required; phone number optional, unique, mainland China number format) |
| PUT | `/api/v1/customer/` | Update patient information |
| DELETE | `/api/v1/customer/{id}` | Delete a patient |
| GET | `/api/v1/customer/{id}/history` | Medical history list (allergy history / past history recorded entry by entry, newest first; returns 400 if the patient does not exist) |
| POST | `/api/v1/customer/{id}/history` | Record medical history: body `{ type, content }`; `type` 0 = allergy / 1 = past history; `content` must be non-empty and ≤200 characters (auto-trimmed); returns 400 if the patient does not exist or the type is invalid |
| DELETE | `/api/v1/customer/history/{historyId}` | Delete one medical history record (returns 400 if it does not exist; demo-environment behavior, no audit trail) |

## Treatment Record `/api/v1/treat`

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/treat/` | Create a treatment record (`customerId` must point to an existing patient); may carry `staffId` to attribute the treating physician (optional, echoed back as `staffName`); the pulse presentation supports cun/guan/chi (寸/关/尺) per-position fields for both hands (`pulseCunLeft`/`pulseGuanLeft`/`pulseChiLeft`/`pulseCunRight`/`pulseGuanRight`/`pulseChiRight`, plus the overall `pulse`/`pulseLeft`/`pulseRight` and the supplementary `pulseInfo`) |
| GET | `/api/v1/treat/{id}` | Query by id |
| GET | `/api/v1/treat/customer/{id}` | Query all treatment records by patient id |
| GET | `/api/v1/treat/customer/name/{name}` | Query by patient name (the name must be unique) |
| GET | `/api/v1/treat/visited?ids=` | Batch-query visit counts by patient (`ids` comma-separated, required; missing returns 400): counts the treats table grouped by patient and returns `[{ customerId, visits }]`; patients without treatment records (including non-existent ids) do not appear in the result. This is the basis for the "first visit / return visit" badge on the scheduling page — visits ≥ 1 = return visit, no record = first visit (treats has no per-item dimension, counting is per patient; the treatment-course card marker takes precedence) |
| GET | `/api/v1/treat/history` | Paged query of a patient's treatment history (`customerId` required, descending by creation time) |
| GET | `/api/v1/treat/fuzzy` | Fuzzy search (pass `id` for a single lookup; otherwise narrow down patients by `name` / `age` / `phone` and page the results) |
| PUT | `/api/v1/treat/` | Full update of the treatment record by id: id/customerId required and must exist; fields follow the request body (omitting `staffId` clears the attribution); the response includes the patient name/age/gender and the treating physician attribution (evicts the findById cache after the update) |
| DELETE | `/api/v1/treat/{id}` | Delete a treatment record |

For field meanings see [Data model · treats table](/server/data-model#treats-诊疗单). Acupuncture prescription fields (`acuMethod` needling method ≤30 characters, `retentionMinutes` needle retention minutes 1-240 nullable, `manipulation` manipulation technique ≤30 characters, `moxaMethod` moxibustion method ≤60 characters (e.g. "moxa stick suspended moxibustion, 15 minutes per acupoint"), `acuCourse` course/frequency ≤100 characters) are saved with the treatment record on create/update; out-of-range values return 400; blank text is treated as empty. See TreatAcuHttpTest.

## Feedback and Retrospective `/api/v1/review`

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/review/` | Save the daily summary (upsert keyed on midnight of the current day; same-day saves overwrite) |
| GET | `/api/v1/review/day?day=` | Query the daily summary by date (returns an empty template if none exists) |
| POST | `/api/v1/review/staff` | Create a staff daily retrospective (passing only `name` auto-fills the staff id) |
| POST | `/api/v1/review/customer` | Create a patient follow-up record (passing only `name` auto-fills the patient id; `day` is the follow-up day) |
| GET | `/api/v1/review/staff/list/day?day=` | Query all staff retrospectives for a day |
| GET | `/api/v1/review/customer/list/day?day=` | Query all patient follow-ups for a day |
| GET | `/api/v1/review/customer/list/customer?customerId=` | Query all follow-up plans for a patient (descending by follow-up day, the first entry is the nearest scheduled one; `advice` is the follow-up reason, used by the record page for next-follow-up statistics/reminders) |
| POST | `/api/v1/review/staff/bulk` | Batch-save staff retrospectives (written entry by entry) |
| POST | `/api/v1/review/customer/bulk` | Batch-save patient follow-ups |
| DELETE | `/api/v1/review/staff/{id}` | Delete a staff retrospective |
| DELETE | `/api/v1/review/customer/{id}` | Delete a patient follow-up |
| GET | `/api/v1/review/{id}` | Query a daily retrospective summary by id (returns 400 if it does not exist) |
| DELETE | `/api/v1/review/{id}` | Delete a daily retrospective summary by id (returns 400 if it does not exist; demo-environment behavior, no audit trail) |

## Service Card `/api/v1/item`

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/item/` | Create a service card (unique name, price, description; `durationMinutes` per-session duration in minutes is optional — display-only, not involved in settlement) |
| PUT | `/api/v1/item/` | Update a service card |
| GET | `/api/v1/item/page` | Paged query (the current implementation does not filter by conditions and returns everything) |
| GET | `/api/v1/item/{id}` | Query by id |
| GET | `/api/v1/item/name/{name}` | Query by name (`{name}` is a path variable; locked down by PathLookupHttpTest) |
| DELETE | `/api/v1/item/{id}` | Delete; returns the service card as it was before deletion |

## Staff and Attendance (Clinic Operations) `/api/v1/staff`

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/staff/` | Create a staff member (unique account, password 2-20 characters, phone number deduplicated; `title` job title optional — the scheduling page's physician roster = active staff whose title contains "医师" (physician); `famousLevel` famous-doctor level 0-3, where 0 is ordinary and 1-3 are famous doctors; invalid values return 400) |
| PUT | `/api/v1/staff/` | Update a staff member (including `password` changes the password as well; `title` is updated in sync; `famousLevel` follows the same rules as create, passing null leaves it unchanged) |
| GET | `/api/v1/staff/page` | Paged query of staff (descending by `famousLevel`, ascending by creation time within the same level) |
| GET | `/api/v1/staff/{id}` | Query by id |
| GET | `/api/v1/staff/name/{name}` | Query by name (`{name}` is a path variable; locked down by PathLookupHttpTest) |
| GET | `/api/v1/staff/phone/{phone}` | Query by phone number (`{phone}` is a path variable and does not interfere with the by-name lookup; locked down by StaffLookupTest + PathLookupHttpTest) |
| DELETE | `/api/v1/staff/{id}` | Delete a staff member |
| POST | `/api/v1/staff/sign` | Sign in / sign out (`signType`: 1 = clock in, 0 = clock out) |
| GET | `/api/v1/staff/timesheet/today` | Today's attendance for the current user (clock-in/out details and total duration) |
| GET | `/api/v1/staff/timesheet/month?month=` | Monthly attendance statistics for all staff (1-12, current year only) |
| GET | `/api/v1/staff/timesheet/week?weekDate=` | Weekly shift comparison: the week containing `weekDate` (Monday through Sunday) as staff × 7 days, planned shifts (`staff_shifts` by weekday) compared against actual clock-ins (earliest clock-in / latest clock-out per day, rounded to whole hours; past days missing a clock-out count as 0); an empty `weekDate` takes the current week, invalid formats return 400 |
| POST | `/api/v1/staff/leave/` | Submit a leave request: `staffId` + `leaveType` (0 sick leave / 1 personal leave) + `reason` + `startTime`/`endTime` required, the end must not be earlier than the start; the response includes the joined staff name. Recording convention: it does not change the staff member's login status and there is no approval flow |
| GET | `/api/v1/staff/leave/page` | Paged leave requests (current, pageSize, staffId optional), staff name joined, descending by id |
| DELETE | `/api/v1/staff/leave/{id}` | Delete a leave record (demo-environment behavior, no audit trail) |

::: tip Attendance conventions
Durations are counted in whole hours; anything under 1 hour is not shown. With multiple clock-ins on the same day the earliest is taken, and with multiple clock-outs the latest; if someone has clocked in but not out, the current time is used. See the [admin console](/web) page documentation for details.
:::

## Staff Shift Table `/api/v1/shift` (login required)

Recurring shift table (weekly cycle): staff × weekday (`weekday` 1 = Monday … 7 = Sunday) × `HH:mm` time range; each staff member has at most one shift per weekday.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/shift/` | Add a shift: `staffId` + `weekday` (1-7) + `start`/`end` (HH:mm, start < end) required; the staff member must exist and the staff-plus-weekday combination is unique (duplicates return 400 “员工周X已有班次” — the staff member already has a shift on this weekday); the response includes the joined staff name and weekday text |
| PUT | `/api/v1/shift/` | Full update by id; changing the staff member/weekday to collide with an existing shift returns 400 |
| GET | `/api/v1/shift/page` | Paged shifts (current, pageSize, optional staffId/weekday filters), ascending by staff member then weekday |
| GET | `/api/v1/shift/staff/{staffId}` | A staff member's shifts for the whole week (ascending by weekday; the data entry point for attendance and scheduling) |
| GET | `/api/v1/shift/{id}` | Shift details (staff name joined) |
| DELETE | `/api/v1/shift/{id}` | Delete a shift (demo-environment behavior, no audit trail) |

::: tip Shift table conventions
The shift table is for scheduling reference only; attendance still follows signs: deleting or adjusting shifts does not write back to any clock-in records, and there is currently no linkage with sign-in, leave, or appointments.
:::

## Patient Service-Selection Kiosk `/api/v1/kiosk` (kiosk.md K2)

Unauthenticated (front-desk large-screen self-service entry, see the [patient service-selection design](/design/kiosk)); read-only browsing plus three write entry points: ordering, self-service scheduling, and openid binding.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| GET | `/api/v1/kiosk/items` | List of listed service cards (enabled=1, ascending by sort+name), exposing only id/name/price/cover/description |
| POST | `/api/v1/kiosk/orders` | Place an order: body `{ phone, name?, itemIds[] }`. The phone number must match `1\d{10}`; service cards must exist and be listed (everything is validated up front before the record is created); a patient record is created idempotently by phone number (when no name is given it defaults to “到店客人” — walk-in guest), and one `status=0` order is written per item with a snapshot of the service card's current price. Returns `{ customerId, customerName, orders[{id,itemId,itemName,price}], totalFee }` |
| POST | `/api/v1/kiosk/appointments` | Self-service scheduling: body `{ phone, name?, itemId?, startTime }`. The slot must be `yyyy-MM-dd HH:mm` **on the hour**, within `09:00–17:00`, and later than the current moment; the service card is optional (decided in person at the clinic) but must exist and be listed; rate limiting allows at most 1 appointment with `status IN (0,1)` per phone number per day (exceeding returns 400 “当日已有预约，请到店或致电改约” — an appointment already exists today, please visit or call to change it); a patient record is created idempotently by phone number, and an appointment is written with `duration=60`, `status=0`, `staffId=null`, and the remark “小程序自助” (mini program self-service). Returns `{ customerId, customerName, appointmentId, startTime, itemName?, status: 0 }`. For the conventions see the [mini program self-service scheduling design](/design/app-booking) |
| POST | `/api/v1/kiosk/sms-code` | SMS verification code (shared by `sms_verified` card lookup and `openid_only` binding; `off` mode returns 400): body `{ phone }`; issues a 6-digit code stored in `sms_codes` (valid for 5 minutes, one-time use). The system has no SMS gateway, so code issuance is a server-side stub — the code only goes to the database and the log (`SMS_CODE_DEMO phone=… code=…`) and is never exposed externally |
| POST | `/api/v1/kiosk/openid-session` | Mini program login exchanged for an openid (available only in `openid_only` mode; other modes return 400): body `{ code }` (the `wx.login` temporary credential); the server exchanges it for an openid via WeChat jscode2session and returns `{ openid }`; requires `WECHAT_APP_ID`/`WECHAT_APP_SECRET` to be configured — when they are not, it rejects explicitly with 400 (no silent fallback); WeChat errors are mapped to 400; every attempt (including rejections) is written to the `card_lookup_logs` audit; the `session_key` never leaves the server |
| POST | `/api/v1/kiosk/card-lookup` | Card lookup by phone: body `{ phone?, openid?, smsCode? }`; follows `sinomed.phone-lookup.mode` to use openid or SMS two-factor; a hit returns `{ customerId, customerName, cards: [{ cardId, itemId, itemName, totalTimes, remainingTimes, status, validUntil }] }`; on a miss or rejection no card data is returned at all, and every attempt (including rejections) is written to the `card_lookup_logs` audit. For the conventions see the [card lookup by phone design](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定) |
| POST | `/api/v1/kiosk/openid-bind` | openid self-service binding (available only in `openid_only` mode; other modes return 400; decided on the user's behalf 2026-10-15): body `{ openid, phone, smsCode }`; after ownership of the phone number is proven with a one-time SMS code, the openid is bound to the patient record for that phone number (no front desk, no desktop needed). Repeated binding to the same record succeeds idempotently; if the openid is already bound to another record it is rejected with 400 (no silent rebinding — the front desk must handle it); a missing openid / missing phone number or code / wrong code / no patient record for the phone number all return 400. Returns `{ message }`; every attempt (including rejections) is written to the `card_lookup_logs` audit (result `bind_ok` / `bind_conflict`) |
| POST | `/api/v1/kiosk/my-appointments` | Self-service appointment lookup by phone (cross-device): body `{ phone, smsCode }` (available only in `sms_verified` mode, the same one-time codes as card lookup); returns `{ appointments: [{ appointmentId, startTime, itemName, status }] }` — the phone number's **not yet expired** (from midnight today) not-yet-arrived appointments (status=0), newest bookings first, an empty array when there are none; every attempt (including rejections) is written to the `card_lookup_logs` audit. For the conventions see the [card lookup by phone design](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定) (the same identity lookup channel) |
| POST | `/api/v1/kiosk/appointment-cancel` | Self-service appointment cancellation by phone (decided on the user's behalf 2026-10-16; `app-booking.md` ruling 2's premise "cancellation verifies no identity" lapsed once two-factor verification landed; available only in `sms_verified` mode): body `{ phone, smsCode, appointmentId }`, the code is one-time (a code already spent on lookup must be re-issued for cancellation; a missing appointment id does not burn the code); only the caller's own status=0 not-yet-expired appointment may be cancelled (0→9); not the owner / already received or skipped / already cancelled / already expired / unknown appointment are all refused with 400 (no silent paths); after cancellation the slot is released per the "one booking per slot" rule and the same-day frequency cap lifts accordingly. Returns `{ message }`; every attempt (including rejections) is written to the `card_lookup_logs` audit (result `cancel_ok` / `missing_appointment` / `not_owner` / `not_cancelable`) |

## Print Templates `/api/v1/print` (desktop.md D7)

Unauthenticated (blank layouts with no business data; the desktop workstation shell pulls templates even when not logged in, see the [desktop design](/design/desktop)).

| Method | Path | Purpose |
| ---- | ---- | ---- |
| GET | `/api/v1/print/templates` | All whitelisted templates (prescription / receipt) with a content-digest version `{ version, templates[{ name, content }] }`; the version is the first 16 hex characters of the content's SHA-256 and stays the same as long as the content does. Template resolution: `data/printtemplates/{name}.html` (`PRINT_TEMPLATES_DIR` is configurable) — the override directory wins, falling back to the layout built into the jar when missing, so updating a template only requires changing the server-side file, no shell release needed |
| GET | `/api/v1/print/receipt/{settlementId}` | **Settlement receipt overprint (login required)**: fills the 80mm receipt template from the settlement and returns printable HTML directly (`text/html`). The receipt number is `S+<settlement id>`; for a visit-card write-off (actual amount 0) the amount column reads “次卡抵扣” (visit-card write-off); the header clinic name can be overridden with the `sinomed.receipt.clinic-name` config; patient/staff names are HTML-escaped. Returns 400 if the settlement does not exist |

## Orders `/api/v1/order` (kiosk.md K3, login required)

> Multi-item: order line items go to the `order_item` table (name/price are snapshots from the moment of ordering, qty≥1, subtotal = unit price × quantity); the order endpoints uniformly join out `items[]`; for legacy orders without line-item rows, a single row of quantity 1 is derived from `itemId+price`. Semantics of `orders.price`: single row = the row subtotal, multiple rows = the sum of the row subtotals.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/order/` | Front-desk order creation: `customerId` required (the patient must exist), `staffId` optional to record who created it; single item passes `itemId` (the service card must exist and be enabled), multiple items pass `items[{itemId,qty}]` (qty≥1, validated row by row, `price` = sum of the row subtotals, `itemId` records the first row); prices take a snapshot of the service card's current price, and the order is written with `status=0` pending check-in |
| PUT | `/api/v1/order/remark` | Order-level remark: body `{id, remark}`, ≤200 characters; an empty string/null clears it; overlong values return 400 |
| GET | `/api/v1/order/page` | Paged query (current, pageSize, optional status; optional pending where `true` = only pending settlement 0/1), descending by creation time (then by id within the same second), joining out the patient name/phone, service card name, line items `items`, and the settlement payment method `payType` |
| GET | `/api/v1/order/{id}` | Details (patient name, service card name, line items `items`, and remark `remark` joined) |
| PUT | `/api/v1/order/status` | Status transitions: `0→1`, `1→2`, and `0/1→9` cancel; other combinations are rejected (the body carries id and status) |
| GET | `/api/v1/order/summary` | Summary by patient: `customerId` required, returns `{ orders, amount }` (order count and total amount) |
| DELETE | `/api/v1/order/{id}` | Delete; only orders with `status=9` (cancelled) can be deleted, together with their line items |

## Appointments `/api/v1/appointment` (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/appointment/` | Create an appointment: `customerId` + `startTime` required, `itemId`/`staffId`/`duration`/`remark` optional; new appointments always get `status=0` not yet arrived; **at most 1 booking per slot** — when an appointment with `status IN (0,1)` already exists for the same physician on the same day and hour (floored to the hour bucket), it returns 400 “该时段已约满（每时段限约 1 位），请换医师或时段” (this slot is fully booked, at most 1 booking per slot, please pick a different physician or slot); an empty `staffId` (self-service booking, assigned on arrival) belongs to the same empty group and is mutually exclusive within it |
| GET | `/api/v1/appointment/page` | Paged query (current, pageSize, optional status; optional date `yyyy-MM-dd` to filter by day), joining out the patient name/phone, service card name, and staff name |
| PUT | `/api/v1/appointment/status` | Status transitions: only `0→1` (arrived and checked in, after which the order is created from the treatment page), `0→8` (skipped), and `0→9` (cancelled) are allowed; skipped/cancelled are terminal states and other combinations are rejected. `0→1` triggers the **automatic queue-calling linkage** (docs/tablet.md "到号自动叫号"): checking in immediately writes one `queue_calls` record (number = the day's check-in sequence zero-padded to two digits, room = the treating physician's name + “诊室” (consultation room) or “待分配诊室” (room to be assigned) when unassigned, name masked, broadcast to all screens); skipping or cancelling does not call the queue |
| DELETE | `/api/v1/appointment/{id}` | Delete; only appointments with `status=9` (cancelled) can be deleted |

::: tip Appointment conventions
A slot is the start time + duration (minutes); statuses: `0` not yet arrived, `1` checked in, `8` skipped, `9` cancelled (8/9 are terminal). At most 1 booking per slot (per the admin-booking prototype): a request is rejected when a not-yet-arrived or checked-in appointment already exists for the same physician on the same day and hour; cancelling or skipping automatically releases the slot; self-service bookings (empty staffId) and physician-assigned ones belong to different groups and do not affect each other. After check-in, start the treatment from the treatment page — the appointment status is not written back automatically from the treatment record.
:::

## Stored Value `/api/v1/recharge` (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/recharge/` | Top up stored value: `customerId` + `money` (>0); the patient must already have a record; writes one positive ledger entry |
| GET | `/api/v1/recharge/balance` | Patient stored-value balance: the sum over the ledger (`customerId` required), 0 when there are no entries |
| GET | `/api/v1/recharge/page` | Paged ledger (current, pageSize, optional customerId), patient name joined; rows with negative `money` are deductions from stored-value payments |

## Charging `/api/v1/settlement` (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/settlement/` | Take payment and settle: `orderId` + `payType` (`1` stored value / `2` WeChat / `3` Alipay / `4` cash / `5` visit-card write-off); the order status goes `0/1 → 2` completed; stored-value payments check the balance and write a negative ledger entry, returning 400 on insufficient funds; a visit-card write-off deducts 1 visit from the order's service card and records the actual amount as 0, returning 400 when no card with valid remaining visits exists; duplicate charging is prevented by checking "the most recent settlement is not refunded" (after a refund and re-charge, one order can have multiple settlements) |
| GET | `/api/v1/settlement/page` | Paged settlements (current, pageSize), patient name joined, newest first |
| GET | `/api/v1/settlement/report/month?month=` | Monthly charging report: `month=yyyy-MM` required (invalid format / non-existent month returns 400); aggregates the month's settlements by settlement time — `totalCount` settlement count, `totalMoney` total actual amount received (yuan), `cardCount` visit-card write-off settlement count, `days[]` per-day counts/actual amounts (only days with settlements, ascending), `payTypes[]` payment-method breakdown (`payType`/`payTypeText`/count/actual amount, only methods that occurred, ascending); stored-value top-ups are not counted as revenue |
| GET | `/api/v1/settlement/report/trend?months=` | Business trend: `months` 1-24 optional (default 6, out-of-range returns 400); aggregates the actual amount / settlement count / visit-card write-off count / treatment volume month by month over the last N months including the current one (`rows[]`: `month`/`money`/`count`/`cardCount`/`treatCount`), zero-filled for months without data, months ascending |
| POST | `/api/v1/settlement/{id}/refund` | Refund: `reason` required, ≤100 characters; a settlement can be refunded only once (uniqueness of `refund_logs.settlement_id` as the backstop; an already refunded settlement returns 400 “该结算已退款” — this settlement has already been refunded); a refund = the whole settlement refunded via the original `payType` — stored value gets a compensating positive ledger entry (note “退单 #结算id”), a visit card voids that order's write-off entry and restores the visits (amount recorded as 0), WeChat/Alipay/cash only record the refund; the order goes `2 → 1` accepted and can be charged again; returns the refund view (patient/operator names joined) |
| GET | `/api/v1/settlement/reconciliation?date=` | Daily reconciliation: `date=yyyy-MM-dd` required (missing/invalid returns 400); aggregates by calendar day the payments (count/actual amount/`incomePayTypes[]`/visit-card write-offs listed separately), top-ups (count/amount listed separately and not counted as revenue, including only real top-ups — stored-value payment deductions go to the payment side and refund compensations to the refund side), and refunds (count/amount/`refundPayTypes[]` by original payment method + line items); `netMoney` = actual amount received - refunds, `cashNetMoney` = cash payments - cash refunds (for cash-drawer checking) |

::: tip Charging conventions
The actual amount received takes the order's price snapshot (an empty price is charged as 0); WeChat/Alipay are **on-site scan-to-pay** (decided 2026-10-12) — the settlement counter displays the merchant's payment QR code (served as an image by `/api/v1/pay-qrcode/{method}`; a missing image gets a 404 and the page says so honestly), the patient scans and pays outside the system, and the system only books the amount under the chosen method without invoking a real cashiering gateway; receipts go through the server-side overprint at `/api/v1/print/receipt/{settlementId}` (80mm HTML, printed from the browser); a visit-card write-off requires the patient to hold a card with valid remaining visits for this order's service card (with multiple cards, earlier-issued ones are deducted first); **prescription processed-herb lines entering settlement: decided not to do** — charging is based on service-card order settlement, and prescription pricing is a trial-calculation/advisory convention (the processed-herb lines in the billing prototype are illustrative). The payment method of a settled order is joined out as `payType` from the order pagination. The monthly charging report (`GET /report/month`) counts only settlements — stored-value top-ups themselves are not revenue, visit-card write-offs have an actual amount of 0 and are listed separately as `cardCount`. Refunds and daily reconciliation are implemented ([reconciliation.md](../design/reconciliation.md)): refunds return the whole settlement via the original route, one refund per settlement, reason required; daily reconciliation aggregates three sections by calendar day, top-ups are listed separately and not counted as revenue, and the cash net amount is for cash-drawer checking; no partial refunds / cross-day reversals / invoices / real online-payment refunds.
:::

## Payment QR Codes `/api/v1/pay-qrcode` (login required)

> Serves images for on-site scan-to-pay: reads the `{sinomed.pay.qrcodes-dir:data/payqrcodes}/` directory (the same data/ directory convention as printtemplates — the merchant drops an image in and it takes effect, no rebuild needed); purely serves images with no payment gateway and no callbacks; the money changes hands outside the system. Payment QR codes are merchant assets and are not served to unauthenticated callers.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| GET | `/api/v1/pay-qrcode/{method}` | Get the merchant's payment QR code image: `method` is whitelisted to `wechat`/`alipay` (anything else always gets a 404, closing off path concatenation); looks for `{method}.png`/`.jpg`/`.jpeg` in order and serves `image/png`/`image/jpeg` per the extension; a missing file gets a 404 — the page honestly reports it as unconfigured rather than faking a code. img tags cannot carry the Bearer token, so the page fetches the image via fetch+blob (same convention as receipt reprinting) |

## Visit Cards `/api/v1/card` (login required)

Patient-held cards (count cards / treatment-course cards — service items counted by course such as tuina and moxibustion follow the treatment-course card convention): issuing a card grants the full visit count, and settlement deducts against the service card and writes a write-off ledger entry; `validUntil` expiry is optional = valid forever, and expired cards do not participate in write-offs (see [data-model](/server/data-model) for the conventions).

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/card/` | Issue a card: `customerId` + `itemId` + `totalTimes` (≥1) required, `sourceOrderId` optional (traces the purchasing order), `validUntil` optional = valid forever (milliseconds; expired cards do not participate in write-offs); remaining visits = total visits, status active |
| GET | `/api/v1/card/list` | List a patient's cards (`customerId` required), newest cards first, patient name and service card name joined |
| GET | `/api/v1/card/customers?ids=` | Batch-query cards by patients (`ids` comma-separated; an empty list returns `[]`), patient names and service card names joined; aggregate scenarios such as the treatment-course markers on scheduling grid cells pull everything at once instead of querying per patient |
| PUT | `/api/v1/card/{id}/status?status=` | Deactivate / restore (`1` active / `0` deactivated); deactivated cards do not participate in write-offs |
| GET | `/api/v1/card/{id}/usages` | List a card's write-off records (newest first): one per settlement deduction, containing `timesUsed` (which use, starting at 1), `orderId`, `staffId/staffName` (the serving staff, taken from the order's staffId), and `createTime`; returns 400 if the card does not exist |
| POST | `/api/v1/card/{id}/adjust` | Gift visits (treatment-course card marketing): `times` (1-99) + `reason` (required, ≤100 characters) required; for an active card, N is added to both total/remaining and an adjustment entry is written; a deactivated card returns 400 (restore it first); expiry is not blocked |
| POST | `/api/v1/card/{id}/transfer` | Transfer card (treatment-course card marketing): `customerId` (target patient) + `reason` (required, ≤100 characters) required; reassigns the owning patient (remaining visits/expiry unchanged) and writes an adjustment entry; a deactivated card / non-existent target patient / card already held by that patient all return 400 |
| GET | `/api/v1/card/{id}/adjusts` | List a card's adjustment entries (newest first): the audit trail of gifted visits/transfers, containing `type` (1 gift / 2 transfer), `times`, `customerId/customerName` (transfer target), `reason`, `staffId/staffName` (operator, taken from the currently logged-in account), and `createTime`; returns 400 if the card does not exist |

The deduction itself does not go through this group: choose `payType=5` on the settlement endpoint, which deducts 1 visit against the order's service card and writes one write-off ledger entry in the same transaction (no entry without an order — manual deductions are not supported; write-offs happen together with settlement).

## Prescriptions `/api/v1/prescription` (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/prescription/price` | Prescription trial calculation (pricing only, no prescription written): the body passes `herbs` (herb/weight) + `doses`; prices in real time against the herb dictionary and returns `{ totalFen, perDoseFen, doses, herbCount, pricedHerbCount, unknownHerbs[] }`; herb names not in the dictionary are not charged |
| POST | `/api/v1/prescription/compatibility` | Compatibility review: body `{ herbs: ["herb name", …] }`; checks against the classic 十八反 (eighteen incompatibilities, contraindicated) / 十九畏 (nineteen antagonisms, use with caution); free-text herb names match by alias containment (“法半夏” hits “半夏”); returns `{ checked, findings[] }` (an empty `findings` means no incompatibility was found); the notice does not block — whether to proceed is the physician's judgment |
| POST | `/api/v1/prescription/allergy-check` | Allergy review: body `{ customerId, herbs: ["herb name", …] }`; checks against the patient's allergy history (`customer_histories` type=0) — a herb name (≥2 characters; single characters like “参” are excluded to prevent false positives) contained in the record's original text is a hit; returns `{ customerId, checked, findings[] }` (each finding contains `herb`/`historyId`/`content`, the history text; a herb hitting multiple history records yields one finding per record; an empty `findings` means no hit); past history does not participate; unknown patient / empty list returns 400; the notice does not block — whether to proceed is the physician's judgment |
| POST | `/api/v1/prescription/dose-check` | Dosage review: body `{ herbs: [{ herb, weight }] }`; checks grams per dose against the herb dictionary's per-dose gram ceiling (`herbs.dose_max`, optional = no warning): a `weight` **strictly greater** than the ceiling is a hit (equal values do not warn); returns `{ checked, limited, findings[], unmatched[] }` (`checked` is the number of entries that participated after trimming blanks; `limited` is the number of entries with a ceiling set, counting repeated herbs of the same name; each finding contains `herb`/`weight`/`limit`; herb names not in the dictionary go to `unmatched` with no reference; entries without a ceiling do not participate); an empty list returns 400; the notice does not block — whether to proceed is the physician's judgment |
| POST | `/api/v1/prescription/` | Write a prescription: `customerId` + `herbs` (at least 1 herb: `herb` name + `weight` in grams, optional `special` special decoction method / `process` processing) required; `treatId`/`staffId`/`reviewStaffId` (pharmacist review)/`checkStaffId` (dispensing check; the signature is stored and the staff id validated)/`doses` (default 7)/`usage` (decoction method)/`takeMethod` (administration)/`remark` optional; `prescriptionType=1` means a paste (recorded as “待制作” pending preparation, with `craft` recording the finishing method such as 炼蜜 (refined honey) / 清膏 (clear paste) / 糖膏 (sugar paste) / 阿胶收膏 (Ejiao finishing); no bag count); with `prescriptionType=0` (default) and `decoction=true` it is a decoction service (bag count = doses, recorded as “待煎” pending decoction, `deliveryType` 0 pickup / 1 same-city delivery stored with the prescription and ignored when there is no decoction service); returns the created prescription (including herbs) |
| PUT | `/api/v1/prescription/{id}/decoction?status=` | Decoction service status flow (decoctions only): only 待煎 (pending decoction, 1) → 可取 (ready for pickup, 2) → 已取 (picked up, 3) may advance in order; no decoction service selected (0) / going back / skipping returns 400; the response is the full prescription view after the transition. The decoction bag count = doses and the service fee = bags × 3 yuan (300 fen per bag) is computed on the fly, not stored, and returned as `decoctionFeeFen` with the view (advisory only — charging is still based on service-card order settlement) |
| PUT | `/api/v1/prescription/{id}/paste?status=` | Paste pickup status flow: only 待制作 (pending preparation, 1) → 可取 (ready for pickup, 2) → 已取 (picked up, 3) may advance in order; a non-paste (`prescriptionType=0`) returns 400 “不是膏方” (not a paste); going back or skipping returns 400 “流转无效” (invalid transition); the response is the full prescription view after the transition |
| POST | `/api/v1/prescription/{id}/dispense` | Pharmacy dispensing: 待发药 (pending dispensing, 0) → 已发药 (dispensed, 1), one-way, writing a dispensing ledger entry (`dispense_logs`); the operator is taken from the currently logged-in account (filled in automatically, leave the path empty); the body may optionally carry `{ note }` (≤100 characters); dispensing an already-dispensed prescription returns 400 “处方已发药” (the prescription has already been dispensed); when a decoction/paste reaches “已取” (picked up) it is automatically marked dispensed (the ledger note records the source), so there is no need to click dispense again |
| GET | `/api/v1/prescription/page` | Paged query (current, pageSize, optional customerId), patient name, physician name, and herbs joined, descending by id |
| GET | `/api/v1/prescription/dispense/page` | Pharmacy workbench pagination: `dispenseStatus` optional (0 pending dispensing / 1 dispensed), `customerKeyword` optional fuzzy match on patient name/phone, descending by id; joins the patient name and phone, type/doses/herb count, and the decoction/paste status text |
| GET | `/api/v1/prescription/{id}` | Prescription details (including herbs ordered by sort) |
| DELETE | `/api/v1/prescription/{id}` | Delete a prescription (together with its herbs; demo-environment behavior, no audit trail) |

## Syndrome Prescription Templates `/api/v1/prescription/template` (login required)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/prescription/template/` | Create a template: `name` (syndrome name, 1–20 characters, unique) + `herbs` (at least 1: herb + weight, special optional) required; `doses` (default 7)/`decoction` (0 not needed / 1 decoction service, default 0)/`usage`/`remark`/`enabled` (default 1) optional |
| PUT | `/api/v1/prescription/template/` | Full update by id (herbs fully replaced); renaming to collide with another template returns 400 |
| GET | `/api/v1/prescription/template/enabled` | Listed templates (enabled=1, ascending by id, herbs included) — the data entry point for "apply template" on the prescription-writing page |
| GET | `/api/v1/prescription/template/page` | Paged query (descending by id, including disabled; optional fuzzy `name` filter), herbs joined |
| GET | `/api/v1/prescription/template/{id}` | Template details (including herbs ordered by sort; returns 400 if it does not exist) |
| DELETE | `/api/v1/prescription/template/{id}` | Delete a template (together with its herbs; does not affect written prescriptions; demo-environment behavior, no audit trail) |

## External Therapy Prescription Templates `/api/v1/therapy-template` (login required)

Common combinations for external therapies such as ear-point seed pressing, acupoint application, moxibustion, and cupping (method + point selection sites + course), following the same conventions as syndrome templates: only suggested values are stored; "apply external therapy template" on the treatment page pre-fills them, after which they can be freely edited with the treatment record (nothing is written back to the template), and disabled templates do not appear on the treatment page.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/therapy-template/` | Create a template: `name` (template name, 1–20 characters, unique, e.g. “失眠·耳穴压豆”) + `method` (external therapy method, ≤10 characters) required; `target` (point selection/sites)/`course` (course/frequency)/`note` (precautions) each optional up to 100 characters; `enabled` (default 1) |
| PUT | `/api/v1/therapy-template/` | Full update by id; renaming to collide with another template returns 400 |
| GET | `/api/v1/therapy-template/enabled` | Listed templates (enabled=1, ascending by name) — the data entry point for "apply external therapy template" on the treatment page |
| GET | `/api/v1/therapy-template/page` | Paged query (descending by id, including disabled; optional fuzzy `name` / exact `method` filters) |
| GET | `/api/v1/therapy-template/{id}` | Template details (returns 400 if it does not exist) |
| DELETE | `/api/v1/therapy-template/{id}` | Delete a template (does not affect existing treatment records; demo-environment behavior, no audit trail) |

::: tip Prescription conventions
Herb names are free text — compatibility review (the eighteen incompatibilities/nineteen antagonisms, static rules), allergy review (matching against the patient's allergy history text), and pricing (real-time trial calculation against the herb dictionary) are implemented; review notices do not block, pricing keeps no snapshot and follows dictionary price changes, and herb names not in the dictionary are honestly labeled “未比价” (not priced) and not charged; the decoction service pickup is implemented (bag count = doses, 待煎→可取→已取 pending decoction → ready for pickup → picked up, one-way flow, no rush jobs or rollback); pastes are implemented (`prescriptionType=1`, written as “待制作” pending preparation with `craft` recording the finishing method, counted per batch without decoction bag counts, pickup flow 待制作→可取→已取 pending preparation → ready for pickup → picked up, one-way, independent of the decoction service); pharmacy dispensing is implemented (`dispense_status` 0 pending/1 dispensed, one-way, + the `dispense_logs` ledger, `POST /{id}/dispense` and the `GET /dispense/page` workbench, with decoction/paste “已取” picked up automatically marking dispensed); syndrome prescription templates are implemented (unique template names, suggested values only, freely added or removed after applying without writing back, disabled ones absent from the prescription-writing page); external therapy prescription templates are implemented (`/api/v1/therapy-template`, applying one on the treatment page brings in the method/point selection/course suggestions, same conventions as syndrome templates); the formula library is implemented (classic formula reference, searched by formula name or pinyin on the prescription-writing page to bring in the whole formula with one click, freely adjusted afterwards without writing back to the library); inventory is a planned feature; dosages are grams per single dose (decimals allowed), and `special` records decoction methods such as 先煎 (decoct first), 后下 (added late), and 包煎 (wrapped decoction).
:::

## Formula Library `/api/v1/formula` (login required)

A classic formula reference library (unique formula names; `pinyin` is the full-pinyin lowercase search code). Division of labor with syndrome templates: formulas store only literary reference values — "formula name + herb composition + source/efficacy" — without prescription-writing suggestions such as doses or decoction service; the "formula library" on the prescription-writing page searches by formula name or pinyin to bring in the whole formula, which can then be freely adjusted with the prescription and is never written back to the library.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/formula/` | Add a formula: `name` (1–20 characters, unique) + `pinyin` (full-pinyin lowercase, lowercased automatically on entry) + `herbs` (at least 1: herb + weight, special optional) required; `source` (origin)/`indication` (efficacy and indications) optional |
| PUT | `/api/v1/formula/` | Full update by id (herbs fully replaced); renaming to collide with another formula returns 400 |
| GET | `/api/v1/formula/page` | Paged query (descending by id; `keyword` fuzzy-matches the formula name **or pinyin code**, e.g. “逍遥” or “xiaoyao”), herbs joined |
| GET | `/api/v1/formula/{id}` | Formula details (including herbs ordered by sort; returns 400 if it does not exist) |
| DELETE | `/api/v1/formula/{id}` | Delete a formula (together with its herbs; does not affect written prescriptions; demo-environment behavior, no audit trail) |

## Herb Dictionary `/api/v1/herb`

Login required; the pricing reference for prescription calculation. Herb names are unique and match prescription herb names **by exact name** (“炙甘草” does not match “甘草”); the price is fen per gram (int), and the total = Σ round(fen-per-gram × grams per dose) × doses.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/herb/` | Add an herb (unique name, price in fen per gram >0; a duplicate name reports “药材已收录” (herb already recorded); `stockMin`/`stockMax` stock floor/ceiling optional = no warning) |
| PUT | `/api/v1/herb/` | Update an herb (price/name changes; duplicate-name checking excludes itself; `stockMin`/`stockMax` are wholly overwritten on update, passing null clears them) |
| DELETE | `/api/v1/herb/{id}` | Delete an herb (demo convention with no reference check; affected herbs become “未比价” (not priced) afterward) |
| GET | `/api/v1/herb/page` | Paged query (current, pageSize, keyword name-contains filter), ascending by name |

## Processed Herb Inventory `/api/v1/herb-stock` (login required)

Only ledger entries are recorded; current stock = total stock-in - total stock-out (grams); stock-out consumes batches first-expiry-first-out (FEFO), and batch expiry dates drive near-expiry warnings. Ledger-book convention — no purchase documents or settlement involved.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/herb-stock/` | Register stock-in/stock-out: `herbId` + `type` (1 stock-in / 0 stock-out) + `quantity` (grams, positive integer) required; stock-in may carry `expiry` (yyyy-MM-dd, not earlier than today)/`supplier` (≤50 characters)/`note` (≤100 characters); stock-out may not exceed current stock (400 “出库数量超过当前库存：N 克” — the stock-out quantity exceeds current stock: N grams); stock-out entries carry no expiry |
| GET | `/api/v1/herb-stock/page` | Paged ledger (current, pageSize, optional herbId/type filters), herb name joined, descending by id |
| GET | `/api/v1/herb-stock/balance?expiryWithinDays=30` | Stock balance: for each herb, the current grams + the earliest unconsumed batch expiry (FEFO) + days until expiry + `warnExpiry` (expiring within the threshold or already expired; default 30 days, capped at 365) + `stockMin`/`stockMax`/`warnStock` (when the herb dictionary has a stock floor/ceiling set, being below the floor or above the ceiling flags `warnStock`); herbs without ledger entries produce no row, ascending by name |
| DELETE | `/api/v1/herb-stock/{id}` | Delete a ledger entry (demo convention, no audit trail; the balance is recalculated from the remaining entries; returns 400 if the entry does not exist) |

## Acupoint Dictionary `/api/v1/acupoint` (login required)

A meridian-acupoint reference library (unique acupoint names; `pinyin` is the full-pinyin lowercase search code; the meridian is required). The point selection field on treatment records remains free text — the dictionary only assists selection: the treatment page searches by acupoint name or pinyin, and the selected entry is appended to the point selection text.

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/acupoint/` | Add an acupoint: `name` (1–10 characters, unique) + `pinyin` (full-pinyin lowercase, lowercased automatically on entry) + `meridian` (the meridian it belongs to, ≤20 characters) required; `location` (surface location)/`indication` (indications) optional |
| PUT | `/api/v1/acupoint/` | Full update by id; renaming to collide with another acupoint returns 400 |
| GET | `/api/v1/acupoint/page` | Paged query (descending by id; `keyword` fuzzy-matches the acupoint name **or pinyin code**, e.g. “足三” or “zusanli”) |
| GET | `/api/v1/acupoint/{id}` | Acupoint details (returns 400 if it does not exist) |
| DELETE | `/api/v1/acupoint/{id}` | Delete an acupoint (does not affect existing treatment records; demo-environment behavior, no audit trail) |

## Advertising Screens `/api/v1/ads` (tablet.md T1/T2)

Management endpoints require login; playlist delivery and media static resources are unauthenticated (intranet screen device convention, see the [tablet display design](/design/tablet)).

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST/PUT | `/api/v1/ads/materials` | Create / update a material (name, type 1 image 2 video, url, durationMs, enabled, sort; `category` category (material subtitle, optional); overlay copy kicker eyebrow line / title headline / copy body text / price price line, all optional — when all four are empty it plays as a pure material; delivered with the playlist to the tablet layout layer) |
| DELETE | `/api/v1/ads/materials/{id}` | Delete a material |
| GET | `/api/v1/ads/materials/page` | Paged materials (current, pageSize) |
| POST | `/api/v1/ads/materials/upload` | multipart media upload, saved to `data/ads/yyyyMM/`, returns `{ url, originalName, size }` |
| POST/PUT | `/api/v1/ads/schedules` | Create / update a schedule (screenId, materialId, `name` schedule name optional (shown in screen status), weekdays `1=Monday…7=Sunday` comma-separated optional, start/end `HH:mm` optional, enabled) |
| DELETE | `/api/v1/ads/schedules/{id}` | Delete a schedule |
| GET | `/api/v1/ads/schedules/page` | Paged schedules |
| POST/PUT | `/api/v1/ads/screens` | Create / update a screen (unique code, name, location, enabled) |
| DELETE | `/api/v1/ads/screens/{id}` | Delete a screen |
| GET | `/api/v1/ads/screens/page` | Paged screens |
| GET | `/api/v1/ads/playlist?screen={code}` | Tablet delivery: the heartbeat updates `last_seen_at`; materials are picked from the schedules matching the weekday/time slot (falling back to all enabled materials when no schedule matches), returning `{ version, items[] }`; `version` is a content stamp of `max(update_time)` over materials + schedules. Returns 400 for an unknown screen code |

Media files are statically mapped via `/media/**` to the upload directory (`sinomed.ads.upload-dir`, default `data/ads`) and read directly without login.

## Queue Calling `/api/v1/calls` (tablet.md T8)

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/api/v1/calls` | Front-desk queue call (login required): number and room required, patientMasked optional, empty screenId = broadcast to all screens; a targeted screen must exist and be enabled |
| GET | `/api/v1/calls/latest?screen={code}&since={id}` | Tablet cursor pull (unauthenticated): records after `since`, both broadcast and targeted to this screen, ascending by id, returning `{ since, calls[] }`; pass `since` back on the next call. Returns 400 for an unknown screen code |
| GET | `/api/v1/calls/recent` | The 20 most recent for the admin console (login required), descending by id |

## Error Tracking `/error`

| Method | Path | Purpose |
| ---- | ---- | ---- |
| POST | `/error` | Receives frontend error reports and writes them to the log |

## General Conventions

- **Pagination request**: `current` (starting at 1), `pageSize`; **pagination response**: `{ data, total, pages, success }`.
- **Path binding**: for a few endpoints (lookup by name etc.) the `{xxx}` placeholder must appear in the URL, with the actual value taken from the query parameter of the same name.
- **Error responses**: 400 parameter or business validation failure, 401 unauthenticated, 403 account locked, 500 system error; the response body is `{ message, ... }`.
- **Online debugging**: once the server is up, open <http://127.0.0.1:2347/doc.html> (the Knife4j Chinese interface) to debug directly.


