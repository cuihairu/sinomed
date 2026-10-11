import { request } from '@umijs/max';

/** 订单状态：0 已下单、1 已确认（接待中）、2 已完成、9 已取消 */
export const ORDER_STATUS = {
  CREATED: 0,
  CONFIRMED: 1,
  DONE: 2,
  CANCELLED: 9,
} as const;

export interface OrderItem {
  itemId?: number;
  /** 卡项名（下单时刻快照） */
  itemName?: string;
  /** 单价快照（元） */
  price?: number;
  /** 数量（≥1） */
  qty?: number;
  /** 小计 = 单价 × 数量（元） */
  subtotal?: number;
}

export interface Order {
  id?: number;
  customerId?: number;
  /** 列表联出的展示字段 */
  customerName?: string;
  customerPhone?: string;
  itemId?: number;
  itemName?: string;
  /** 订单明细行（多品项/数量；存量单回退一行数量 1） */
  items?: OrderItem[];
  /** 接待员工 id，Kiosk 单为空 */
  staffId?: number | null;
  status?: number;
  /** 成交价（元；多明细单为各行小计合计） */
  price?: number;
  /** 整单备注（可空，≤200 字） */
  remark?: string | null;
  /** 支付方式（结算联出）：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡，未结算为 null */
  payType?: number;
  createTime?: string;
  updateTime?: string;
}

/** 业务单号（原型口径）：创建日期 + 流水号补零，如 20261008-006 */
export const orderBusinessNo = (order?: Pick<Order, 'id' | 'createTime'>) => {
  if (!order?.id) return '-';
  const day = order.createTime ? new Date(order.createTime) : undefined;
  const date = day && !Number.isNaN(day.getTime())
    ? `${day.getFullYear()}${String(day.getMonth() + 1).padStart(2, '0')}${String(day.getDate()).padStart(2, '0')}-`
    : '';
  return `${date}${String(order.id).padStart(3, '0')}`;
};

export interface OrderPageResult {
  data?: Order[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 顾客消费汇总（顾客档案「累计消费」统计卡）：只统计已完成订单 */
export interface OrderSummary {
  orders?: number;
  /** 累计消费金额（元，已完成订单价格合计） */
  amount?: number;
}

export async function queryOrderPage(
  params: { current?: number; pageSize?: number; status?: number; pending?: boolean },
  options?: { [key: string]: any },
) {
  return request<OrderPageResult>('/api/v1/order/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function fetchOrder(id: number, options?: { [key: string]: any }) {
  return request<Order>(`/api/v1/order/${id}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 按顾客汇总消费：累计消费=已完成订单价格合计 */
export async function fetchOrderSummary(
  customerId: number,
  options?: { [key: string]: any },
) {
  return request<OrderSummary>('/api/v1/order/summary', {
    method: 'GET',
    params: { customerId },
    ...(options || {}),
  });
}

/** 状态流转：0→1 接单、1→2 完成、0/1→9 取消（服务端校验非法流转） */
export async function updateOrderStatus(
  body: { id: number; status: number },
  options?: { [key: string]: any },
) {
  return request<Order>('/api/v1/order/status', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 前台建单：单品传 itemId，多品项传 items[{itemId,qty}]；价格取卡项现价快照并落明细行，落 status=0 待接待 */
export async function createOrder(
  body: { customerId: number; itemId?: number; items?: { itemId: number; qty: number }[]; staffId?: number },
  options?: { [key: string]: any },
) {
  return request<Order>('/api/v1/order/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 整单备注：≤200 字，空串清空 */
export async function updateOrderRemark(
  body: { id: number; remark: string },
  options?: { [key: string]: any },
) {
  return request<Order>('/api/v1/order/remark', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteOrder(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/order/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
