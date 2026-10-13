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

/** 顾客病史：逐条记录（过敏/既往），新记录在前 */
export interface CustomerHistory {
  id?: number;
  customerId?: number;
  /** 0 过敏史 / 1 既往史 */
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
