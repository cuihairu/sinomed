import { request } from '@umijs/max';

export async function createCustomer(body: API.Customer, options?: { [key: string]: any }) {
  return request<API.Customer>('/api/v1/customer/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function updateCustomer(body: API.Customer, options?: { [key: string]: any }) {
  return request<API.Customer>('/api/v1/customer/', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
export async function deleteCustomer(cid: number, options?: { [key: string]: any }) {
  return request<API.Customer>(`/api/v1/customer/${cid}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
export async function queryCustomerByPage(
  params: API.QueryCustomerParams,
  options?: { [key: string]: any },
) {
  return request<API.QueryCustomerResult>('/api/v1/customer/page', {
    method: 'GET',
    params: params,
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchCustomerById(cid: number | string, options?: { [key: string]: any }) {
  return request<API.Customer>(`/api/v1/customer/${cid}`, {
    method: 'GET',
    params: {},
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchCustomerByPhone(phone: string, options?: { [key: string]: any }) {
  return request<API.Customer>(`/api/v1/customer/phone/${phone}`, {
    method: 'GET',
    params: {},
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

/** 顾客病史：逐条记录（过敏/既往/护理方案），新记录在前 */
export interface CustomerHistory {
  id?: number;
  customerId?: number;
  /** 0 过敏史 / 1 既往史 / 2 护理方案（美容业态 d 档案扩展） */
  type?: number;
  content?: string;
  createTime?: string;
}

export async function listCustomerHistories(
  cid: number | string,
  options?: { [key: string]: any },
) {
  return request<CustomerHistory[]>(`/api/v1/customer/${cid}/history`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function addCustomerHistory(
  cid: number | string,
  body: { type: number; content: string },
  options?: { [key: string]: any },
) {
  return request<CustomerHistory>(`/api/v1/customer/${cid}/history`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function deleteCustomerHistory(hid: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/customer/history/${hid}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

/** 顾客标签（美容业态 d）：肤质/偏好轻量标签，同顾客同标签唯一 */
export interface CustomerTag {
  id?: number;
  customerId?: number;
  /** 标签（≤20 字，如「肤质·油性」「偏好·力度大」） */
  tag?: string;
  createTime?: string;
}

export async function listCustomerTags(
  cid: number | string,
  options?: { [key: string]: any },
) {
  return request<CustomerTag[]>(`/api/v1/customer/${cid}/tag`, {
    method: 'GET',
    ...(options || {}),
  });
}

export async function addCustomerTag(
  cid: number | string,
  body: { tag: string },
  options?: { [key: string]: any },
) {
  return request<CustomerTag>(`/api/v1/customer/${cid}/tag`, {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function deleteCustomerTag(tid: number, options?: { [key: string]: any }) {
  return request<{ message?: string }>(`/api/v1/customer/tag/${tid}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

/** 美容敏感信息单独同意（PIPL 口径）：consent 1/0，服务端记时刻与经办员工 */
export async function recordBeautyConsent(
  cid: number | string,
  body: { consent: number },
  options?: { [key: string]: any },
) {
  return request<any>(`/api/v1/customer/${cid}/consent`, {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}
