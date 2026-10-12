import { request } from '@umijs/max';

export async function createTreat(body: API.Treat,options?: { [key: string]: any }) {
  return request<API.Treat>('/api/v1/treat/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** 编辑态全量更新：PUT /api/v1/treat/，id/customerId 必填且须存在 */
export async function updateTreat(body: API.Treat,options?: { [key: string]: any }) {
  return request<API.Treat>('/api/v1/treat/', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function fetchTreatById(id:number|string,options?: { [key: string]: any }) {
  return request<API.Treat>(`/api/v1/treat/${id}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
export async function deleteTreat(id: number,options?: { [key: string]: any }) {
  return request<API.MessageResp>(`/api/v1/treat/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}
export async function queryTreatByPage(params: API.QueryTreatParams,options?: { [key: string]: any }) {
  return request<API.QueryTreatResult>('/api/v1/treat/history', {
    method: 'GET',
    params: params,
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

export async function fuzzyQueryTreatByPage(params: API.QueryTreatParams,options?: { [key: string]: any }) {
  return request<API.QueryTreatResult>('/api/v1/treat/fuzzy', {
    method: 'GET',
    params: params,
    headers: {
      'Content-Type': 'application/json',
    },
    ...(options || {}),
  });
}

/** 顾客就诊次数（GET /api/v1/treat/visited?ids=，批量）：visits ≥ 1 = 有既往诊疗记录。
 *  排班页「首诊/复诊」角标依据——复诊=有记录，首诊=无记录（不在返回里）；有疗程卡时疗程标记优先 */
export interface CustomerVisits {
  customerId?: number;
  visits?: number;
}

export async function fetchTreatVisited(ids: number[], options?: { [key: string]: any }) {
  return request<CustomerVisits[]>('/api/v1/treat/visited', {
    method: 'GET',
    params: { ids: ids.join(',') },
    ...(options || {}),
  });
}
