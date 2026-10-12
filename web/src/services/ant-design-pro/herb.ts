import { request } from '@umijs/max';

/** 药材字典条目：处方计价的比价依据；price 为每克价格（分） */
export interface Herb {
  id?: number;
  /** 唯一，与处方药名精确比对 */
  name?: string;
  /** 每克价格（分），>0 */
  price?: number;
  /** 库存下限（克，可空=不预警；余额低于下限时余额接口标 warnStock） */
  stockMin?: number;
  /** 库存上限（克，可空=不预警） */
  stockMax?: number;
  /** 单剂克数上限（克/剂，可空=不预警；开方剂量审方超线提示不拦截） */
  doseMax?: number;
  createTime?: string;
}

export interface HerbPageResult {
  data?: Herb[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 收录药材（name 唯一，price 每克分价 >0） */
export async function createHerb(
  body: { name: string; price: number },
  options?: { [key: string]: any },
) {
  return request<Herb>('/api/v1/herb/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 更新药材（改价/改名，名称查重不含自身） */
export async function updateHerb(
  body: { id: number; name: string; price: number },
  options?: { [key: string]: any },
) {
  return request<Herb>('/api/v1/herb/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 删除药材（删后相关处方药味转「未比价」） */
export async function deleteHerb(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/herb/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

/** 字典分页；keyword 非空按名称包含过滤，名称升序 */
export async function queryHerbByPage(
  params: { current?: number; pageSize?: number; keyword?: string },
  options?: { [key: string]: any },
) {
  return request<HerbPageResult>('/api/v1/herb/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 出入库流水：type 1 入库 / 0 出库；quantity 克；expiry 批次效期（入库可带） */
export interface HerbStockLog {
  id?: number;
  herbId?: number;
  /** 联出 */
  herbName?: string;
  type?: number;
  quantity?: number;
  /** yyyy-MM-dd，可空 */
  expiry?: string;
  supplier?: string;
  note?: string;
  createTime?: string;
}

/** 药材库存余额：stock 克；FEFO 口径最早未消耗批次效期与预警 */
export interface HerbStockBalance {
  herbId?: number;
  name?: string;
  stock?: number;
  nextExpiry?: string;
  /** 负数表示已过期 */
  expiryInDays?: number;
  warnExpiry?: boolean;
  /** 库存下限（克，可空=不预警） */
  stockMin?: number;
  /** 库存上限（克，可空=不预警） */
  stockMax?: number;
  /** 是否触库存线（低于下限或高于上限） */
  warnStock?: boolean;
}

/** 登记出入库（出库不得超过当前库存；入库效期不得早于今天） */
export async function createStockLog(
  body: { herbId: number; type: number; quantity: number; expiry?: string; supplier?: string; note?: string },
  options?: { [key: string]: any },
) {
  return request<HerbStockLog>('/api/v1/herb-stock/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 流水分页：herbId/type 可空过滤，id 倒序 */
export async function queryStockPage(
  params: { current?: number; pageSize?: number; herbId?: number; type?: number },
  options?: { [key: string]: any },
) {
  return request<HerbPageResult>('/api/v1/herb-stock/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 库存余额（仅含已有流水的药材，名称升序） */
export async function queryStockBalance(
  params: { expiryWithinDays?: number },
  options?: { [key: string]: any },
) {
  return request<HerbStockBalance[]>('/api/v1/herb-stock/balance', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 删除流水（演示口径无留痕，余额按剩余流水重算） */
export async function deleteStockLog(id: number, options?: { [key: string]: any }) {
  return request<HerbStockLog>(`/api/v1/herb-stock/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
