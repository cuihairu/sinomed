# Admin console (web)

`web/` is the main workbench for the clinic's daily operation: an Ant Design Pro + Umi (@umijs/max) project on Node.js 24 / pnpm, with a Chinese UI.

Screenshots of implemented pages (data is fictional demo data; the graphite × antique-gold theme is taken from the brand logo, with three further switchable theme colors — pine smoke green / cinnabar / indigo):

![Admin console · patient records (implemented screenshot)](/screenshots/admin-customer.png)

More page prototypes (treatment record / daily report / service cards / orders / advertising screens / appearance settings) are in the [docs-site product preview](./index.md#product-preview) and `docs/design/mockups/` in the repository.

## Running

```bash
cd web
pnpm install
pnpm dev     # dev mode: MOCK=none, proxied to the local server
```

- Dev URL `http://localhost:8000`; `/api` is proxied to `http://127.0.0.1:2347` (`config/proxy.ts`).
- `pnpm build` produces `dist/`, served by Nginx; `pnpm lint` runs code checks, `pnpm jest` runs unit tests.

## Project structure

| Location | Contents |
| ---- | ---- |
| `config/config.ts` | Site title, layout (side), i18n (zh-CN default), plugins and openAPI configuration |
| `config/routes.ts` | Route table (page registration) |
| `config/proxy.ts` | dev / test / pre environment proxies |
| `src/pages/` | Page components, grouped by business domain |
| `src/services/ant-design-pro/` | API definitions (one-to-one with the server's `/api/v1/**`) |
| `src/access.ts` | Runtime permissions: `canAdmin` (management), `canUser` (logged in) |
| `src/app.tsx` | Fetches the current user at startup; redirects when unauthenticated |
| `src/requestErrorConfig.ts` | Injects `Authorization: Bearer` from `localStorage.jwt`; unified error toasts |
| `src/locales/` | Multi-language strings for menus and other UI text |

## Pages by task

After login the default landing page is the **daily report** (`/dash/day`). The clinic's main flow runs "patient record → treatment (diagnosis) → service cards → retrospective & follow-up"; find pages by task:

### Appointments (/appointment)

The first link of the main flow "appointment → check-in at the desk → start a treatment record". Two views: a list workbench and a per-day time-slot grid (aligned with `docs/design/mockups/admin-booking.html`).

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/appointment/schedule` | Scheduling view | Per-day view of doctor × time-slot occupancy (9:00–18:00 hourly grid; columns follow the **doctor roster** (`staffs.title` semantics: employed and title containing "医师/doctor"; if the roster is empty, falls back to doctors with bookings that day; roster sorted by **famous-doctor level descending** — higher `famousLevel` first, ties by creation time ascending, via `GET /api/v1/staff/page`) and is **linked to the shift table** — doctors on duty that day (`staff_shifts` keyed by weekday) come first, roster doctors without a shift get a dimmed "no shift today" header): empty cells show a dashed "bookable"; booked cells show patient / item and a status tag (not yet arrived / arrived / skipped / cancelled), with one-tap "check-in" inside not-yet-arrived cells (0→1); the right rail has "today's appointments" four-tile stats (total / arrived / not yet arrived / skipped) plus the "not yet arrived" list (slot · patient · item · doctor + check-in) and a "mini-program integration" planning note, with date paging and "today"; cells and list rows carry **visit markers**: the treatment-course-card marker wins (the Nth card of the same item = the Nth course, by purchase order; `GET /card/customers` batches all cards per patient); patients without cards get a **follow-up / first-visit** marker from visit history (`GET /treat/visited?ids=` batch-counts treats; any record = follow-up, none = first visit; treats have no item dimension, so this is per-patient) |
| `/appointment/query` | Appointment list | Today / all toggle + status tabs (not yet arrived / checked in / skipped / cancelled, with counts); row actions: check-in (0→1), skip (0→8), cancel (0→9), "start treatment record" after check-in jumps to `/treat/create?customerId=`, cancelled rows can be deleted |
| `/appointment/create` | Create appointment | Locate the patient by phone number (prompts to create the record first if none) → time slot (date & time + duration 30/60/90 minutes) → item / staff / remarks optional; submission sends a time string with timezone offset (the server's Jackson defaults to UTC; without the offset the time shifts by 8 hours) |

Scheduling policy: no same-slot conflict check (demo boundary); when a cell holds multiple bookings the first is shown with an `+N` tag; the slot grid covers only 9:00–18:00 on the hour — appointments starting off the hour fall into the containing hour cell.

### Patients (/customer)

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/customer/create` | Create patient | New record: name / age / gender / phone / address / membership level / birthday |
| `/customer/query` | Patient search | Paged search; row actions: create diagnosis, update, diagnosis history, follow-up plan, delete |
| `/customer/update` | Patient record | Loads and saves patient info; the header card shows the record-creation month and the latest visit; the history section records allergy history / past medical history entry by entry (allergy / past toggle + content ≤200 chars, deletable); the cards section shows each visit card's "used X of N" progress and "purchase date · valid until" (issue may set a validity; empty = no expiry), with issue / suspend / restore; a "write-off log" modal lists entries (which visit / time / serving staff / order number; no number for backfilled history); valid cards allow "add visits" (1-99 + reason ≤100 chars) and "transfer card" (search the target patient, phone masked, self excluded); an "adjustment log" modal keeps the trail (bonus visits / transfer, reason, operator, receiving patient name) |

### Treatment records (/treat)

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/treat/create` | Create diagnosis | The big treatment-record form: chief complaint, inquiry / inspection / palpation, pulse (left and right hand, **cun / guan / chi positions** — three cells ×2 plus an overall summary and additions), five phases (the **five-phase generation & restraint map**: Fire-Heart / Wood-Liver / Earth-Spleen / Metal-Lung / Water-Kidney nodes, with the central spleen highlighted and the four indicators root / restraint / classic / urgency reacting live to input), point selection (pick from the acupoint dictionary and append; free text still allowed), acupuncture prescription (needle technique / moxibustion method / retention minutes / manipulation / course frequency + "apply an external-therapy template" prefilling method / points / course suggestions, freely editable afterwards), diagnosis, plan, diet, conditioning; supports `?customerId=` prefill and `?treatId=` follow-up refill; **attributed doctor** (optional `staffId` stored with the record; the server resolves `staffName` from staffs; the last three treatments and the patient-record timeline show the attributed doctor, honestly labeled "unattributed" when absent); the patient strip shows real card balances (honestly labeled when there are no cards) plus a **treatment-course marker** (the Nth card of the same item = the Nth course; unmarked without cards), and the today's-treatment count is real; the "payment" card deep-links to the **settlement counter** (receivables per order + receipt printing; no order totals are computed on this page), the order-form item rows are reserved as a planned area (no fake buttons), print / scan counters are labeled as planned (no telemetry source); the top **peripheral status bar** shows the barcode scanner / 80mm receipt printer / prescription-note printer connection state — inside the desktop workstation shell it comes from the real `peripheral_status` command (30 s polling + instant refresh on scan events), in a browser it is honestly labeled "requires the desktop workstation"; never faked (see [desktop](/design/desktop) D10) |
| `/treat/query` | Treatment search | Search treatment records by id / name / age / phone |
| `/treat/acupoints` | Acupoint dictionary | Maintenance page for the meridian-point reference library: add / edit / delete; point names unique; search by point name or pinyin code (e.g. "足三" / "zusanli"); picking a point in the treatment page's "point selection" search appends it to the points text |
| `/treat/history` | Diagnosis history | A patient's historical treatment records (newest first) |

Screenshots of the implemented create-diagnosis page (four examinations, pulse, point selection, five phases, conditioning plan):

![Admin console · treatment record (implemented screenshot)](/screenshots/admin-diagnosis.png)

### Service cards (/item)

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/item/create` | Create card | Name / price / duration (minutes, optional, display-only) / description; supports `?itemId=` refill (editing reuses this page — submitting with an id goes through the real `PUT /api/v1/item/` update; without an id it creates) |
| `/item/query` | Card search | Paged search and delete; **the edit action jumps to `/item/create?itemId=` to refill** |

### Feedback (/review) — retrospectives and follow-ups

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/review/day` | Daily summary | Write / view what went well and what to improve today |
| `/review/staff` | Staff summaries | Maintain staff retrospectives for a day in bulk |
| `/review/customer` | Patient feedback | Maintain patient follow-up records in bulk |
| `/review/plan` | Follow-up plan | Set a follow-up date, latest visit and reason for a patient (stored as the follow-up record's `advice`; the record page shows the next follow-up and the reminder from this) |

### Reports (/dash)

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/dash/day` | Daily report | Default home page: staff summary, today's follow-ups and today's summary in one layout |
| `/dash/month` | Monthly billing report | Month picker + three stat cards for the month (amount received / settlement orders / visit-card write-offs) + a 6-month trend double bar chart (receipts in ink bars / treatments in pale bars, each month labeled "N visits / N orders", current month highlighted) + payment-method mix + day-by-day details (orders / receipts); data comes from billing & settlement, with visit-card write-offs at 0 receipts listed separately |

### Clinic operations (/staff — staff and attendance)

Staff account maintenance, clock in / clock out and attendance statistics; clinic-operations support:

| Path | Page | What it does | Permission |
| ---- | ---- | ---- | ---- |
| `/staff/create` | Create staff | New staff account (including **title** `title`, free text; the scheduling page's doctor roster = employed with a title containing "医师"; **famous-doctor level** `famousLevel`, 0 regular / 1-3 famous doctor — affects list ordering and the badge only) | `canAdmin` only |
| `/staff/query` | Staff search | Paged search (columns include the **famous-doctor level**: 0 regular / level-1 · level-2 · level-3 famous-doctor badges) and staff deletion | `canAdmin` only |
| `/staff/update` | Update staff | Refill and save (including **title** `title` and **famous-doctor level** `famousLevel`) | - |
| `/staff/sign`, `/staff/sign/timesheet/today`, `/staff/timesheet/list` | Sign in / today's timesheet / timesheet list | Clock in / out, own hours today, a per-month all-staff attendance matrix | Logged in / - |
| `/staff/leave` | Staff leave | A leave form on the left (staff picker / type sick · personal / start-end time / reason) + recent leave records on the right (deletable); API `POST /api/v1/staff/leave/`; records never change login state and there is no approval flow | Logged in |
| `/staff/shifts` | Shift table | Periodic shift matrix (rows = staff, columns = Monday–Sunday, cells hold `HH:mm–HH:mm` shifts); the "add shift" modal picks staff / weekday / window (30-minute steps, end must be later than start); tap a cell to adjust, `✕` to delete; one shift per staff per weekday; attendance still follows the sign-in records; APIs `/api/v1/shift` | `canAdmin` only |
| `/staff/timesheet/week` | Weekly timesheet | Staff × 7 days (Monday–Sunday): "planned" = shift table, "actual" = that day's punches (earliest in / latest out, whole hours); pick a date to see its week, "this week" jumps back to the current one; API `/api/v1/staff/timesheet/week` | `canAdmin` only |

### Orders (/order)

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/order/create` | Front-desk order | Locate the patient by phone → multi-select service cards (quantity editable per row, prices snapshot the current card price, row subtotals sum into the total) and an optional order creator → the order lands as "pending check-in"; for walk-in additions at the desk |
| `/order/query` | Order management | Orders placed at the Kiosk or by the front desk are received here: accept → complete; unaccepted orders can be cancelled; deletion is limited to cancelled orders; completed / cancelled orders can be "viewed" in detail (line items unit price / quantity / subtotal, total, order remarks, payment method, order time, source); business number `YYYYMMDD-NNN`, newest first |

Orders come from the in-store Kiosk or the front desk; the desk accepts them here. No online payment — settlement happens in person.

Screenshots of the implemented pages (order management):

![Admin console · order management (implemented screenshot)](/screenshots/admin-orders.png)

### Billing & settlement (/billing)

The payment step for Kiosk / front-desk orders (matching the settlement-counter layout of `docs/design/mockups/admin-billing.html`).

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/billing/settle` | Settlement counter | Left: the unsettled queue (order status 0/1, `Kiosk` / `front desk` source tags, business number `YYYYMMDD-NNN`, items as a "name ×qty" string) + recent settlements (each can reprint a receipt or be refunded: reason required ≤100 chars in a one-way confirm modal, refunded rows marked; a refunded order returns to the queue for re-payment); selecting an order shows the settlement line items (item / qty / subtotal; multi-item orders join `order_item` details) and order remarks (an amber note block, ≤200 chars, saved on blur); pick a payment method to collect: stored value (shows the live balance; if short, top up any amount and collect together), WeChat, Alipay (selecting shows the merchant **payment QR code** for the patient to scan; a missing code 404s with an honest "not configured"), cash, visit card (shows the valid remaining count for this order's item; selecting deducts 1 visit, records 0 received, and appends a "card deduction ¥0" row); on success the order turns "completed" and a settlement is written |
| `/billing/reconciliation` | Daily reconciliation | Reconcile by calendar day: date picker (default today, future dates disabled) + stat cards (received / refunds / net / net cash / top-ups listed separately) + payment-mix table + two detail tables for top-ups and refunds (joined with patient / operator names; refund rows carry the reason and original payment method); API `/api/v1/settlement/reconciliation` |

Settlement policy: the amount received takes the order's price snapshot (multi-item orders sum the line subtotals; `order_item` stores name / price snapshots); stored-value payment automatically deducts the balance (flows summed); visit-card deduction takes 1 from the card's remaining visits, records 0 received, and writes the write-off flow in the same transaction (visible in the patient record's cards section); WeChat / Alipay are in-person scanned payments: the counter shows the merchant payment QR (`/api/v1/pay-qrcode/{method}` renders it; a missing image 404s honestly, and a laminated table card can bridge), the patient's scan-payment happens outside the system and the system only books the chosen method (no payment gateway; receipts follow the merchant statement); receipts use the server-side overprint `GET /api/v1/print/receipt/{settlementId}` (80mm HTML; printable on success, reprintable from recent settlements); prescription-herb rows entering settlement was ruled out — billing follows service-card order settlement, and prescription pricing is an estimate / hint; refunds return on the original path (stored value back to balance, visit cards back to counts, cash returned in person), and the daily reconciliation page `/billing/reconciliation` cross-checks by calendar day.

Screenshots of the implemented pages (settlement counter: stored value / top-up and visit-card deduction implemented; the prototype's prescription-herb rows were ruled out of settlement):

![Admin console · settlement counter (implemented screenshot)](/screenshots/admin-billing.png)

### Prescriptions (/prescription)

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/prescription/create` | Write prescription | Prescription header (patient gender · age · masked phone / doctor · date · visit no. = treatment record / prescription-number preview `Rx-YYYYMMDD-No.`, generated after saving) → locate the patient by phone → the latest treatment record's pattern-differentiation strip (pattern = the treatment record's diagnosis, method / plan = its plan; switchable across records, `treatId` saved with the prescription; without one a create-record prompt shows) → "apply template" prefills herbs / doses / administration from a syndrome (optional); "formula library" prefills a whole formula by name or pinyin → write herbs (name / grams / processing / decoction method; rows add and delete) + the "add from herb catalog" dropdown (search by Chinese name or **pinyin initials** like zz / fs / gcao; picking fills an empty row or appends one; options carry the per-gram price; pinyin-pro computes locally with caching) + special-decoction reminders (scanning herb decoction notes for 先煎 / 后下 / 包煎 / 烊化 / 冲服 / 另煎 counts "N herbs need annotation") + **dose-cap hints** (`POST /api/v1/prescription/dose-check`, debounced 500 ms: a per-herb single-dose gram weight strictly above that herb's `dose_max` triggers a hint; equal values do not; nothing is blocked — the doctor decides; caps are clinic-entered — pharmacopoeia authority values are medical reference data and are not hard-coded in the repo, same as stock limits) + type (decoction / paste; paste picks the finishing method) + administration (doses / decoction / how to take) + remarks + signature row (optional prescriber / pharmacist review / compounding check); checking "decoction service, N bags" reveals the pickup method (self-pickup / local delivery at ¥6.00 — the fee remains informational) and a pickup-window note (before 17:00 today; 2 hours for rush); the pricing panel shows the prototype's three-line account: herbs N items × doses + decoction service (¥3/bag, ruled into receivables on 2026-10-12) → receivable = their sum, with actual payment still following service-card order settlement; three buttons at the bottom: save & charge (submit) · save to my formula library (stores the current formula as an enabled template under the syndrome name) · print the prescription note (prints the note card only) |
| `/prescription/query` | Prescription search | All written prescriptions (patient / doctor / herbs / doses / type / decoction / paste status / decoction & administration / written-at / live pricing); the "detail" drawer shows the whole formula (processing / decoction columns) with pricing details, decoction and administration side by side, decoction and paste flows and pickup, dispensing status with quick dispense confirmation (with a remark, via the pharmacy workbench), deletable |
| `/prescription/dispensary` | Pharmacy workbench | Pending / dispensed / all tabs with counts + search by patient name / phone; list = prescription no. / patient (name + masked phone) / type / doses / herb count / decoction / paste / dispense status & time / written-at; the row action "dispense" opens a modal with an optional remark (≤100 chars) and one-way confirm; dispensed rows are read-only; decoction / paste "picked up" marks auto-dispense without a second tap; API `/api/v1/prescription/dispense/page` |
| `/prescription/templates` | Syndrome templates | Maintenance for the syndrome-prescription template library: create / edit (inline herb rows) / delete / enable switch; syndrome names unique; templates store suggested values only — applied values are freely adjustable and never written back |
| `/prescription/therapy-templates` | External-therapy templates | Maintenance for the external-therapy template library: create / edit / delete / enable switch; template names unique (1–20 chars) + therapy method (≤10 chars, e.g. 耳穴压豆 / 穴位贴敷 / 艾灸 / 拔罐) + suggested points / course frequency / notes; the treatment page's "apply external-therapy template" dropdown (enabled templates only) prefills needle technique / course and the points text — freely editable afterwards, never written back; API `/api/v1/therapy-template` |
| `/prescription/formulas` | Formula library | Maintenance for the classic-formula reference library: add / edit (inline herb rows) / delete; formula names unique; search by name or pinyin code (e.g. "逍遥" / "xiaoyao"); herb gram weights follow the literature's usual amounts — prefilled values are freely adjustable and never written back |
| `/prescription/herbs` | Herb dictionary | The pricing reference for prescriptions: add / reprice / delete; names unique and matched exactly; prices are fen per gram (displayed as yuan); maintainable **stock limits** (`stockMin`/`stockMax` grams, empty = no alert) and **per-dose caps** (`doseMax` grams/dose, empty = no alert; the basis for the writing page's dose checks) |
| `/prescription/herb-stock` | Herb stock | A stock balance board (grams + the earliest unconsumed batch's expiry + expiry alerts with switchable 30/60/90-day thresholds + a **stock line** alert column: below the floor / above the ceiling) + stock-in/out entry (herb / type / quantity / expiry / supplier / remarks; out-entries take no expiry) + paged, deletable flows; stock = total in − total out; out-consumption follows FEFO by expiry; API `/api/v1/herb-stock` |

Herb names are free text; compatibility review (the eighteen incompatibilities / nineteen antagonisms), allergy alerts and live pricing are implemented — the writing page checks as you type (compatibility hints never block; after a patient is located, herb names are matched against the patient's allergy history verbatim, hits list the herb + the original record text, no placeholder when there are none; pricing matches the herb dictionary by exact name, unlisted names are not charged); decoction service is implemented — checking "decoction service, N bags" on the writing page (bags = doses; the ¥3/bag fee is informational; pickup method self / local delivery saved with the prescription), the search page lists decoction status and the detail drawer follows the flow (waiting → ready → picked up); paste is implemented — selecting type "paste" and a finishing method (honey-refined / clear / sugar / gelatin, clearable) saves it as "to prepare" (paste counts by batch; no decoction service), the search page lists paste status and the detail drawer follows the flow (to prepare → ready → picked up); syndrome templates are implemented — the writing page's "apply template" dropdown (enabled templates only) prefills freely editable values, plus "save to my formula library" storing the current formula as an enabled template under the syndrome name; the formula library is implemented — the writing page's "formula library" dropdown searches by name / pinyin and prefills the whole formula (herbs prefilled, dose count unchanged), freely editable afterwards.

Screenshots of the implemented pages (herbal prescriptions: writing / search / compatibility review / allergy alerts / live pricing / decoction & paste pickup / pattern-differentiation linkage):

![Admin console · herbal prescription (implemented screenshot)](/screenshots/admin-herbprescription.png)

### Advertising screens (/ads)

Management of the lobby display's (tablet) materials, schedules, screen registration and call records:

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/ads/materials` | Materials | Upload advertising materials (video / image), category subtitle (e.g. "clinic events", optional), enable / disable, sorting; four overlay-text fields (kicker / headline / copy / price line, all optional — all empty plays the bare material), delivered with the playlist to the tablet's layout layer (an adaptation of the prototype's whole-page tablet-screen design: media fills the stage, text overlays on a bottom-left gradient) |
| `/ads/schedules` | Schedules | Schedule name (optional) + materials arranged onto screens by weekday and time window |
| `/ads/screenss` | Screens | Kiosk screen registration, online status (playing schedule name + rotation length), schedule binding |
| `/ads/calls` | Call queue | After the front desk calls, the target screen (or all screens) shows the number and room fullscreen within 3 seconds; appointment check-in (0→1) **calls automatically** (server-side linkage, visible in the recent-call list; walk-ins are still called manually) |

Screenshots of the implemented pages (materials + screen status + quick calling):

![Admin console · advertising screens (implemented screenshot)](/screenshots/admin-ads.png)

### Appearance (/appearance)

Site theme and brand presentation (design in the docs-site "theme switching" preview):

| Path | Page | What it does |
| ---- | ---- | ---- |
| `/appearance` | Appearance settings | Four theme colors, applied on tap: graphite × antique gold (default, from the brand logo) / pine smoke green / cinnabar / indigo; the choice applies instantly and is remembered, switching the site's tokens.css variable groups |

Unauthenticated entry: `/user/login` (account + password login; after login each role lands on its home page; no sign-up — accounts are created by the administrator, see the auth conventions in [api](/server/api)).

## Permissions and roles

- `canAdmin`: the admin console's create / search staff entries (the administrator with server-side `role == 0`; login response `currentAuthority=admin`);
- `canUser`: sign-in and today's timesheet entries (any logged-in staff);
- Server-side method-level authorization is enforced: staff-management write endpoints (`POST`/`PUT`/`DELETE` /api/v1/staff) carry `@PreAuthorize("hasRole('ADMIN')")`, matching the page-level `canAdmin`; the server's role mapping is `role 0 → ADMIN`, `1-9 management → MANAGER`, `≥10 regular staff → USER` (StaffEntity.getAuthorities); unauthorized writes return 403 (ResponseExceptionAdvice maps AccessDeniedException to 403, never 500; regression: StaffPermissionHttpTest). All other business endpoints still require only login (no per-method authorization).

## Frontend leftovers (stated honestly)

- The brand question is settled (2026-10-12): the site brand is "Youngs 医馆" — evidenced together by the logo 养 mark, all interface prototypes and the owned domain youngs.fun; not scaffolding residue. The login page title, Footer copy and `src/manifest.json` brand values all follow it (the Footer keeps the youngs.fun business link; the PWA manifest is not wired up yet — config does not reference it and it is not in the build output, the brand values sit ready for wiring);
- `config/oneapi.json` is still the scaffold's template schema (with template endpoints like `/api/rule`), **kept for reference** — the pro plugin's preinstalled openAPI codegen + requestRecord recording was removed (its dependency chain carried an unpatched mockjs); `src/services/ant-design-pro/` is maintained by hand.

> Cleaned up: the scaffold's leftover pages `src/pages/Welcome.tsx`, `Admin.tsx`, `TableList/` (unrouted, unreferenced); the petstore sample service `src/services/swagger/` (the second openAPI entry in `config.ts` removed with it); the placeholder card pages `/item/spread` and `/item/update` (the form was really a misuse of `createItem` creating a new card; removed together with the routes — editing uniformly goes through `/item/create?itemId=`, and since 2026-10-12 submitting with an id goes through `updateItem` PUT for a real update; "spread" had no design in the prototype admin-items.html and was ruled scaffolding residue, not to be rebuilt).
