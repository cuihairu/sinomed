# Mini program white-label build guide

Build packages for multiple brands from the same `app/` codebase: brand name, theme color, backend domain, appid and icons all come from **build parameters** — switching merchants means passing different parameters, with no code changes and no extra branches.

## Collect these parameters from the merchant first

| Parameter | CLI flag | Notes | Example |
| ---- | ---- | ---- | ---- |
| Brand name | `--name` | Mini program name, navigation bar title, H5 page title | 和济堂 |
| Backend domain | `--domain` | Server address, must be https | `https://api.hejitang.example.com` |
| Theme color | `--theme` | `#RGB` or `#RRGGBB`; primary color for nav bar / buttons / self-check page | `#8C1F28` |
| Mini program appid | `--appid` | The appid registered on the WeChat platform, 16 characters starting with `wx` | `wx1a2b3c4d5e6f7a8b` |
| Icon | `--icon` | **Only files supplied by the merchant are accepted** — no stand-ins of any kind | `/path/hejitang-icon.png` |
| Description text | `--desc` | H5 page `description` only | 和济堂中医馆线上服务 |
| Build target | `--target` | `weapp` (default) or `h5` | weapp |

With no parameters you get the **default package** (Sinomed / `#C7A674` / `https://sinomed.cuihairu.site` / `touristappid`).

## One-command build

```bash
cd app
node scripts/build-brand.mjs \
  --name 和济堂 \
  --domain https://api.hejitang.example.com \
  --theme '#8C1F28' \
  --appid wx1a2b3c4d5e6f7a8b \
  --icon /path/hejitang-icon.png \
  --target weapp
```

Output goes to `app/dist/`; import it into the WeChat DevTools to preview.

What the script does: parameter validation (theme-color format, appid format, domain scheme, icon file existence) → inject environment variables → `taro build` → restore the appid in the repo-root `project.config.json`. Invalid parameters fail loudly and exit; you never get a package built with wrong parameters.

For an H5 package, change `--target` to `h5`; the brand name / icon / theme color then go into the page title, favicon and meta tags.

## Two verified parameter sets

Both command sets below were actually run and the output verified:

**Default package**

```bash
node scripts/build-brand.mjs
```

Verified: `dist/project.config.json` appid is `touristappid`; `dist/app.json` navigation bar is `Sinomed` / `#C7A674`; no merchant residue anywhere in the output.

**Sample merchant package (和济堂)**

```bash
node scripts/build-brand.mjs \
  --name 和济堂 \
  --domain https://api.hejitang.example.com \
  --theme '#8C1F28' \
  --appid wx1a2b3c4d5e6f7a8b \
  --icon /tmp/hejitang/icon.png
```

Verified: `dist/project.config.json` appid is `wx1a2b3c4d5e6f7a8b`; `dist/app.json` navigation bar is `和济堂` / `#8C1F28`; the icon is injected into the in-page self-check area as base64; after the build the repo-root `project.config.json` is restored to `touristappid` and the git worktree is clean.

In-package self check: the first page you open after the build is the "white-label build parameter self-check page", where all four values — brand name, theme color, backend domain and in-page icon — are visible directly. Check that page first after every build; the parameters are right or wrong at a glance.

## Icon policy

- **Only files supplied by the merchant.** `app/brand-assets/` in the repo holds only the default assets; a merchant icon is never copied in, renamed, recolored or otherwise "processed".
- Pass a png/jpg bitmap: injected into both the H5 favicon and the in-page mini-program icon.
- Pass an svg: only the H5 favicon is injected; the in-page mini-program icon is left empty and the build prints a note (the mini-program `<Image>` component does not accept svg, and no substitute image is swapped in).
- **The mini-program avatar inside WeChat** is uploaded in the WeChat platform console and has nothing to do with this script — the script cannot change what the WeChat platform stores.

## Troubleshooting

**Theme color / brand did not take effect?** Open the self-check page and look at the four values. If they are still wrong, the parameter most likely failed validation (the build fails loudly in that case), or you ran a bare `pnpm build:weapp` and bypassed the script — always build merchant packages through `build-brand.mjs`.

**404s after changing the domain?** Confirm the server is actually deployed at that domain and exposes the `/api/v1` prefix; for local debugging the domain can be `http://localhost:8080`.

**Wrong appid?** The appid is written into `dist/project.config.json` at build time and validated when the WeChat DevTools imports `dist`. Fix the parameter and rebuild; the repo's `project.config.json` is restored automatically after every build, so no merchant appid ever lingers.
