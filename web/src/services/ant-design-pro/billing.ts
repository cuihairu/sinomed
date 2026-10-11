import { request } from '@umijs/max';

/** 支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣（微信/支付宝为演示口径：仅记录方式） */
export const PAY_TYPE = {
  STORED_VALUE: 1,
  WECHAT: 2,
  ALIPAY: 3,
  CASH: 4,
  CARD: 5,
} as const;

export const PAY_TYPE_TEXT: Record<number, string> = {
  1: '储值卡',
  2: '微信',
  3: '支付宝',
  4: '现金',
  5: '次卡',
};

/** 支付图标底色：储值走主题 brand，微信/支付宝/现金按原型分色（admin-billing 口径；次卡取同族灰绿） */
export const PAY_ICON_BG: Record<number, string> = {
  1: 'var(--brand)',
  2: '#5f9e6e',
  3: '#5b7fa6',
  4: '#9a8f7a',
  5: '#8a938c',
};

export interface Recharge {
  id?: number;
  customerId?: number;
  /** 列表联出的展示字段 */
  customerName?: string;
  /** 金额（元，充值为正、储值支付为负） */
  money?: number;
  /** 备注（可空，储值退款补回为「退单 #结算id」） */
  note?: string;
  createTime?: string;
}

export interface RechargeBalance {
  customerId?: number;
  /** 储值余额（元，流水合计，可为 0） */
  balance?: number;
}

export interface Settlement {
  id?: number;
  orderId?: number;
  customerId?: number;
  /** 列表联出的展示字段 */
  customerName?: string;
  payType?: number;
  /** 实收金额（元） */
  money?: number;
  createTime?: string;
  /** 是否已退款（列表联出，undefined 视为未退） */
  refunded?: boolean;
}

/** 退款单（refund_logs） */
export interface RefundRecord {
  id?: number;
  settlementId?: number;
  orderId?: number;
  customerId?: number;
  /** 联出展示字段 */
  customerName?: string;
  /** 退款金额（元，= 原结算实收；次卡为 0） */
  money?: number;
  /** 原支付方式 */
  payType?: number;
  payTypeText?: string;
  /** 退款理由（≤100 字） */
  reason?: string;
  staffId?: number;
  /** 联出展示字段 */
  staffName?: string;
  createTime?: string;
}

/** 日对账·充值行（只含真实充值，不含储值支付扣减与退款补回） */
export interface ReconRecharge {
  id?: number;
  customerId?: number;
  customerName?: string;
  money?: number;
  note?: string;
  createTime?: string;
}

/** 日对账·支付方式构成 */
export interface ReconPayRow {
  payType?: number;
  payTypeText?: string;
  count?: number;
  money?: number;
}

/** 日对账单：收款/充值/退款三段 + 净额与现金净额 */
export interface Reconciliation {
  date?: string;
  /** 收款段 */
  incomeCount?: number;
  incomeMoney?: number;
  cardCount?: number;
  incomePayTypes?: ReconPayRow[];
  /** 充值段（单列不计入营收） */
  rechargeCount?: number;
  rechargeMoney?: number;
  recharges?: ReconRecharge[];
  /** 退款段 */
  refundCount?: number;
  refundMoney?: number;
  refundPayTypes?: ReconPayRow[];
  refunds?: RefundRecord[];
  /** 净额 = 实收 - 退款 */
  netMoney?: number;
  /** 现金净额 = 现金收款 - 现金退款（钱箱核对用） */
  cashNetMoney?: number;
}

export interface BillingPageResult<T> {
  data?: T[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 储值充值：落一条正数流水 */
export async function createRecharge(
  body: { customerId: number; money: number },
  options?: { [key: string]: any },
) {
  return request<Recharge>('/api/v1/recharge/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 储值余额 = recharges 流水合计 */
export async function fetchRechargeBalance(
  customerId: number,
  options?: { [key: string]: any },
) {
  return request<RechargeBalance>('/api/v1/recharge/balance', {
    method: 'GET',
    params: { customerId },
    ...(options || {}),
  });
}

/** 储值流水分页；金额为负的行是储值支付扣减 */
export async function queryRechargePage(
  params: { current?: number; pageSize?: number; customerId?: number },
  options?: { [key: string]: any },
) {
  return request<BillingPageResult<Recharge>>('/api/v1/recharge/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 收款结算：订单 0/1 → 2 已完成，一单一结算；储值支付余额不足报 400 */
export async function settleOrder(
  body: { orderId: number; payType: number },
  options?: { [key: string]: any },
) {
  return request<Settlement>('/api/v1/settlement/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 结算单分页（时间倒序） */
export async function querySettlementPage(
  params: { current?: number; pageSize?: number },
  options?: { [key: string]: any },
) {
  return request<BillingPageResult<Settlement>>('/api/v1/settlement/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 月度收费报表·单日聚合 */
export interface ReportDayRow {
  day?: string;
  count?: number;
  money?: number;
}

/** 月度收费报表·支付方式聚合 */
export interface ReportPayRow {
  payType?: number;
  payTypeText?: string;
  count?: number;
  money?: number;
}

/** 月度收费报表：按结算时间聚合当月结算单；次卡核销实收 0，cardCount 单列 */
export interface SettlementMonthReport {
  month?: string;
  totalCount?: number;
  /** 实收合计（元） */
  totalMoney?: number;
  cardCount?: number;
  days?: ReportDayRow[];
  payTypes?: ReportPayRow[];
}

/** 月度收费报表：month=yyyy-MM */
export async function fetchSettlementMonthReport(
  month: string,
  options?: { [key: string]: any },
) {
  return request<SettlementMonthReport>('/api/v1/settlement/report/month', {
    method: 'GET',
    params: { month },
    ...(options || {}),
  });
}

/** 退单：一笔结算只能退一次；reason 必填 ≤100 字；原路退回，订单回退可重收 */
export async function refundSettlement(
  id: number,
  reason: string,
  options?: { [key: string]: any },
) {
  return request<RefundRecord>(`/api/v1/settlement/${id}/refund`, {
    method: 'POST',
    data: { reason },
    ...(options || {}),
  });
}

/** 日对账单：date=yyyy-MM-dd */
export async function fetchReconciliation(
  date: string,
  options?: { [key: string]: any },
) {
  return request<Reconciliation>('/api/v1/settlement/reconciliation', {
    method: 'GET',
    params: { date },
    ...(options || {}),
  });
}

/** 结算小票打印地址：80mm HTML（需登录；页面须带 token 拉取后写入新窗口，见 Settle 页 openReceipt） */
export const receiptPrintUrl = (settlementId: number) => `/api/v1/print/receipt/${settlementId}`;
