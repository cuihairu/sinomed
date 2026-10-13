import { request } from '@umijs/max';

/** 穴位字典条目：经络穴位参考（穴名唯一），接诊取穴字段仍为自由文本 */
export interface Acupoint {
  id?: number;
  /** 穴位名（如「足三里」），唯一 1–10 字 */
  name?: string;
  /** 拼音检索码（全拼小写，如 zusanli） */
  pinyin?: string;
  /** 归经（如「足阳明胃经」；经外奇穴归「经外奇穴」） */
  meridian?: string;
  /** 体表定位 */
  location?: string;
  /** 主治简述 */
  indication?: string;
  createTime?: string;
}

export interface AcupointPageResult {
  data?: Acupoint[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 收录穴位：name + pinyin（全拼小写字母）+ meridian（归经）必填 */
export async function createAcupoint(body: Acupoint, options?: { [key: string]: any }) {
  return request<Acupoint>('/api/v1/acupoint/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 更新穴位：按 id 全量更新；换名撞其他穴位报 400 */
export async function updateAcupoint(body: Acupoint, options?: { [key: string]: any }) {
  return request<Acupoint>('/api/v1/acupoint/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 穴位分页；keyword 模糊匹配穴名或拼音码（如「足三」或「zusanli」） */
export async function queryAcupointPage(
  params: { current?: number; pageSize?: number; keyword?: string },
  options?: { [key: string]: any },
) {
  return request<AcupointPageResult>('/api/v1/acupoint/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 删除穴位（不影响已开接诊单；演示环境口径，无留痕） */
export async function deleteAcupoint(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/acupoint/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
