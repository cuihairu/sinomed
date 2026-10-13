#!/usr/bin/env node
/*
 * docs 结构检查（对应 docs/design/mockups/README.md §7 三条维护清单的可自动化部分）：
 *   A. Markdown 图片引用必须解析到真实文件 —— VitePress build 不校验图片链接，
 *      断链只在浏览器里 404，构建照样绿灯，所以这里单独查。
 *      路径语义与 VitePress 一致：`/xxx` 视为 docs/public 根，其余相对所在 .md 目录。
 *      跳过 http(s)/data/srcset 之外的外链，以及代码块与行内代码里的示例串
 *      （`![alt](path)` 写在反引号里不会渲染成图片）。
 *   B. HTML `<img src="/...">` 绝对路径必须命中 docs/public —— VitePress 只对
 *      Markdown 语法重写 base，HTML 标签里的绝对路径不重写，是该站最经典的 404 来源。
 *   C. 每份原型 HTML 必须挂 tokens.css（五端唯一样式源），否则主题切换失效。
 *   D. tokens.css 必须保留四主题：:root 默认石墨 + pine/cinnabar/indigo 三块。
 *
 * 用法：pnpm -C docs check    （docs.yml 在 build 前调用）
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const IGNORED_DIRS = new Set(['node_modules', '.vitepress', '.git']);
const MOCKUPS = join(ROOT, 'design', 'mockups');
const TOKENS = join(MOCKUPS, 'tokens.css');
const PUBLIC = join(ROOT, 'public');

/** 收集 docs 下全部 Markdown（跳过依赖与构建产物） */
function markdownFiles(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (IGNORED_DIRS.has(name)) continue;
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) markdownFiles(p, out);
    else if (name.endsWith('.md')) out.push(p);
  }
  return out;
}

/**
 * 掩掉不渲染成图片的上下文：围栏代码块整段、行内代码片段。
 * 用等长空白替换以保留行/列信息，便于报错定位。
 */
function stripCodeBlocks(text) {
  const lines = text.split('\n');
  let inFence = false;
  const out = lines.map((line) => {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence;
      return ' '.repeat(line.length);
    }
    if (inFence) return ' '.repeat(line.length);
    return line.replace(/`[^`\n]*`/g, (m) => ' '.repeat(m.length));
  });
  return out.join('\n');
}

const rel = (p) => relative(ROOT, p).split('\\').join('/');
const failures = [];

// --- A. Markdown 图片引用 ---
for (const file of markdownFiles(ROOT)) {
  const source = stripCodeBlocks(readFileSync(file, 'utf8'));
  const re = /!\[[^\]]*\]\(([^)]+)\)/g;
  let m;
  while ((m = re.exec(source)) !== null) {
    let src = m[1].trim();
    if (/^(https?:)?\/\//.test(src) || src.startsWith('data:') || src.startsWith('#')) continue;
    src = src.split('#')[0].split('?')[0];
    if (!src) continue;
    const abs = isAbsolute(src) ? join(PUBLIC, src) : join(dirname(file), src);
    if (!statSyncSafe(abs)) failures.push(`图片断链 ${rel(file)}: ${src}`);
  }
}

/** 只读判定文件是否存在，避免对目录 stat 抛异常 */
function statSyncSafe(p) {
  try {
    return statSync(p).isFile();
  } catch {
    return false;
  }
}

// --- B. HTML <img src="/..."> 绝对路径必须命中 docs/public ---
for (const file of markdownFiles(ROOT)) {
  const source = stripCodeBlocks(readFileSync(file, 'utf8'));
  const re = /<img\b[^>]*\bsrc="([^"]+)"/g;
  let m;
  while ((m = re.exec(source)) !== null) {
    const src = m[1].trim();
    if (!isAbsolute(src) || /^(https?:)?\/\//.test(src) || src.startsWith('data:')) continue;
    const abs = join(PUBLIC, src);
    if (!statSyncSafe(abs)) {
      failures.push(`HTML 绝对路径图片断链 ${rel(file)}: ${src}（VitePress 不重写 HTML 标签里的绝对路径）`);
    }
  }
}

// --- C. 原型 HTML 必须挂 tokens.css ---
for (const name of readdirSync(MOCKUPS)) {
  if (!name.endsWith('.html')) continue;
  const html = readFileSync(join(MOCKUPS, name), 'utf8');
  if (!/href=["']tokens\.css["']/.test(html)) {
    failures.push(`原型未引入 tokens.css：design/mockups/${name}`);
  }
}

// --- D. tokens.css 四主题齐全（:root 默认 + 三套可切换） ---
const tokens = readFileSync(TOKENS, 'utf8');
if (!/^\s*:root\s*{/m.test(tokens)) failures.push('tokens.css 缺少 :root 默认主题块（石墨）');
const themeBlocks = tokens.match(/^\s*\[data-theme=["'][^"']+["']\]/gm) || [];
if (themeBlocks.length !== 3) {
  failures.push(`tokens.css 可切换主题块应为 3 个，实际 ${themeBlocks.length}（:root 默认主题不计入）`);
}

// --- 汇总 ---
if (failures.length) {
  for (const f of failures) console.error(`✗ ${f}`);
  console.error(`\n${failures.length} 项检查失败`);
  process.exit(1);
}
console.log('docs 结构检查通过：图片引用无断链、原型样式源与四主题齐全');
