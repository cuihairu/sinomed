import { request } from '@umijs/max';

/**
 * 外治处方模板：耳穴压豆/穴位贴敷/艾灸/拔罐等外治法的常用组合（方法 + 取穴部位 + 疗程）。
 * 口径：模板只存建议值，接诊页套用后回填针灸处方区随单自由改（不写回模板）。
 */
export interface TherapyTemplate {
  id?: number;
  /** 模板名（唯一，1–20 字） */
  name?: string;
  /** 外治方法（≤10 字） */
  method?: string;
  /** 建议取穴/部位（≤100 字，可空） */
  target?: string;
  /** 建议疗程/频次（≤100 字，可空） */
  course?: string;
  /** 注意事项（≤100 字，可空） */
  note?: string;
  /** 上架：0 停用 / 1 启用 */
  enabled?: number;
  createTime?: string;
  updateTime?: string;
}

export interface TherapyTemplatePageResult {
  data?: TherapyTemplate[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 建模板：name（1–20 字唯一）/ method（≤10 字）必填；enabled 默认 1 */
export async function createTherapyTemplate(
  body: TherapyTemplate,
  options?: { [key: string]: any },
) {
  return request<TherapyTemplate>('/api/v1/therapy-template/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 更新模板：按 id 全量更新；换名撞其他模板报 400 */
export async function updateTherapyTemplate(
  body: TherapyTemplate,
  options?: { [key: string]: any },
) {
  return request<TherapyTemplate>('/api/v1/therapy-template/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 模板分页：id 倒序；name 模糊 / method 精确过滤可选（含停用，管理端口径） */
export async function queryTherapyTemplatePage(
  params: { current?: number; pageSize?: number; name?: string; method?: string },
  options?: { [key: string]: any },
) {
  return request<TherapyTemplatePageResult>('/api/v1/therapy-template/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 上架模板（enabled=1，名称升序）：接诊页「套用外治模板」取数入口 */
export async function queryEnabledTherapyTemplates(options?: { [key: string]: any }) {
  return request<TherapyTemplate[]>('/api/v1/therapy-template/enabled', {
    method: 'GET',
    ...(options || {}),
  });
}

/** 删除模板（演示环境口径，无留痕） */
export async function deleteTherapyTemplate(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/therapy-template/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
