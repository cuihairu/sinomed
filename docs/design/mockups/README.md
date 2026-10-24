# UI Prototype Render Log and File Inventory

> One brand language: graphite × antique gold (derived from logo gray #666666 + gold #C7A674), on a rice-paper ground with Song-typeface headings. Four switchable theme palettes: graphite (default), pine smoke green, cinnabar, indigo. The data universe is consistent across the repo (Ms. Wang (王女士) / gold-tier card / moxibustion card with 6 visits left / dog-day moxibustion ¥880 / Physician Wang (王中医师), and so on).

---

## 1. Source File Inventory (`docs/design/mockups/`)

| File | Target | Status | Description |
|---|---|---|---|
| `tokens.css` | all | **Design-system core** | CSS variables for the four themes, palette, fonts, spacing, radii, shadows |
| `admin-customer.html` | web | ✅ Feature implemented | Patient record: create / query / update / diagnosis history / follow-up plan / held service cards (visit-card issuance and remaining visits) |
| `admin-diagnosis.html` | web | ✅ Feature implemented | Treatment record: four examinations / pulse / five phases / point selection / diagnosis / plan |
| `admin-dash-day.html` | web | ✅ Feature implemented | Daily report: staff summaries / today's follow-ups / today's recap / bar chart of treatments over the last 7 days |
| `admin-items.html` | web | ✅ Feature implemented | Service card management: covers / listing / ordering / edit / delete / paging |
| `admin-orders.html` | web | ✅ Feature implemented | Order management: paging / state transitions (accept / complete / cancel / delete) / view details / multi-item line items (order_item rows, business number `YYYYMMDD-NNN`, item column "name ×qty", front-desk multi-select of service cards with quantity when creating an order) |
| `admin-ads.html` | web | ✅ Feature implemented | Advertising-screen management: four tabs for materials / schedules / screens / queue calling |
| `admin-appearance.html` | web | ✅ Feature implemented | Appearance settings: instant switching via four theme cards + live preview grid |
| `admin-booking.html` | web | ✅ Feature implemented | Booking and scheduling: the not-yet-arrived list / appointment creation / check-in-to-treatment transfer and the physician × slot schedule grid are implemented (`/appointment/schedule`, right column with today's four-tile appointment stats / not-yet-arrived list / mini program linkage notes); the skipped status (0→8) and the one-booking-per-slot limit (same physician, same hour, validated server-side) are implemented; mini program self-service booking is implemented (`POST /api/v1/kiosk/appointments` + mini program `pages/booking`); ✅ 2026-10-12 the "Nth course / return visit / first visit" corner badges on grid and not-yet-arrived rows are implemented — treatment-course card markers take priority (the Nth card for the same item), and without a card the badge falls back to return/first visit from treatment history (`GET /treat/visited?ids=` batch-queries aggregated treat counts per patient; treats have no item dimension, so a per-patient policy was decided) |
| `admin-herbprescription.html` | web | ✅ Feature implemented | Chinese-medicine prescriptions: prescription-pad writing / query implemented (`/prescription/create`, `/prescription/query`, herb names as free text) + compatibility review implemented (`POST /prescription/compatibility`, static rules for the Eighteen Incompatibles / Nineteen Antagonisms) + pricing implemented (`/api/v1/herb` dictionary + `POST /prescription/price` trial calculation, lookups as you type on the prescribing page, herb names not in the catalog are not billed) + decoction-service pickup implemented (checking decoction at prescribing with bags = doses lands as pending-decoction, the query page flows pending → ready → picked up, service fee ¥3/bag shown as a hint only, 141f295/47e4828); all 11 todo items cleared 2026-10-12 (rx header / special decoction-method reminder / dosage review dose-check / three signatures / pickup method / three buttons / processing column / three-cell usage / pinyin search / allergy always visible, with the decoction fee decided to roll into the three-line account total); the only remaining blocker is the **authoritative source** for pharmacopoeia dosages (the mechanism is in place and the clinic records dose_max itself; the data source awaits the user's decision) |
| `admin-billing.html` | web | ✅ Feature implemented | Billing and settlement: the pending-settlement queue / stored-value payment and recharge / WeChat · Alipay · cash are implemented (`/billing/settle`, settlement-counter form) + visit-card offset implemented (payType 5, deducts 1 visit, 0 received) + receipt printing implemented (server-side template printing `GET /api/v1/print/receipt/{settlementId}`, print after collection / reprint the latest settlement, f57f10d/1b33703) + multi-row settlement detail / whole-order remark (`PUT /api/v1/order/remark`, ≤200 chars) / queue business numbers implemented (2026-10-11) + the in-store scan-to-pay QR display implemented (`GET /api/v1/pay-qrcode/{method}` serves the image, an honest hint when the image is missing, 2026-10-12); the prescription herb rows in the prototype were ruled out (on the grounds that "billing is anchored to service-card order settlement", prescription pricing being a trial-calculation / hint policy); the real online-payment channel (#88) is a red line and stays untouched |
| `desktop-workstation.html` | desktop | ✅ Feature implemented | Treatment order creation + printer / scanner peripheral status |
| `mobile-home.html` | app | ✅ Feature implemented | Mini program home: brand header (bell / round avatar button at the right, the form of address taken from what self-service booking left on this device) / booking banner (shows a reminder state when there is an unexpired not-yet-arrived appointment on this device, otherwise a self-service booking entry; both navigate to the `pages/booking` tab) / Today's wellness (the grid pulls real data; beyond 4 items an "All items" expander appears) / my service cards / wellness tips; four-tab tabBar at the bottom (Home / Booking / Cards / Me); the white-label self-check moved off the customer home to `pages/me` (for engineering acceptance); there is no endpoint for card remaining visits, so `pages/card` and the home page both carry an honest "planned feature" placeholder |
| `kiosk-menu.html` | kiosk | ✅ Feature implemented | Service selection and ordering: service card grid / basket / order by phone number / receipt |
| `tablet-screen.html` | tablet | ✅ Feature implemented | Ad carousel + queue calling on screen (masked names) |

---

## 2. Screenshot Artifacts (`docs/public/screenshots/`)

### 2.1 The base 14 (1440×900 @2x → 2880×1800; tablet/desktop 1920×1080 @1.5x → 2880×1620)

| Artifact | Size | Source file | Render command |
|---|---|---|---|
| `admin-customer.png` | 2880×1800 | `admin-customer.html` | see below |
| `admin-diagnosis.png` | 2880×1800 | `admin-diagnosis.html` | same as above |
| `admin-dash-day.png` | 2880×1800 | `admin-dash-day.html` | same as above |
| `admin-items.png` | 2880×1800 | `admin-items.html` | same as above |
| `admin-orders.png` | 2880×1800 | `admin-orders.html` | same as above |
| `admin-ads.png` | 2880×1800 | `admin-ads.html` | same as above |
| `admin-appearance.png` | 2880×1800 | `admin-appearance.html` | same as above |
| `admin-booking.png` | 2880×1800 | `admin-booking.html` | same as above |
| `admin-herbprescription.png` | 2880×1800 | `admin-herbprescription.html` | same as above |
| `admin-billing.png` | 2880×1800 | `admin-billing.html` | same as above |
| `desktop-workstation.png` | 2880×1620 | `desktop-workstation.html` | same as above |
| `mobile-home.png` | 780×1688 | `mobile-home.html` | 390×844 @2x |
| `kiosk-menu.png` | 1800×2400 | `kiosk-menu.html` | 900×1200 @2x (portrait) |
| `tablet-screen.png` | 2880×1620 | `tablet-screen.html` | same as above |

### 2.2 Theme-switch montage (2×2)

| Artifact | Size | Source | How it is generated |
|---|---|---|---|
| `admin-themes.png` | 2880×1872 | four theme variants of `admin-customer.html` | `convert` scales each proportionally → `montage -tile 2x2 -geometry +0+18 -background '#3a3d3b'` |

---

## 3. Render Commands (reproducible)

### 3.1 Environment Setup

```bash
# 字体（必须，否则宋体回退无衬线）
sudo apt-get install -y fonts-noto-cjk-extra && fc-cache -f
fc-list | grep -ci "noto serif cjk"   # 应输出 ≥ 30

# Headless Chromium（Playwright 自带）
cd /home/cui/workspaces/sinomed/docs/design/mockups
```

### 3.2 Rendering the Base 14

```bash
# 统一参数：--force-device-scale-factor=2 --hide-scrollbars --no-sandbox --disable-gpu
# 管理端页面：1440x900 @2x
for f in admin-customer admin-diagnosis admin-dash-day admin-items admin-orders admin-ads admin-appearance admin-booking admin-herbprescription admin-billing; do
  /home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
    --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/${f}.png \
    --window-size=1440,900 \
    --force-device-scale-factor=2 \
    "file:///home/cui/workspaces/sinomed/docs/design/mockups/${f}.html"
done

# 桌面/平板：1920x1080 @1.5x
for f in desktop-workstation tablet-screen; do
  /home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
    --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/${f}.png \
    --window-size=1920,1080 \
    --force-device-scale-factor=1.5 \
    "file:///home/cui/workspaces/sinomed/docs/design/mockups/${f}.html"
done

# 移动端：390x844 @2x
/home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
  --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/mobile-home.png \
  --window-size=390,844 \
  --force-device-scale-factor=2 \
  "file:///home/cui/workspaces/sinomed/docs/design/mockups/mobile-home.html"

# 自助机：900x1200 @2x（竖屏）
/home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
  --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/kiosk-menu.png \
  --window-size=900,1200 \
  --force-device-scale-factor=2 \
  "file:///home/cui/workspaces/sinomed/docs/design/mockups/kiosk-menu.html"
```

### 3.3 Rendering the Four Theme Variants (admin-customer as the example; the rest follow the same pattern)

```bash
cd /home/cui/workspaces/sinomed/docs/design/mockups

for theme in pine cinnabar indigo graphite; do
  # graphite = 无 data-theme（默认）
  if [ "$theme" = "graphite" ]; then
    cp admin-customer.html _theme-graphite.html
  else
    sed "s/<body>/<body data-theme=\"${theme}\">/" admin-customer.html > _theme-${theme}.html
  done
done

# 渲染四张
for theme in graphite pine cinnabar indigo; do
  /home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
    --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/admin-customer-theme-${theme}.png \
    --window-size=1440,900 --force-device-scale-factor=2 \
    "file:///home/cui/workspaces/sinomed/docs/design/mockups/_theme-${theme}.html"
done
```

### 3.4 Montage Generation of `admin-themes.png` (key: scale proportionally BEFORE montaging)

```bash
cd /home/cui/workspaces/sinomed/docs/public/screenshots

# 1) 逐张等比缩放到 1440x900（留白不裁剪）
for theme in graphite pine cinnabar indigo; do
  convert admin-customer-theme-${theme}.png \
    -resize 1440x900 \
    -background '#3a3d3b' -gravity center -extent 1440x900 \
    _tile-${theme}.png
done

# 2) 2x2 拼图，格间距 18px，背景色同石墨卡底
montage _tile-graphite.png _tile-pine.png _tile-cinnabar.png _tile-indigo.png \
  -tile 2x2 -geometry +0+18 -background '#3a3d3b' \
  admin-themes.png

# 3) 清理中间文件
rm _tile-*.png
```

> ⚠️ **Pitfall avoided**: `montage -geometry 1440x900+0+18` **center-crops** instead of scaling proportionally, which cuts the content in all four tiles. Run `convert -resize 1440x900` first, then montage.

---

## 4. Data-Universe Consistency (cross-page check table)

| Entity | Key values | Pages where it appears |
|---|---|---|
| Patient: Ms. Wang (王女士) | 137****3333, gold-tier member, moxibustion card with 6→5 visits left | admin-customer, admin-diagnosis, admin-billing, admin-dash-day, admin-orders, mobile-home, admin-herbprescription |
| Physician Wang (王中医师) | TCM physician, prescription signatures, treatment-record creation | admin-diagnosis, admin-dash-day, admin-herbprescription, admin-billing |
| Physician Shen (沈中医师) | TCM physician, abdominal tuina, staff summary submitted | admin-dash-day, admin-booking, admin-orders |
| Technician Su (苏技师) | technician, foot-bath steaming, pharmacist review / compounding | admin-dash-day, admin-herbprescription |
| Director Gu (顾馆长) | clinic director, store-patrol reconciliation, pharmacist review | admin-dash-day, admin-herbprescription |
| Service card: Moxibustion conditioning (艾灸调理) | ¥100/visit, 10-visit card | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| Service card: Abdominal tuina (腹部推拿) | ¥180/visit, 30 minutes | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| Service card: Shoulder-and-neck tuina (肩颈推拿) | ¥160/visit | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| Service card: Foot-bath steaming (足浴熏蒸) | ¥120/visit | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| Service card: Dog-day moxibustion course card (三伏灸疗程卡) | ¥880 | admin-items, admin-customer, mobile-home |

---

## 5. Document Embedding Reference (VitePress `/sinomed/` base path)

| Document | Embedding syntax | Description |
|---|---|---|
| `README.md` | `![alt](/screenshots/xxx.png)` | Root-relative; VitePress prepends base automatically |
| `docs/index.md` | same | Home page product preview + planned future features |
| `docs/web.md` | same | Four images: patient record / treatment record / orders / advertising screen |
| `docs/app.md` | same | Mobile home prototype (annotated that the UI prototype is not yet implemented) |
| `docs/design/desktop.md` | same | Desktop workstation prototype |
| `docs/design/kiosk.md` | same | Kiosk prototype |
| `docs/design/tablet.md` | same | Tablet prototype + admin-ads.png |
| `docs/research/features.md` | cross-links inside tables | Screenshot paths footnoted on the booking / Chinese-medicine / billing rows |

> **Note**: VitePress rewrites Markdown image syntax `![](path)` to `/sinomed/path`; an absolute path written inside an HTML tag `<img src="/screenshots/...">` is **not** rewritten and will 404.

---

## 6. Most Recent Full Re-render Record

| Date | Trigger | Key fixes |
|---|---|---|
| 2026-10-08 | An audit found holes in admin-items, layout misalignment in admin-dash-day, a cropping error in admin-themes.png, and missing Noto Serif CJK | 1) Added the 6th row on the service-card page + a pagination bar 2) Moved the report bar chart to the bottom of the right column stack 3) Installed 35 font variants 4) Switched the montage to `convert -resize` proportional scaling |

---

## 7. Ongoing Maintenance Checklist

**Automated gate**: `pnpm -C docs check` (`docs/scripts/check-docs.mjs`, zero dependencies), invoked by `docs.yml` before build. VitePress build itself does not validate image links — a broken link only 404s in the browser while the build stays green — so it stands as a separate check.

| Item | What it checks |
|---|---|
| Broken image links | Every Markdown `![alt](path)` is resolved with VitePress semantics (`/xxx` → `docs/public`, everything else relative to the `.md` file); sample strings inside code blocks and inline code are skipped |
| HTML absolute paths | `<img src="/...">` must hit `docs/public` — VitePress rewrites base only for Markdown syntax, not for absolute paths in HTML tags (see the "Note" above) |
| Prototype style source | Every `design/mockups/*.html` must link `tokens.css`, otherwise theme switching breaks |
| All four themes present | `tokens.css` keeps the `:root` graphite default + the three `[data-theme]` blocks `pine`/`cinnabar`/`indigo` |

**Still needs human judgment** (beyond the gate's reach):

- New pages → data-universe consistency (whether the prototype sample data matches the backend entity policy), plus render commands and screenshot cross-references
- Theme color changes → full re-render of the 14+4 images (needs a browser + CJK font environment; see the render record in §6)
