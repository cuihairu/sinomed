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

/** 账号能力矩阵（/api/v1/wx/capabilities/{accountId}；account_type + verified 驱动，设计稿 §6.1/§6.2） */
export interface WxCapabilities {
  accountId?: number;
  accountName?: string;
  accountType?: string;
  verified?: boolean;
  callback?: boolean;
  materials?: boolean;
  webAuth?: boolean;
  /** 未认证订阅号为 false：菜单工作区隐藏 + 提示（§6.2 降级表） */
  menuManagement?: boolean;
  customerService?: boolean;
  massMessaging?: boolean;
  templateMessage?: boolean;
}

export async function queryCapabilities(
  accountId: number,
  options?: { [key: string]: any },
) {
  return request<WxCapabilities>(`/api/v1/wx/capabilities/${accountId}`, {
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

// ---------- 消息流水 / 素材台账（管理端只读） ----------

/** 消息流水行（docs/server/api.md：/api/v1/wx/message-logs；reject 为拒绝审计） */
export interface WxMessageLog {
  id?: number;
  accountId?: number;
  /** in 入站 / out 出站回复 / reject 拒绝审计 */
  direction?: string;
  msgType?: string;
  fromUser?: string;
  toUser?: string;
  content?: string;
  /** 该粉丝已绑定顾客的姓名；未绑定为空 */
  customerName?: string;
  msgId?: number;
  result?: string;
  createTime?: string;
}

/** 素材台账行（/api/v1/wx/materials/page；localPath 为空 = 仅远端记录，无图可看） */
export interface WxMaterial {
  id?: number;
  accountId?: number;
  mediaId?: string;
  materialType?: string;
  name?: string;
  url?: string;
  localPath?: string;
  size?: number;
  mimeType?: string;
  createTime?: string;
}

export async function queryMessageLogs(
  params: {
    current?: number;
    pageSize?: number;
    accountId?: number;
    fromUser?: string;
    direction?: string;
    msgType?: string;
    result?: string;
    startTime?: string;
    endTime?: string;
  },
  options?: { [key: string]: any },
) {
  return request<WxPageResult<WxMessageLog>>('/api/v1/wx/message-logs', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function queryMaterialsByPage(
  params: {
    current?: number;
    pageSize?: number;
    accountId?: number;
    materialType?: string;
    startTime?: string;
    endTime?: string;
  },
  options?: { [key: string]: any },
) {
  return request<WxPageResult<WxMaterial>>('/api/v1/wx/materials/page', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 取素材本地副本：需带 JWT，故走 request 拿 blob（裸 img src 带不上 Authorization） */
export async function fetchMaterialBlob(id: number, options?: { [key: string]: any }): Promise<Blob> {
  return request<Blob>(`/api/v1/wx/materials/${id}`, {
    method: 'GET',
    responseType: 'blob',
    ...(options || {}),
  });
}

// ---------- 自定义菜单（设计稿 §4.3「编辑/草稿」本地半边；推送微信半边压凭据） ----------

/** 自定义菜单行（/api/v1/wx/menus；两级，parentId 表达层级，平铺返回） */
export interface WxMenu {
  id?: number;
  accountId?: number;
  /** 父菜单 id；顶级为空 */
  parentId?: number;
  /** 一级 ≤4 汉字（8 字母等效）、二级 ≤8 汉字（16 字母等效），服务端加权校验 */
  name?: string;
  /** view/click/scancode_push/scancode_waitmsg/pic_sysphoto/pic_photo_or_album/pic_weixin/
   *  location_select/view_miniprogram；一级仅 view/click */
  menuType?: string;
  /** view 型必填（http/https） */
  url?: string;
  /** 素材类菜单的 media_id */
  mediaId?: string;
  /** view_miniprogram 必填 */
  miniprogramAppid?: string;
  miniprogramPagepath?: string;
  /** click 型必填（回调透传） */
  eventKey?: string;
  sort?: number;
  /** 1 启用 / 0 草稿（草稿不推送微信） */
  enabled?: number;
  createTime?: string;
  updateTime?: string;
}

export async function queryMenus(
  params?: { accountId?: number },
  options?: { [key: string]: any },
) {
  return request<WxMenu[]>('/api/v1/wx/menus', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

export async function createMenu(body: WxMenu, options?: { [key: string]: any }) {
  return request<WxMenu>('/api/v1/wx/menus', {
    method: 'POST',
    data: body,
    ...(options || {}),
  });
}

export async function updateMenu(body: WxMenu, options?: { [key: string]: any }) {
  return request<WxMenu>('/api/v1/wx/menus', {
    method: 'PUT',
    data: body,
    ...(options || {}),
  });
}

export async function deleteMenu(id: number, options?: { [key: string]: any }) {
  return request<Record<string, any>>(`/api/v1/wx/menus/${id}`, {
    method: 'DELETE',
    ...(options || {}),
  });
}

// ---------- 粉丝绑定（管理端） ----------

/**
 * 管理端解绑粉丝（设计稿 §4.7「换人关注属线下场景走管理端」；纯本地库操作不触微信）：
 * 清 wx_fans.customer_id（保留粉丝行）并落审计 unbind；无粉丝行/未绑定服务端 400
 * （no_fan/not_bound），errorMessage 由调用方 message.error 透出。
 */
export async function unbindFan(
  accountId: number,
  openid: string,
  options?: { [key: string]: any },
) {
  return request<{ message: string }>(`/api/v1/wx/fans/${accountId}/unbind`, {
    method: 'POST',
    data: { openid },
    ...(options || {}),
  });
}

/**
 * 推送启用菜单到微信（设计稿 §4.3「推送」半边；真实出网压凭据）：
 * 只推 enabled=1（草稿不推送），成功回推送一级菜单数；缺 AppSecret/无启用菜单/微信 errcode
 * 非 0 服务端 400 并透传 errcode，由调用方 message.error 透出。
 */
export async function pushMenus(accountId: number, options?: { [key: string]: any }) {
  return request<{ message: string }>(`/api/v1/wx/menus/push/${accountId}`, {
    method: 'POST',
    ...(options || {}),
  });
}
