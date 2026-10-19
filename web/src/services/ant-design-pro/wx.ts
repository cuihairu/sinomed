import { request } from '@umijs/max';

/** 公众号账号（管理端视图：服务端绝不下发 appSecret/token/aesKey） */
export interface WxAccount {
  id?: number;
  appId?: string;
  accountName?: string;
  /** service 服务号 / subscription 订阅号 */
  accountType?: string;
  /** 1 已认证 / 0 未认证（能力矩阵驱动） */
  verified?: number;
  /** 1 启用 / 0 停用 */
  enabled?: number;
  /** plaintext / 安全模式（第二期 AES） */
  encryptMode?: string;
  remark?: string;
  createTime?: string;
}

/** 自动回复规则（docs/server/api.md：/api/v1/wx/auto-replies） */
export interface WxAutoReply {
  id?: number;
  accountId?: number;
  /** subscribe 欢迎语 / keyword 关键词 / fallback 兜底 */
  ruleType?: 'subscribe' | 'keyword' | 'fallback';
  /** keyword 型必填；其它型服务端强制清空 */
  keyword?: string;
  /** exact 全等 / contains 包含 */
  matchType?: 'exact' | 'contains';
  /** 第一期仅 text */
  replyType?: 'text';
  content?: string;
  /** 匹配优先级，小者先试 */
  priority?: number;
  /** 1 启用 / 0 停用 */
  enabled?: number;
  createTime?: string;
  updateTime?: string;
}

export interface WxPageResult<T> {
  data?: T[];
  total?: number;
  pages?: number;
  success?: boolean;
}

/** 账号列表（规则页选账号用；单店量小不分页） */
export async function queryWxAccounts(options?: { [key: string]: any }) {
  return request<WxAccount[]>('/api/v1/wx/accounts', {
    method: 'GET',
    ...(options || {}),
  });
}

// ---------- 自动回复规则 ----------

export async function queryAutoRepliesByPage(
  params: { current?: number; pageSize?: number; accountId?: number; ruleType?: string; enabled?: number },
  options?: { [key: string]: any },
) {
  return request<WxPageResult<WxAutoReply>>('/api/v1/wx/auto-replies/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function createAutoReply(body: WxAutoReply, options?: { [key: string]: any }) {
  return request<WxAutoReply>('/api/v1/wx/auto-replies', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateAutoReply(body: WxAutoReply, options?: { [key: string]: any }) {
  return request<WxAutoReply>('/api/v1/wx/auto-replies', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteAutoReply(id: number, options?: { [key: string]: any }) {
  return request<Record<string, any>>(`/api/v1/wx/auto-replies/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}
