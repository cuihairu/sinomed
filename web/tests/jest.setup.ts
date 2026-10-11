// 全局 mock @umijs/max 的 request：服务层单测只断言请求契约（URL/method/参数），
// 不触网。放在 setupFilesAfterEnv，避免依赖 babel 的 jest.mock 提升行为。
jest.mock('@umijs/max', () => ({
  request: jest.fn(() => Promise.resolve({})),
}));
