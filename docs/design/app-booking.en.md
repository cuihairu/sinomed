# Mini Program Self-Service Booking (Unauthenticated Write Endpoint) · Design Draft

> 🌐 [中文](./app-booking.md) · **This page is in English**

::: tip Status: implemented (2026-10-10)
The decision points in §7 have been ruled on (see "Decision record"), and every item of the §8 implementation checklist has landed: server `36102cf`, mini program `f7ccbbf`. This document keeps the full agreed policy as the implementation reference.
:::

## 1. Goals and Scope

Self-service booking for patients in the mini program (`app/`, Taro): pick a date → pick a slot → pick a service item (skippable) → submit, which writes one `status=0` not-yet-arrived appointment; after arrival, the front desk proceeds in the admin console as usual with "check-in → start treatment".

**Not in the MVP**: payment, SMS/verification codes, patient-side cancellation, same-slot conflict checking, recurring schedules, cross-store support.

## 2. Policy Alignment with the Kiosk

The patient self-service Kiosk ([设计文档](/design/kiosk)) already implements a closed unauthenticated loop; self-service booking **reuses that exact policy** instead of opening a new model:

| Policy | Kiosk (implemented) | Self-service booking (this draft) |
| ---- | ---- | ---- |
| Unauthenticated zone | `/api/v1/kiosk/**` (existing permitAll in SecurityConfig) | **The same matcher**; the new path lands inside it, so the security config changes zero lines |
| Structure | Read-only browsing + **the single write endpoint** `POST /kiosk/orders` | Browsing reused + **the single write endpoint** `POST /kiosk/appointments` |
| Record creation | Idempotent patient-record creation keyed on phone number (default name 「到店客人」, Walk-in Guest) | Same policy, reusing the same customers row |
| Payment | Not done; settled in store | Not done; proceeds as usual after in-store check-in |
| Write scope | 1 patient record + N status=0 orders | 0–1 patient records + 1 status=0 appointment |
| Never exposed | Any query/delete capability | Equally not exposed (the response contains only this appointment itself) |

## 3. API Surface (added after the ruling; this is the one and only write endpoint)

### POST `/api/v1/kiosk/appointments` (unauthenticated)

| Item | Description |
| ---- | ---- |
| Request | `{ phone, name?, itemId?, startTime }`; `phone` validated as an 11-digit number; `startTime` in `yyyy-MM-dd HH:mm`, **on the hour**, and later than the current moment; `itemId` optional (chosen in store; must exist and be listed); `name` optional (defaults to 「到店客人」, Walk-in Guest) |
| Filled in server-side | `duration=60`, `staffId=null` (assigned on arrival), `status=0` not yet arrived, `remark` records 「小程序自助」 (mini program self-service) — see the source policy in §4 |
| Response | `{ customerId, customerName, appointmentId, startTime, itemName?, status: 0 }` |
| 400 | Bad phone format / bad time format or not on the hour / a time in the past / a slot out of range (see §4) / service card not found or delisted / an unfinished appointment already exists today (rate limit) |

Browsing gets **zero new endpoints**: the item list uses the existing `GET /api/v1/kiosk/items` as is.

## 4. Security Boundary (the focus of the rulings)

The inherent risks of an unauthenticated write endpoint and the countermeasures (stated as they are; nothing not yet implemented is claimed):

- **Order flooding / abuse**: per phone number, at most **1** appointment per day with `status IN (0,1)`; anything beyond returns 400 「当日已有预约，请到店或致电改约」 ("an appointment already exists today; please reschedule in store or by phone"). Implemented on the existing customers (query by phone) + appointments (count per customer per day) queries — no new tables, no new indexes (sufficient at demo scale).
- **Hard slot constraint**: only on-the-hour slots `09:00–17:00` on later days are accepted (aligned with the last cell start of the 9:00–18:00 scheduling grid), which constrains the shape of junk data at the source.
- **Phone number as identity**: the demo environment trusts the phone number — no SMS verification — the same policy as kiosk ordering (the kiosk doc already notes "add a verification code or screen token if exposed to the public internet"; this endpoint shares that boundary); verification codes / a rate-limiting gateway are planned items.
- **No lateral data exposure**: the response contains only this appointment and the patient's own name; there is no way to look up other people by phone number; `staffId` is never sent down.
- **Source policy**: `remark` records 「小程序自助」 (mini program self-service) as a soft marker, with **no schema change** (no source column). Such rows simply show up as "unassigned" in the admin console list / scheduling grid, which is acceptable for the demo; add a `source` field only if a hard distinction is later needed (planned).

## 5. Admin Console Impact: Zero Changes

