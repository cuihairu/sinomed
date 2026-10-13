import { request } from '@umijs/max';

export async function createItem(body: API.Item, options?: { [key: string]: any }) {
  return request<API.Item>('/api/v1/item/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function fetchItem(id: number | string, options?: { [key: string]: any }) {
  return request<API.Item>(`/api/v1/item/${id}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function queryItemByPage(
  params: API.QueryItemParams,
  options?: { [key: string]: any },
) {
  return request<API.QueryItemResult>('/api/v1/item/page', {
    method: 'GET',
    params: params,
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function updateItem(body: API.Item, options?: { [key: string]: any }) {
  return request<API.Item>('/api/v1/item/', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function deleteItem(id: number, options?: { [key: string]: any }) {
  return request<API.Item>(`/api/v1/item/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
