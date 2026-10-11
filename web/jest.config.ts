import { createConfig } from '@umijs/max/test';

// 不走 configUmiAlias：它会用 esbuild-register 解析 config/config.ts，Node 24 下崩溃。
// createConfig 只提供转换器与环境，别名映射在此手工补齐，与 umi 运行时配置解耦。
export default async () => {
  const config = createConfig({
    target: 'browser',
  });

  return {
    ...config,
    moduleNameMapper: {
      ...(config.moduleNameMapper || {}),
      '^@@/(.*)$': '<rootDir>/src/.umi-test/$1',
      '^@/(.*)$': '<rootDir>/src/$1',
    },
    setupFiles: [...(config.setupFiles || []), './tests/setupTests.jsx'],
    setupFilesAfterEnv: ['<rootDir>/tests/jest.setup.ts'],
  };
};
