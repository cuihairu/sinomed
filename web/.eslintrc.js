// Umi 4 官方 lint 配置（eslintrc 格式）。
// @umijs/lint 4.7 仍只发布 eslintrc 版配置（见 dist/config/eslint/index.js），
// 与 ESLint 8 配套；ESLint 9 已移除 eslintrc 支持，故此处固定 eslint 8.35.0。
const config = require('@umijs/lint/dist/config/eslint');

// eqeqeq：放行 `x == null` / `x != null` 判空惯用法（等价于 null 或 undefined），
// 其余场景仍强制 === / !==（与 airbnb 同一口径）。
config.rules.eqeqeq = ['error', 'always', { null: 'ignore' }];

module.exports = config;
