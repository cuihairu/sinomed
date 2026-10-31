/**
 * 色彩 token 对比度回归（tokens.css 是五端色彩唯一真相源）。
 * --faint 是三级/次要文字，必须在所有可能承载它的浅底上过 WCAG AA（普通文字 4.5:1）：
 * 四主题 brand-tint、amber-tint、warn-tint、paper、白底。同时锁 ink > muted > faint 亮度层级，
 * 防止后续调整把「次要/三级文字」层级压平（审核 2026-10-31 发现的回归面）。
 */
import * as fs from 'fs';
import * as path from 'path';

function luminance(hex: string): number {
  const h = hex.replace('#', '');
  const rgb = [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
  const lin = rgb.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2];
}
function contrast(fg: string, bg: string): number {
  const l1 = luminance(fg);
  const l2 = luminance(bg);
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

/** 从 tokens.css 提取 CSS 自定义属性：:root 全局变量 + 每套 [data-theme] 主题变量。 */
function extractVars(block: string): Record<string, string> {
  const vars: Record<string, string> = {};
  const re = /--([\w-]+)\s*:\s*([^;]+);/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(block))) vars[m[1]] = m[2].trim();
  return vars;
}

function readTokens(): { root: Record<string, string>; themeVars: Record<string, string>[] } {
  const css = fs.readFileSync(path.join(__dirname, '../../tokens.css'), 'utf8');
  const rootMatch = css.match(/:root\s*\{([^}]*)\}/);
  if (!rootMatch) throw new Error('tokens.css: missing :root block');
  const root = extractVars(rootMatch[1]);
  const themeVars: Record<string, string>[] = [];
  const themeRe = /\[data-theme="([^"]+)"\]\s*\{([^}]*)\}/g;
  let tm: RegExpExecArray | null;
  while ((tm = themeRe.exec(css))) themeVars.push(extractVars(tm[2]));
  return { root, themeVars };
}

// 运行时从 tokens.css 读取，而非复制字面量；tokens.css 调整后本锁自动跟随（唯一真相源）。
const { root, themeVars } = readTokens();
const TOKENS = {
  ink: root.ink,
  muted: root.muted,
  faint: root.faint,
  paper: root.paper,
  white: root.card,
  brandTints: themeVars.map((t) => t['brand-tint']),
  amberTint: root['amber-tint'],
  warnTint: root['warn-tint'],
};

function rgb(hex: string): number[] {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
}

describe('color token AA regression', () => {
  it('--faint passes 4.5:1 on every tint background that can carry it', () => {
    const backgrounds = [
      ...TOKENS.brandTints,
      TOKENS.amberTint,
      TOKENS.warnTint,
      TOKENS.paper,
      TOKENS.white,
    ];
    for (const bg of backgrounds) {
      expect(contrast(TOKENS.faint, bg)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('keeps ink > muted > faint luminance hierarchy (tier separation)', () => {
    const lum = (c: string) => luminance(c);
    expect(lum(TOKENS.ink)).toBeLessThan(lum(TOKENS.muted));
    expect(lum(TOKENS.muted)).toBeLessThan(lum(TOKENS.faint));
    // 层级间至少留 ΔRGB 距离，防止三级文字与二级文字几乎重合
    const [mr, mg, mb] = rgb(TOKENS.muted);
    const [fr, fg, fb] = rgb(TOKENS.faint);
    const deltaMutedFaint = Math.abs(mr - fr) + Math.abs(mg - fg) + Math.abs(mb - fb);
    expect(deltaMutedFaint).toBeGreaterThan(20);
  });
});
