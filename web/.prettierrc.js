// 仓库此前没有 prettier 配置，prettier 按默认值（双引号）跑，会把全仓单引号
// 风格冲成双引号。这里显式采用 @umijs/fabric 的官方 prettier 配置（单引号、
// printWidth 100、尾逗号 all），与仓内既有代码风格一致。
module.exports = require('@umijs/fabric').prettier;
