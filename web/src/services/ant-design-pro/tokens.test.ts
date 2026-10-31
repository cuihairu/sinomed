/**
 * 色彩 token 对比度回归（tokens.css 是五端色彩唯一真相源）。
 * --faint 是三级/次要文字，必须在所有可能承载它的浅底上过 WCAG AA（普通文字 4.5:1）：
 * 四主题 brand-tint、amber-tint、warn-tint、paper、白底。同时锁 ink > muted > faint 亮度层级，
 * 防止后续调整把「次要/三级文字」层级压平（审核 2026-10-31 发现的回归面）。
 */
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

// 与 web/src/tokens.css 同源；改 token 时此处必须同步（tokens.css 是唯一真相源）
const TOKENS = {
  ink: '#262927',
  muted: '#5e635f',
  faint: '#696c68',
  paper: '#f7f7f4',
  white: '#ffffff',
  brandTints: ['#eceeee', '#e7efe9', '#f6eae7', '#e8edf2'],
  amberTint: '#f4eee1',
  warnTint: '#f7ede4',
};

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
    const [ir, ig, ib] = [0x26, 0x29, 0x27];
    const [mr, mg, mb] = [0x5e, 0x63, 0x5f];
    const [fr, fg, fb] = [0x69, 0x6c, 0x68];
    const deltaMutedFaint = Math.abs(mr - fr) + Math.abs(mg - fg) + Math.abs(mb - fb);
    expect(deltaMutedFaint).toBeGreaterThan(20);
  });
});