Appointments appear as usual in `/appointment/query` and `/appointment/schedule`; check-in (0→1), cancellation (0→9), and deletion (only from 9) flows are unchanged. The scheduling grid's existing "several entries in one cell → take the first, +N" policy naturally absorbs conflicts (same-slot conflict checking was never done in the first place — a demo boundary).

## 6. Mini Program Pages (implemented after the ruling)

- `pages/booking`: date strip (7 days from today) → slot grid (9:00–17:00 on the hour, all shown as bookable — the server does no occupancy lookup; conflicts are reconciled manually at in-store check-in) → item selection (reuses `GET /api/v1/kiosk/items`, skippable) → submit with phone + name.
- A successful submission shows the appointment number and slot; 400 messages are surfaced verbatim (e.g. 「当日已有预约…」).
- The home "booking banner" (an implemented placeholder) navigates to this page, and the placeholder marker is retired along with it.
- H5 integrates directly; the WeChat mini program needs the API domain added to the business-domain whitelist (a deployment policy; see the same-origin constraints in the [换马甲构建指南](/app-white-label)).

## 7. Decision Record (ruled 2026-10-10)

| # | Question | Ruling | Rationale |
| ---- | ---- | ---- | ---- |
| 1 | Is the "1 per day per phone" rate limit the right fit? | **1 per day per phone** (counting status IN (0,1); beyond that, 400) | Unauthenticated with no verification code, so anti-abuse comes first; loosening it is only a counter-threshold change, to be retuned once real repeat-booking cases appear |
| 2 | Can patients cancel on their own? | **No** (admin-console 0→9 cancellation only) | Cancellation verifies no identity, so maliciously canceling other people's appointments could not be stopped; admin-console cancellation already covers the operational scenarios |
| 3 | Should the slot be fixed at 60 minutes? | **Fixed at 60 minutes** | Simplifies both server-side validation and scheduling-grid display; the admin console can still book 30/60/90 — the two policies do not affect each other |
| 4 | Add a `source` column? | **No**; `remark` records 「小程序自助」 as a soft marker | At demo scale a soft marker is enough; a column would touch the schema and existing queries — migrate only when a real reporting need shows up |

## 8. Implementation Checklist (completed 2026-10-10)

| # | Item | Acceptance |
| ---- | ---- | ---- |
| A1 ✅ | server: `POST /api/v1/kiosk/appointments` (validation + rate limit + idempotent record creation + appointment write), commit `36102cf` | The 12-case curl matrix passed in full: normal booking (including no service card, itemName=null) / the same phone on different days resolves to the same patient / a second booking the same day 400 / a past time 400 / off-hour 400 / out-of-range slot 400 / bad format 400 / delisted service card 400 / bookable without a token (unauthenticated zone); the stored row verified as `duration=60`, `staffId=null`, `status=0`, remark 「小程序自助」 |
| A2 ✅ | app: the `pages/booking` page + home banner wiring + the `bookAppointment` service function, commit `f7ccbbf` | Full flow exercised on H5 (Playwright): banner entry (planning placeholder retired) → pick date / slot / item / phone → success receipt (appointment number / slot / item name) → rebooking the same phone the same day surfaces 400 「当日已有预约，请到店或致电改约」; build:h5 + build:weapp green |
| A3 ✅ | Docs promoted to final: this draft marked implemented + policy added to api.md/data-model.md + the appointment row updated in features.md | docs build passed; the status line matches the source |

## 9. Follow-up Candidate Trade-offs (ruled 2026-10-10)

| Candidate | Ruling | Rationale |
| ---- | ---- | ---- |
| An explicit admin-console filter for the 「小程序自助」 source | **Not doing it** | The soft marker is already plainly visible in the remark column of the admin appointment list; decision 4 settled on no source column, and a filter is half a source concept. When a real reporting need appears, migrate it together with the source column |
| Mini program login / my service cards / my appointments pages | **Not doing it** (blocker = patient identity verification) | A direct unauthenticated lookup by phone number would expose lateral data (same boundary as §4); verification codes or passwords exceed the demo MVP policy (§1). Kept as planning placeholders |
| My service cards (card lookup by phone) | **Implemented** (a 2026-10-14 ruling superseded that item of the row above; the row's other two items remain not-done) | No bare-phone-number card lookup (privacy red line); instead openid binding / phone + SMS-code two-factor, selected by the three-state switch `sinomed.phone-lookup.mode` (default `off`), with `card_lookup_logs` audit writes. The home "my service cards" block is entry linkage + a local summary echo (form of address / number of cards held / query date, valid for 7 days; details and remaining visits are never stored on the device). Policy in [kiosk.md](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定) |
