// 工具链 glob 配对回归：rimraf 等经 glob 做通配清理/发现，若 glob 与 minimatch 主版本错配
// （glob@10 配 minimatch@11），globSync 会静默返回 0 个匹配——构建门禁不走该路径故不会红。
// 以真实文件断言把该缺陷钉死：本文件自身必须被同一模式命中。
// 见 todo.md「Codex 深审发现」第 1 条与 web/pnpm-workspace.yaml 的 glob override。
import { globSync } from 'glob';

const pattern = 'src/**/*.test.{ts,tsx,js,jsx}';

describe('glob 与 minimatch 配对可发现真实文件', () => {
  it('按模式命中的是本仓真实测试文件，且不为空', () => {
    const files = globSync(pattern);

    expect(files.length).toBeGreaterThan(0);
    expect(files.some((f) => f.endsWith('requestErrorConfig.test.ts'))).toBe(true);
    expect(files.some((f) => f.endsWith('globTooling.test.ts'))).toBe(true);
  });

  it('不匹配的模式返回空数组而非抛错', () => {
    expect(globSync('src/**/*.test.nope')).toEqual([]);
  });
});
