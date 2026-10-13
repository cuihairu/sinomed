import { request } from '@umijs/max';

/** 顾客持卡（次卡）：itemName/customerName 为列表联出展示字段 */
export interface Card {
  id?: number;
  customerId?: number;
  /** 列表联出的展示字段 */
  customerName?: string;
  itemId?: number;
  /** 列表联出的展示字段 */
  itemName?: string;
  totalTimes?: number;
  remainingTimes?: number;
  /** 1 有效 / 0 停用 */
  status?: number;
  sourceOrderId?: number;
  /** 有效期至（可空=长期有效），过期卡不参与抵扣 */
  validUntil?: string;
  createTime?: string;
}

/** 发卡：customerId + itemId + totalTimes（≥1）必填；余次=总次数、状态有效；validUntil 传当天末毫秒数（可空=长期有效） */
export async function issueCard(
  body: {
    customerId: number;
    itemId: number;
    totalTimes: number;
    sourceOrderId?: number;
    validUntil?: number;
  },
  options?: { [key: string]: any },
) {
  return request<Card>('/api/v1/card/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 按顾客查持卡（新卡在前） */
export async function listCardsByCustomer(customerId: number, options?: { [key: string]: any }) {
  return request<Card[]>(`/api/v1/card/list?customerId=${customerId}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 批量按顾客查持卡（排班格位疗程标记等聚合场景，一次拉齐避免逐顾客查询；ids 逗号分隔） */
export async function listCardsByCustomers(ids: number[], options?: { [key: string]: any }) {
  return request<Card[]>(`/api/v1/card/customers?ids=${ids.join(',')}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 停用 / 恢复：status 1 有效 / 0 停用 */
export async function setCardStatus(id: number, status: number, options?: { [key: string]: any }) {
  return request<Card>(`/api/v1/card/${id}/status?status=${status}`, {
    method: 'PUT',
    ...(options || {}),
  });
}

/** 次卡核销记录：每次结算抵扣一条（补录口径无订单号） */
export interface CardUsage {
  id?: number;
  cardId?: number;
  orderId?: number;
  staffId?: number;
  /** 列表联出的展示字段 */
  staffName?: string;
  /** 本次是第几次消费（1 起） */
  timesUsed?: number;
  createTime?: string;
}

/** 按持卡查核销记录（新记录在前） */
export async function listCardUsages(id: number, options?: { [key: string]: any }) {
  return request<CardUsage[]>(`/api/v1/card/${id}/usages`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 持卡调整流水（疗程卡营销留痕：赠次/转卡） */
export interface CardAdjust {
  id?: number;
  cardId?: number;
  /** 1 赠次 / 2 转卡 */
  type?: number;
  /** 赠加次数（1-99；转卡记 0） */
  times?: number;
  /** 转卡目标顾客（联出展示名） */
  customerId?: number;
  customerName?: string;
  /** 调整理由（必填 ≤100 字） */
  reason?: string;
  staffId?: number;
  /** 联出展示字段 */
  staffName?: string;
  createTime?: string;
}

/** 赠次：有效卡 total/remaining 各加 N（1-99），理由必填；停用卡 400 */
export async function adjustCard(
  id: number,
  body: { times: number; reason: string },
  options?: { [key: string]: any },
) {
  return request<Card>(`/api/v1/card/${id}/adjust`, {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 转卡：有效卡换归属顾客（余次/有效期照旧），理由必填；目标顾客须存在且非本卡现顾客 */
export async function transferCard(
  id: number,
  body: { customerId: number; reason: string },
  options?: { [key: string]: any },
) {
  return request<Card>(`/api/v1/card/${id}/transfer`, {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 按持卡查调整流水（赠次/转卡，新记录在前，联操作人/目标顾客名） */
export async function listCardAdjusts(id: number, options?: { [key: string]: any }) {
  return request<CardAdjust[]>(`/api/v1/card/${id}/adjusts`, {
    method: 'GET',
    ...(options || {}),
  });
}
