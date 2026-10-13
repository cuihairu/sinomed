import { request } from '@umijs/max';

/** 员工班次条目：周期班表（员工 × 星期 × HH:mm 时段，同员工同星期唯一）；考勤打卡仍以 signs 为准 */
export interface Shift {
  id?: number;
  /** 员工 id */
  staffId?: number;
  /** 员工姓名（列表联出展示字段） */
  staffName?: string;
  /** 星期（1 周一 … 7 周日） */
  weekday?: number;
  /** 星期文案（周一 … 周日，由后端给出） */
  weekdayText?: string;
  /** 上班时间 HH:mm */
  start?: string;
  /** 下班时间 HH:mm */
  end?: string;
  createTime?: string;
}

export interface ShiftPageResult {
  data?: Shift[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 加排班：staffId + weekday（1-7）+ start/end（HH:mm，start < end）必填；同员工同星期唯一 */
export async function createShift(body: Shift, options?: { [key: string]: any }) {
  return request<Shift>('/api/v1/shift/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

/** 更新班次：按 id 全量更新；换员工/星期撞已有班次报 400 */
export async function updateShift(body: Shift, options?: { [key: string]: any }) {
  return request<Shift>('/api/v1/shift/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

/** 班次分页；staffId/weekday 可空过滤，按员工、星期升序 */
export async function queryShiftPage(
  params: { current?: number; pageSize?: number; staffId?: number; weekday?: number },
  options?: { [key: string]: any },
) {
  return request<ShiftPageResult>('/api/v1/shift/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 某员工整周班次（weekday 升序） */
export async function queryShiftByStaff(staffId: number, options?: { [key: string]: any }) {
  return request<Shift[]>(`/api/v1/shift/staff/${staffId}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 删除班次（不影响考勤记录；演示环境口径，无留痕） */
export async function deleteShift(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/shift/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
