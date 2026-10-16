# Mini program (app)

> 🌐 [中文](./app.md) · **This page is in English**

`app/` is a Taro multi-target project (React + TypeScript + NutUI React Taro); the same code compiles to the WeChat mini program, H5 and other targets.

::: warning Current status
The mini program has completed **project scaffolding + white-label builds + patient home page + self-service booking + me page + card lookup**: the home page follows the mobile-home prototype (brand header / booking banner / today's wellness suggestions / my service cards / wellness tips); the "today's wellness suggestions" grid pulls live from items on sale (reusing the Kiosk read-only endpoint `GET /api/v1/kiosk/items`); the request wrapper is `src/services/api.ts`. **Self-service booking is implemented** — `pages/booking` (pick date / time slot / item / phone number and submit) posts to the unauthenticated write endpoint `POST /api/v1/kiosk/appointments` (see the [mini-program self-service booking design](/design/app-booking)); the booking banner on the home page is its entry point. The bottom four tabs (home / booking / cards / me): `pages/me` holds the on-device booking summary + cross-device booking lookup (phone number + SMS code, via `my-appointments`, usable in `sms_verified` mode, so the summary no longer disappears when the device changes) + self-service appointment cancellation (`appointment-cancel`, re-issuing a code then cancelling one's own not-yet-arrived, not-yet-expired appointment; decided on the user's behalf 2026-10-16) + theme switching (H5 only) + the white-label self check (for engineering acceptance, moved here from the home page); `pages/card` has phone-based card lookup implemented (phone + SMS-code form; the openid path goes through the mini program's `wx.login` → the server's `/api/v1/kiosk/openid-session` to exchange for an openid, silently falling back to the SMS path when credentials are not configured or the exchange fails; H5 has no `wx.login` and uses only the SMS path; when nothing is bound, entering a phone number + code goes through `openid-bind` for self-service binding (2026-10-15 decision: the openid proves itself and is then bound to the patient record, using the same code channel and audit trail as card lookup, with no front desk and no desktop involved) — both paths are gated by the server-side `sinomed.phone-lookup.mode` tri-state switch (default `off`) and both write to the `card_lookup_logs` audit trail; see the [phone card lookup design](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定). **A logged-in patient session (session persistence) is still not done**: unauthenticated lookup by phone number would expose other people's data (same boundary as the [booking design](/design/app-booking) §4), and a full account system (persistent session after login) is outside the demo MVP scope. **One-shot verified lookup is implemented** — both card lookup in `sms_verified` mode and cross-device booking lookup verify by phone number + one-time code and leave no session afterwards; see the [phone card lookup design](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定). See the [white-label build guide](/app-white-label) for building merchant packages. Business features follow the [admin console](/web).
:::

The patient home page is implemented per the design mock (below is the prototype; the data is fictional demo data, while the live grid data comes from the real endpoint):

![Mini program · home page (implemented screenshot)](/screenshots/mobile-home.png)

## Project structure

| Location | Contents |
| ---- | ---- |
| `config/index.ts` | Taro build configuration (designWidth 375, webpack5, white-label parameter injection, H5 htmlPluginOption) |
| `config/brand.ts` | White-label brand parameter parsing (`BRAND_*` env vars → validated brand config; the defaults are the values built into the repo) |
| `src/services/brand.ts` | Brand constants on the business-code side (compile-time substitution) |
| `src/services/theme.ts` | The four themes (graphite / pine smoke green / cinnabar / indigo): switching on the H5 "me" page + on-device memory (`data-theme` on `documentElement`, via the tokens.css variable groups); the mini program's WXSS has no runtime variable support, so it stays on graphite |
| `src/services/api.ts` | Request wrapper (`Taro.request`; `GET /api/v1/kiosk/items` already wrapped; H5 dev mode goes through the devServer proxy, other targets call the white-label `apiBase` directly) |
| `src/app.config.ts` | Page registration (`pages/index/index`, `pages/booking/index`, `pages/card/index`, `pages/me/index`), nav bar brand name / theme color; bottom tabBar with four tabs (home / booking / cards / me) |
| `src/pages/index/` | Patient home page: brand header / booking banner (entry → self-service booking page) / today's wellness suggestions (grid pulled from items on sale) / my service cards (entry links to the cards page; on-device recall shows greeting + card count + lookup date for 7 days after a lookup, while details and remaining counts are never stored on device; card purchase and remaining-count details are still planned) / wellness tips |
| `src/pages/me/` | Me tab: on-device booking summary + cross-device booking lookup (phone number + code two-factor, via `POST /api/v1/kiosk/my-appointments`, usable in `sms_verified` mode) + self-service cancellation of not-yet-arrived appointments (`POST /api/v1/kiosk/appointment-cancel`, re-issuing a code then cancelling; own and not-yet-expired only) + shortcut entries; theme area (rendered on H5 only) with four theme color dots; white-label self-check footer (brand name / theme color / backend domain / in-page icon) |
| `src/pages/booking/` | Self-service booking page: date strip (today + 7 days) → time-slot grid (9:00–17:00 on the hour, past slots today disabled) → item selection (pulled from items on sale, "decide at the clinic" allowed) → phone number / preferred name submit; the success receipt shows the booking number and slot, and any 400 message from the server is surfaced verbatim |
| `scripts/build-brand.mjs` | One-command white-label build script (parameter validation → injection → build → restore appid) |
| `brand-assets/` | Default icon assets in the repo (merchant icons do not go here; see the [white-label guide](/app-white-label)) |
| `project.config.json` | WeChat DevTools configuration (`miniprogramRoot: ./dist`; the appid is restored automatically after each build) |
| `project.tt.json` | Douyin mini program configuration |
| `babel.config.js` | babel-plugin-import for on-demand NutUI imports |

## Build commands

```bash
cd app
pnpm install
pnpm dev:h5        # H5 dev (browser preview)
pnpm dev:weapp     # WeChat mini program dev (watch mode)
pnpm build:h5      # H5 output → dist/
pnpm build:weapp   # WeChat mini program output → dist/
```

Build scripts for the other targets (swan / alipay / tt / qq / jd / rn / quickapp) are equally available; see `package.json`.

Merchant packages do not use the bare build commands above — always use the [white-label script](/app-white-label):

```bash
node scripts/build-brand.mjs --name <merchant-name> --domain https://... --theme '#RRGGBB' --appid wx...
```

## Key configuration

- **Design spec**: designWidth 375; `pxtransform` converts px to rpx, and NutUI components (the `nut-` prefix) are exempt from the transform;
- **Styling**: plain CSS files (no CSS Modules); `src/tokens.css` and `docs/design/mockups/tokens.css` share one source (the single source of brand variables);
- **UI library**: `@nutui/nutui-react-taro` 2.x, imported on demand via babel-plugin-import;
- **White-label injection**: `BRAND_*` env vars → compile-time substitution (see the [white-label build guide](/app-white-label));
- **webpack**: pinned to 5.104.1 (from 5.111 on, ProgressPlugin added schema validation, which conflicts with the webpackbar bundled in Taro 3.6.40);
- **WeChat appid**: the repo holds the placeholder `touristappid`; a merchant's appid is written into the build output by the build script, and the repo file is never changed.

## Suggestions for whoever picks this up

- The request wrapper already exists (`src/services/api.ts`); extend it per business domain. For authenticated endpoints, follow the admin console's token injection pattern in `src/requestErrorConfig.ts`;
- Page registration lives in `src/app.config.ts`; plan the tabBar around business domains;
- Patient-side identity policy: **no account login session** — patients are unauthenticated, and card lookup / booking lookup verify by openid or phone number + one-time code, then look up and leave no session; the openid is kept in memory only and never persisted. Only the admin console's authentication uses the server's `POST /api/v1/user/login` token-injection pattern (see `web/src/requestErrorConfig.ts`).
