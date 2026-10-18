import { request } from '@umijs/max';

export interface Room {
  id?: number;
  /** 工位名，唯一 */
  name?: string;
  /** 1 在用、0 停用（预约表单只列在用工位） */
  enabled?: number;
  createTime?: string;
  updateTime?: string;
}

/** 工位全量列表（含停用；按 id 升序） */
export async function queryRoomList(options?: { [key: string]: any }) {
  return request<Room[]>('/api/v1/room/', {
    method: 'GET',
    ...(options || {}),
  });
}

export async function createRoom(body: Room, options?: { [key: string]: any }) {
  return request<Room>('/api/v1/room/', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateRoom(body: Room, options?: { [key: string]: any }) {
  return request<Room>('/api/v1/room/', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteRoom(id: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/room/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
