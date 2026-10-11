import { request } from '@umijs/max';

/** 预约状态：0 待到店、1 已接待、8 过号、9 已取消（8/9 为终态） */
export const APPOINTMENT_STATUS = {
  BOOKED: 0,
  RECEIVED: 1,
  NO_SHOW: 8,
  CANCELLED: 9,
} as const;

export interface Appointment {
  id?: number;
  customerId?: number;
  /** 列表联出的展示字段 */
  customerName?: string;
  customerPhone?: string;
  itemId?: number;
  itemName?: string;
  staffId?: number | null;
  /** 接待员工名（列表联出） */
  staffName?: string;
  /** 预约时段开始时刻 */
  startTime?: string;
  /** 时长（分钟） */
  duration?: number;
  status?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AppointmentPageResult {
  data?: Appointment[];
  total?: number;
  pages?: number;
  success?: boolean;
}

export async function createAppointment(
  body: Appointment,
  options?: { [key: string]: any },
) {
  return request<Appointment>('/api/v1/appointment/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function queryAppointmentPage(
  params: { current?: number; pageSize?: number; status?: number; date?: string },
  options?: { [key: string]: any },
) {
  return request<AppointmentPageResult>('/api/v1/appointment/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 状态流转：0→1 到店接待、0→8 过号、0→9 取消（服务端校验非法流转，过号/取消为终态） */
export async function updateAppointmentStatus(
  body: { id: number; status: number },
  options?: { [key: string]: any },
) {
  return request<Appointment>('/api/v1/appointment/status', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteAppointment(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/appointment/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
