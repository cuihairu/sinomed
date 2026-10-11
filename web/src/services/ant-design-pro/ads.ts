import { request } from '@umijs/max';

export interface AdMaterial {
  id?: number;
  name?: string;
  /** 叠加层眉题（可空；四项文案全空即纯素材播放） */
  kicker?: string;
  /** 叠加层大标题（可空） */
  title?: string;
  /** 叠加层文案副题（可空） */
  copy?: string;
  /** 价格行，自由文本（可空） */
  price?: string;
  /** 1 图片、2 视频 */
  type?: 1 | 2;
  /** 上传接口返回的相对地址，如 /media/202610/xxx.png */
  url?: string;
  /** 轮播停留时长（毫秒） */
  durationMs?: number;
  /** 1 启用、0 停用 */
  enabled?: number;
  /** 轮播顺序，小者在前 */
  sort?: number;
  createTime?: string;
  updateTime?: string;
}

export interface AdScreen {
  id?: number;
  /** 屏标识，唯一；平板以 ?screen=code 打开 */
  code?: string;
  name?: string;
  location?: string;
  enabled?: number;
  lastSeenAt?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AdSchedule {
  id?: number;
  screenId?: number;
  materialId?: number;
  /** 逗号分隔 1=周一…7=周日；空 = 每天 */
  weekdays?: string;
  /** HH:mm；空 = 全天 */
  startTime?: string;
  endTime?: string;
  enabled?: number;
  createTime?: string;
  updateTime?: string;
}

export interface MediaUploadResult {
  url?: string;
  originalName?: string;
  size?: number;
}

export interface AdPageResult<T> {
  data?: T[];
  total?: number;
  pages?: number;
  success?: boolean;
}

// ---------- 素材 ----------

export async function queryMaterialsByPage(
  params: { current?: number; pageSize?: number; name?: string; enabled?: number },
  options?: { [key: string]: any },
) {
  return request<AdPageResult<AdMaterial>>('/api/v1/ads/materials/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function createMaterial(body: AdMaterial, options?: { [key: string]: any }) {
  return request<AdMaterial>('/api/v1/ads/materials', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateMaterial(body: AdMaterial, options?: { [key: string]: any }) {
  return request<AdMaterial>('/api/v1/ads/materials', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteMaterial(id: number, options?: { [key: string]: any }) {
  return request<Record<string, any>>(`/api/v1/ads/materials/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

// ---------- 屏 ----------

export async function queryScreensByPage(
  params: { current?: number; pageSize?: number; code?: string; name?: string },
  options?: { [key: string]: any },
) {
  return request<AdPageResult<AdScreen>>('/api/v1/ads/screens/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function createScreen(body: AdScreen, options?: { [key: string]: any }) {
  return request<AdScreen>('/api/v1/ads/screens', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateScreen(body: AdScreen, options?: { [key: string]: any }) {
  return request<AdScreen>('/api/v1/ads/screens', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteScreen(id: number, options?: { [key: string]: any }) {
  return request<Record<string, any>>(`/api/v1/ads/screens/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

// ---------- 排期 ----------

export async function querySchedulesByPage(
  params: { current?: number; pageSize?: number; screenId?: number; materialId?: number },
  options?: { [key: string]: any },
) {
  return request<AdPageResult<AdSchedule>>('/api/v1/ads/schedules/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function createSchedule(body: AdSchedule, options?: { [key: string]: any }) {
  return request<AdSchedule>('/api/v1/ads/schedules', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateSchedule(body: AdSchedule, options?: { [key: string]: any }) {
  return request<AdSchedule>('/api/v1/ads/schedules', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteSchedule(id: number, options?: { [key: string]: any }) {
  return request<Record<string, any>>(`/api/v1/ads/schedules/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

// ---------- 上传 ----------

/** 直接走 antd Upload 时用它带 token；action = POST /api/v1/ads/materials/upload */
export const MATERIAL_UPLOAD_ACTION = '/api/v1/ads/materials/upload';

export function uploadAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('jwt');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// ---------- 叫号 ----------

export interface QueueCall {
  id?: number;
  /** 定向屏 id；为空 = 全部屏 */
  screenId?: number | null;
  number?: string;
  room?: string;
  /** 脱敏姓名，如 张* */
  patientMasked?: string;
  /** 0 待叫、1 已叫 */
  status?: number;
  calledAt?: string;
}

export async function createCall(body: QueueCall, options?: { [key: string]: any }) {
  return request<QueueCall>('/api/v1/calls', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function queryRecentCalls(options?: { [key: string]: any }) {
  return request<QueueCall[]>('/api/v1/calls/recent', {
    method: 'GET',
    ...(options || {}),
  });
}
