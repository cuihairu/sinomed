import { request } from '@umijs/max';

export interface CommissionRule {
  id?: number;
  /** 卡项 id（items.id） */
  itemId?: number;
  /** 卡项名（列表联出） */
  itemName?: string;
  /** 岗位：staffs.title 精确匹配，如 技师/美容师/医师 */
  title?: string;
  /** 提成比例：整数百分比 0–100，金额 = 行小计 × ratio / 100 */
  ratio?: number;
  createTime?: string;
  updateTime?: string;
}

export interface CommissionRecord {
  id?: number;
  orderId?: number;
  settlementId?: number;
  orderItemId?: number;
  staffId?: number;
  /** 员工名（列表联出） */
  staffName?: string;
  itemId?: number;
  /** 卡项名快照 */
  itemName?: string;
  /** 行小计快照（元） */
  subtotal?: number;
  /** 比例快照（百分比） */
  ratio?: number;
  /** 提成金额（元，退单冲销为负） */
  amount?: number;
  /** 备注（退单冲销记「退单冲销 #结算id」） */
  remark?: string;
  createTime?: string;
}

export interface CommissionRecordPageResult {
  data?: CommissionRecord[];
  total?: number;
  pages?: number;
  success?: boolean;
}

export interface CommissionSummary {
  staffId?: number;
  staffName?: string;
  total?: number;
}

export async function queryCommissionRuleList(
  params?: { itemId?: number },
  options?: { [key: string]: any },
) {
  return request<CommissionRule[]>('/api/v1/commission/rule', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function createCommissionRule(
  body: CommissionRule,
  options?: { [key: string]: any },
) {
  return request<CommissionRule>('/api/v1/commission/rule', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateCommissionRule(
  body: CommissionRule,
  options?: { [key: string]: any },
) {
  return request<CommissionRule>('/api/v1/commission/rule', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteCommissionRule(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/commission/rule/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

export async function queryCommissionRecordPage(
  params: {
    current?: number;
    pageSize?: number;
    orderId?: number;
    staffId?: number;
  },
  options?: { [key: string]: any },
) {
  return request<CommissionRecordPageResult>('/api/v1/commission/record/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 按员工合计（工资基数）：净额 = 正向 + 冲销 */
export async function queryCommissionSummary(options?: { [key: string]: any }) {
  return request<CommissionSummary[]>('/api/v1/commission/record/summary', {
    method: 'GET',
    ...(options || {}),
  });
}
