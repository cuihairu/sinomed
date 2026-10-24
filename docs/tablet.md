# Lobby Display (tablet)

::: info Current status
The lobby display is implemented (`tablet/` + the server-side ads/calls module + a single four-tab page in the admin console: materials / schedules / screens / calling). Each item was verified during development; deployment requires hosting `/tablet/` and `/media/` yourself (see the [deployment guide](/server/deploy)). Design and acceptance details are in the [lobby display design](/design/tablet).
:::

The lobby display is a fullscreen Kiosk web page that runs on a **waiting-area tablet**: no app install, no business operations. It auto-enters fullscreen on boot and cycles through the rotation, switching over automatically when the front desk calls a number.

## What it looks like in the waiting area

### Scenario 1: advertising and health-education rotation

The tablet loops through the materials configured in the admin console — clinic service cards, promotional posters (images), and TCM wellness education content (images or videos). Each material has its own independently configurable dwell time (videos use their actual length automatically), and playback switches to the next item seamlessly when one ends.

### Scenario 2: queue-calling integration

About one second after the front desk taps "Call" in the admin console, the tablet automatically switches to a fullscreen call card:

```text
┌──────────────────────────┐
│                          │
│       第二诊室            │
│        请 08 号           │
│        张*                │
│                          │
└──────────────────────────┘
```

- The patient name is masked by default (`张*`) — visible in the waiting area without leaking privacy;
- A tone plus a voice announcement plays (the latter is enabled or disabled automatically depending on device support), and after roughly 15 seconds it **returns to the rotation automatically** — nobody needs to touch the tablet;
- Call sources (two running in parallel; "auto-call on check-in" was approved and implemented on 2026-10-12): ① manual calling from the front desk (initiated from the admin console's `/ads/calls`, still the path for walk-ins without an appointment); ② **auto-call on check-in** — when an appointment is checked in (status 0→1) the server writes a `queue_calls` row automatically: number = that day's check-in sequence number zero-padded to two digits, room = the attending doctor's name + "consulting room" ("unassigned room" when no doctor is assigned), name masked (`张*`), broadcast to all screens (empty `screenId`); skipped or cancelled appointments are not called.

### Scenario 3: playback survives loss of network

Materials are pre-cached locally on the tablet after their first load; when the network drops it keeps playing the cached version and shows an "offline" badge, then pulls the latest schedule automatically once connectivity returns. Note: while offline, **calling is unreachable** (call events come from the server), and calls missed during the outage are not replayed afterwards.

## How materials and schedules are configured (admin console)

Materials, schedules and screens are all maintained in the **admin console (web dash)**; the tablet has no configuration UI at all:

| Setting | Where | Notes |
| ---- | ---- | ---- |
| Upload material | Advertising → Materials | Upload an image / video, and set name, rotation dwell time, enable/disable and sort order |
| Schedule | Advertising → Schedules | Material × effective time window (`HH:mm`) × effective weekdays (empty = every day), bound per screen |
| Room / area targeting | Advertising → Screens | Each tablet registers one screen code (e.g. `lobby-1`, `room-2`); schedules apply per screen |
| Calling | Advertising → Call queue | Enter the number and room (optionally a masked name), pick a target screen or none (all screens), call in one tap |

Schedule hit rule: if the current time falls inside a screen's scheduled window, play the enabled materials bound to that schedule; with no matching schedule, fall back to all enabled materials for that screen. As soon as a material or schedule changes, the tablet refreshes automatically within 30 seconds (content-stamp comparison, media updated by diff).

## Installing the tablet

```text
1. Register the screen in the admin console's "Screens" tab to get a screen code (e.g. lobby-1)
2. Open  https://<deploy-domain>/tablet/?screen=lobby-1  in the tablet browser
3. Add to home screen / set as fullscreen launch page (Android: menu → Add to home screen; a Kiosk-style app can lock this URL directly)
4. Keep it charged and awake; the page requests a best-effort Wake Lock
```

- The tablet side is pure web and runs on any modern tablet browser (Android tablet + Chrome is the reference environment);
- On the deployment side, Nginx serves `/tablet/` as static assets — no app needs to be installed on the tablet.

## Relationship with the desktop workstation and admin console

All three terminals share one server and each has a distinct job:

```text
                 ┌──────────────────────────────┐
                 │  管理端 web（dash）           │
                 │  素材 / 排期 / 屏 / 叫号 管理 │
                 └──────────────┬───────────────┘
                                │ 配置写入
                 ┌──────────────┴───────────────┐
                 │  server（业务中枢）           │
                 │  ads 表 + 下发接口            │
                 └──────┬───────────────┬───────┘
          拉取排期/叫号  │               │  业务接口（建档/诊疗/打印）
                 ┌──────┴──────┐  ┌─────┴─────────┐
                 │ 平板 tablet │  │ 桌面 desktop  │
                 │ 只展示不录入 │  │ 前台/医生工作站│
                 └─────────────┘  └───────────────┘
```

- **The tablet is display-only**: it pulls schedules and call events and never writes business data;
- **The desktop workstation is the business terminal**: patient record creation, treatment entry, printing (see the [desktop design](/design/desktop)); it and the tablet are independent of each other;
- **The admin console is the single configuration entry point**: materials, schedules, screens and calling are all done in the dash.

## Implementation progress

| Capability | Status |
| ---- | ---- |
| Material / schedule / screen tables and APIs | Implemented, verified against every endpoint with curl |
| Tablet rotation / call display / offline cache | Implemented, verified item by item in a browser and headlessly (call displayed in 1.5 s, playback resumes offline) |
| Admin console materials / schedules / screens / calling pages | Implemented, verified in a browser |

See the atomic P0 / P1 acceptance checklist at the end of the [lobby display design](/design/tablet).
