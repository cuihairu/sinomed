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
  body: { customerId: number; itemId: number; totalTimes: number; sourceOrderId?: number; validUntil?: number },
  options?: { [key: string]: any },
) {
  return request<Card>('/api/v1/card/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 按顾客查持卡（新卡在前） */
export async function listCardsByCustomer(
  customerId: number,
  options?: { [key: string]: any },
) {
  return request<Card[]>(`/api/v1/card/list?customerId=${customerId}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 批量按顾客查持卡（排班格位疗程标记等聚合场景，一次拉齐避免逐顾客查询；ids 逗号分隔） */
export async function listCardsByCustomers(
  ids: number[],
  options?: { [key: string]: any },
) {
  return request<Card[]>(`/api/v1/card/customers?ids=${ids.join(',')}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 停用 / 恢复：status 1 有效 / 0 停用 */
export async function setCardStatus(
  id: number,
  status: number,
  options?: { [key: string]: any },
) {
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
export async function listCardUsages(
  id: number,
  options?: { [key: string]: any },
) {
  return request<CardUsage[]>(`/api/v1/card/${id}/usages`, {
    method: 'GET',
    ...(options || {}),
  });
}
