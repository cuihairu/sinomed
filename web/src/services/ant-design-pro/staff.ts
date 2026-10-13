import { request } from '@umijs/max';

export async function createStaff(body: API.Staff, options?: { [key: string]: any }) {
  return request<API.Staff>('/api/v1/staff/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function updateStaff(body: API.Staff, options?: { [key: string]: any }) {
  return request<API.Staff>('/api/v1/staff/', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function sign(body: API.Sign, options?: { [key: string]: any }) {
  return request<API.SignResult>('/api/v1/staff/sign', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function deleteStaff(sid: number, options?: { [key: string]: any }) {
  return request<API.Staff>(`/api/v1/staff/${sid}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
export async function fetchStaffById(sid: number | string, options?: { [key: string]: any }) {
  return request<API.Staff>(`/api/v1/staff/${sid}`, {
    method: 'GET',
    params: {},
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function queryStaffTodayTimesheet(options?: { [key: string]: any }) {
  return request<API.DayTimesheet>('/api/v1/staff/timesheet/today', {
    method: 'GET',
    params: {},
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchMouthTimesheet(
  month: number | string | undefined,
  options?: { [key: string]: any },
) {
  return request<API.MonthTimesheet>(`/api/v1/staff/timesheet/month`, {
    method: 'GET',
    params: {
      month: month,
    },
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchWeekTimesheet(weekDate?: string, options?: { [key: string]: any }) {
  return request<API.WeekTimesheet>('/api/v1/staff/timesheet/week', {
    method: 'GET',
    params: {
      weekDate: weekDate || undefined,
    },
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
export async function queryStaffByPage(
  params: API.QueryStaffParams,
  options?: { [key: string]: any },
) {
  return request<API.QueryStaffResult>('/api/v1/staff/page', {
    method: 'GET',
    params: params,
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchTimesheet(body: API.Sign, options?: { [key: string]: any }) {
  return request<API.SignResult>('/api/v1/staff/sign', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** 员工请假记录（staffName 为列表联出展示字段） */
export interface StaffLeave {
  id?: number;
  staffId?: number;
  /** 列表联出的展示字段 */
  staffName?: string;
  /** 类型：0 病假 / 1 事假 */
  leaveType?: number;
  reason?: string;
  startTime?: string;
  endTime?: string;
  createTime?: string;
}

export interface StaffLeavePageResult {
  data?: StaffLeave[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 提交请假：staffId + leaveType（0 病假 / 1 事假）+ reason + startTime/endTime 必填 */
export async function createLeave(
  body: { staffId: number; leaveType: number; reason: string; startTime: string; endTime: string },
  options?: { [key: string]: any },
) {
  return request<StaffLeave>('/api/v1/staff/leave/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 请假分页（id 倒序，联出员工名）；staffId 可选过滤 */
export async function queryLeavePage(
  params: { current?: number; pageSize?: number; staffId?: number },
  options?: { [key: string]: any },
) {
  return request<StaffLeavePageResult>('/api/v1/staff/leave/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 删除请假记录（演示环境口径，无留痕） */
export async function deleteLeave(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/staff/leave/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
