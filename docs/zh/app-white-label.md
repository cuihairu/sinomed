# 小程序换马甲构建指南

同一套 `app/` 代码出多个品牌的包：品牌名、主题色、后端域名、appid、图标全部走**构建参数**，换商家只传参数，不改代码、不建分支。

## 先向商家收齐这些参数

| 参数 | 命令行参数 | 说明 | 示例 |
| ---- | ---- | ---- | ---- |
| 品牌名 | `--name` | 小程序名称、导航栏标题、H5 页面标题 | 和济堂 |
| 后端域名 | `--domain` | 服务端地址，须 https | `https://api.hejitang.example.com` |
| 主题色 | `--theme` | `#RGB` 或 `#RRGGBB`，导航栏/按钮/自检页主色 | `#8C1F28` |
| 小程序 appid | `--appid` | 微信公众平台注册的 appid，wx 开头 16 位 | `wx1a2b3c4d5e6f7a8b` |
| 图标 | `--icon` | **只收商家自己提供的文件**，不用任何代餐 | `/path/hejitang-icon.png` |
| 描述文案 | `--desc` | 仅 H5 页面 description | 和济堂中医馆线上服务 |
| 构建目标 | `--target` | `weapp`（默认）或 `h5` | weapp |

不传参数即出**默认包**（Sinomed / `#C7A674` / `https://sinomed.cuihairu.site` / `touristappid`）。

## 一键出包

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

产物在 `app/dist/`，导入微信开发者工具即可预览。

脚本做的事：参数校验（主题色格式、appid 格式、域名协议、图标文件存在）→ 注入环境变量 → `taro build` → 把根目录 `project.config.json` 的 appid 改回原样。参数有错当场报错退出，不会出一个装着错参数的包。

H5 包把 `--target` 换成 `h5`，品牌名/图标/主题色会进页面标题、favicon 和 meta。

## 两套参数实证

以下两组命令都实际跑通，产物核验过：

**默认包**

```bash
node scripts/build-brand.mjs
```

核验：`dist/project.config.json` appid 为 `touristappid`；`dist/app.json` 导航栏 `Sinomed` / `#C7A674`；产物内无商家残留。

**示例商家包（和济堂）**

```bash
node scripts/build-brand.mjs \
  --name 和济堂 \
  --domain https://api.hejitang.example.com \
  --theme '#8C1F28' \
  --appid wx1a2b3c4d5e6f7a8b \
  --icon /tmp/hejitang/icon.png
```

核验：`dist/project.config.json` appid 为 `wx1a2b3c4d5e6f7a8b`；`dist/app.json` 导航栏 `和济堂` / `#8C1F28`；图标以 base64 注入自检页；构建后根 `project.config.json` 恢复为 `touristappid`，git 工作区干净。

包内自检：打开首页即「白标构建参数自检页」，品牌名、主题色、后端域名、页内图标四项直接可见——出包后先看这页，参数对不对一眼确认。

## 图标口径

- **只用商家提供的文件。** 仓库里 `app/brand-assets/` 只放默认资产，商家图标不会被拷进去，也不会被改名、转色、做任何"处理"。
- 传 png/jpg 位图：同时注入 H5 favicon 和小程序页内图标。
- 传 svg：只注入 H5 favicon；小程序页内图标位留空并在构建时提示（小程序 `<Image>` 不吃 svg，不代餐换别的图）。
- **微信里的小程序头像**在微信公众平台后台上传，与本脚本无关——脚本改不了微信平台上的资料。

## 常见问题

**主题色/品牌没生效？** 打开自检页看四项参数。还不对，多半是参数格式没过校验（构建会直接报错退出），或手工 `pnpm build:weapp` 绕过了脚本——换包统一走 `build-brand.mjs`。

**换域名后请求 404？** 确认服务端部署在该域名且带 `/api/v1` 前缀；本地调试域名可用 `http://localhost:8080`。

**appid 写错了？** 构建时 appid 会写进 `dist/project.config.json`，微信开发者工具导入 dist 时会校验。改对参数重跑即可，仓库里的 `project.config.json` 每次构建后自动恢复原样，不会残留商家 appid。
