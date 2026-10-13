import { request } from '@umijs/max';

/** 方剂药味（与处方药味同构） */
export interface FormulaHerb {
  herb?: string;
  /** 单剂克数 */
  weight?: number;
  /** 特殊煎法：先煎/后下/包煎等，可空 */
  special?: string;
  sort?: number;
}

/** 方剂库条目：经典方剂参考（方名唯一），herbs 为药味组成 */
export interface Formula {
  id?: number;
  /** 方名（如「逍遥散」），唯一 1–20 字 */
  name?: string;
  /** 拼音检索码（全拼小写，如 xiaoyaosan） */
  pinyin?: string;
  /** 出处（如「太平惠民和剂局方」） */
  source?: string;
  /** 功效主治简述 */
  indication?: string;
  herbs?: FormulaHerb[];
  createTime?: string;
}

export interface FormulaPageResult {
  data?: Formula[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 建方剂：name + pinyin（全拼小写字母）+ herbs（至少 1 味）必填 */
export async function createFormula(body: Formula, options?: { [key: string]: any }) {
  return request<Formula>('/api/v1/formula/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 更新方剂：按 id 全量更新，药味全量替换；换名撞其他方剂报 400 */
export async function updateFormula(body: Formula, options?: { [key: string]: any }) {
  return request<Formula>('/api/v1/formula/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 方剂分页；keyword 模糊匹配方名或拼音码（如「逍遥」或「xiaoyao」） */
export async function queryFormulaPage(
  params: { current?: number; pageSize?: number; keyword?: string },
  options?: { [key: string]: any },
) {
  return request<FormulaPageResult>('/api/v1/formula/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 删除方剂（连同药味；不影响已开处方；演示环境口径，无留痕） */
export async function deleteFormula(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/formula/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
