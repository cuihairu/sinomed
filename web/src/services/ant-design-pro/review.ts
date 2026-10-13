import { request } from '@umijs/max';
import moment from 'moment';

export async function createReview(body: API.Review, options?: { [key: string]: any }) {
  return request<API.ReviewStaff>('/api/v1/review/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function fetchReviewByDay(day: Date, options?: { [key: string]: any }) {
  return request<API.Review>('/api/v1/review/day', {
    method: 'GET',
    params: {
      day: moment(day).format('YYYY-MM-DD hh:mm:ss'),
    },
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function createReviewStaff(body: API.ReviewStaff, options?: { [key: string]: any }) {
  return request<API.ReviewStaff>('/api/v1/review/staff', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function createReviewCustomer(
  body: API.ReviewCustomer,
  options?: { [key: string]: any },
) {
  return request<API.ReviewCustomer>('/api/v1/review/customer', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function createReviewStaffBulk(
  body: API.ReviewStaff[],
  options?: { [key: string]: any },
) {
  return request<API.MessageResp>('/api/v1/review/staff/bulk', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function createReviewCustomerBulk(
  body: API.ReviewCustomer[],
  options?: { [key: string]: any },
) {
  return request<API.MessageResp>('/api/v1/review/customer/bulk', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function deleteReviewStaff(id: number, options?: { [key: string]: any }) {
  return request<API.MessageResp>(`/api/v1/review/staff/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function deleteReviewCustomer(id: number, options?: { [key: string]: any }) {
  return request<API.MessageResp>(`/api/v1/review/customer/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchReviewStaffBulk(day: Date, options?: { [key: string]: any }) {
  return request<API.ReviewStaff[]>('/api/v1/review/staff/list/day', {
    method: 'GET',
    params: {
      day: moment(day).format('YYYY-MM-DD hh:mm:ss'),
    },
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchReviewCustomerBulk(day: Date, options?: { [key: string]: any }) {
  return request<API.ReviewCustomer[]>('/api/v1/review/customer/list/day', {
    method: 'GET',
    params: {
      day: moment(day).format('YYYY-MM-DD hh:mm:ss'),
    },
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fetchCustomerReviews(customerId: number, options?: { [key: string]: any }) {
  return request<API.ReviewCustomer[]>('/api/v1/review/customer/list/customer', {
    method: 'GET',
    params: {
      customerId,
    },
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
