import type { Key } from 'react';
import { pinyin } from 'pinyin-pro';

/** 会员等级展示口径（同顾客档案）：1 普通 / 2 银卡 / 3 金卡 */
export const LEVEL_TEXT: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员' };

/** 分 → 元（计价展示） */
export const fenToYuan = (fen?: number) => `¥${((fen ?? 0) / 100).toFixed(2)}`;

/** 手机掩码：137****3333（原型 rx-head 口径） */
export const maskPhone = (p?: string) => (p ? p.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '');

/** 特殊煎法提醒关键词：命中即计入「N 味需标注」 */
export const SPECIAL_FX = /先煎|后下|包煎|烊化|冲服|另煎/;

/** 开方页内部流转的顾客简要信息（按手机号定位所得） */
export type CustomerBrief = {
  id?: number;
  name?: string;
  phone?: string;
  level?: number;
  gender?: number;
  age?: number;
};

/** 药名 → 拼音首字母串（目录检索用，如 黄芪→hq）；同名反复输入，带缓存避免逐键重算 */
const initialsCache = new Map<string, string>();
export const herbInitials = (name: string) => {
  let v = initialsCache.get(name);
  if (v === undefined) {
    v = pinyin(name, { pattern: 'first', toneType: 'none', type: 'array' }).join('');
    initialsCache.set(name, v);
  }
  return v;
};

/** 目录检索：中文名包含或拼音首字母命中（原型口径 zz / fs / gcao） */
export const filterHerbDir = (input: string, option?: { value?: Key }) => {
  const q = input.trim().toLowerCase();
  if (!q) return true;
  const name = String(option?.value ?? '');
  return name.toLowerCase().includes(q) || herbInitials(name).includes(q);
};
