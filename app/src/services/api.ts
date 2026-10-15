import Taro from '@tarojs/taro'
import { BRAND } from './brand'

/** 上架卡项（与服务端 GET /api/v1/kiosk/items 同构，顾客侧展示同一份上架数据） */
export interface HomeItem {
  id: number
  name: string
  price: number
  cover: string | null
  description: string | null
}

function kioskUrl(path: string): string {
  // H5 开发态走 devServer 代理（config/dev.ts），其余端直连白标 apiBase
  if (process.env.NODE_ENV === 'development' && process.env.TARO_ENV === 'h5') {
    return `/api/v1/kiosk/${path}`
  }
  return `${BRAND.apiBase}/api/v1/kiosk/${path}`
}

/** 拉上架项目；失败抛错（页面就地提示，不造假数据） */
export async function fetchHomeItems(): Promise<HomeItem[]> {
  const res = await Taro.request({
    url: kioskUrl('items'),
    method: 'GET',
    header: { Accept: 'application/json' }
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw new Error(`加载失败（${res.statusCode}）`)
  }
  return res.data as HomeItem[]
}

/** 自助约期提交体（与服务端 POST /api/v1/kiosk/appointments 同构） */
export interface BookingBody {
  phone: string
  name?: string
  itemId?: number
  startTime: string
}

/** 自助约期回执 */
export interface BookingResult {
  customerId: number
  customerName: string
  appointmentId: number
  startTime: string
  itemName?: string | null
  status: number
}

/**
 * 提交自助约期（免登录）；服务端 400 的 errorMessage 原样透出（如频控文案）。
 */
export async function bookAppointment(body: BookingBody): Promise<BookingResult> {
  const res = await Taro.request({
    url: kioskUrl('appointments'),
    method: 'POST',
    header: { 'Content-Type': 'application/json', Accept: 'application/json' },
    data: body
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw kioskError(res.data, res.statusCode, '提交失败')
  }
  return res.data as BookingResult
}

/** 手机查卡：一条持卡行（与 KioskCardLookupResult.CardLine 同构） */
export interface CardLine {
  cardId: number
  itemId: number
  itemName: string | null
  totalTimes: number
  remainingTimes: number
  status: number
  validUntil: string | null
}

/** 手机查卡回执：命中顾客才带持卡列表 */
export interface CardLookupResult {
  customerId: number
  customerName: string
  cards: CardLine[]
}

/** 手机查卡请求：sms_verified 模式 phone+smsCode，openid_only 模式 openid（登录态） */
export interface CardLookupBody {
  phone?: string
  openid?: string
  smsCode?: string
}

/** 服务端 4xx 的 errorMessage 原样透出（如「在线查卡已关闭」「验证码不对」） */
function kioskError(data: unknown, statusCode: number, fallback: string): Error {
  const msg =
    data && typeof data === 'object' && 'errorMessage' in data && typeof (data as any).errorMessage === 'string'
      ? (data as any).errorMessage
      : `${fallback}（${statusCode}）`
  return new Error(msg)
}

/**
 * 获取短信验证码（POST /api/v1/kiosk/sms-code）：sms_verified 模式查卡、
 * openid_only 模式绑定微信共用；off 模式服务端 400 拒绝。
 */
export async function requestSmsCode(phone: string): Promise<void> {
  const res = await Taro.request({
    url: kioskUrl('sms-code'),
    method: 'POST',
    header: { 'Content-Type': 'application/json', Accept: 'application/json' },
    data: { phone }
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw kioskError(res.data, res.statusCode, '获取验证码失败')
  }
}

/**
 * 手机查卡（POST /api/v1/kiosk/card-lookup）：身份绑定后才返回持卡——
 * off 模式全拒（裸手机号查卡不做，隐私红线），openid_only 需 openid，sms_verified 需手机号+验证码。
 */
export async function lookupCards(body: CardLookupBody): Promise<CardLookupResult> {
  const res = await Taro.request({
    url: kioskUrl('card-lookup'),
    method: 'POST',
    header: { 'Content-Type': 'application/json', Accept: 'application/json' },
    data: body
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw kioskError(res.data, res.statusCode, '查询失败')
  }
  return res.data as CardLookupResult
}

/**
 * 小程序登录换 openid（POST /api/v1/kiosk/openid-session）：openid_only 模式专用。
 * 凭据未配置、模式不允许或换取失败时服务端 400 透出，调用方静默降级短信路径。
 */
export async function fetchOpenidSession(code: string): Promise<{ openid: string }> {
  const res = await Taro.request({
    url: kioskUrl('openid-session'),
    method: 'POST',
    header: { 'Content-Type': 'application/json', Accept: 'application/json' },
    data: { code }
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw kioskError(res.data, res.statusCode, '微信登录失败')
  }
  return res.data as { openid: string }
}

/**
 * openid 自助绑定（POST /api/v1/kiosk/openid-bind）：openid_only 模式专用——
 * openid + 手机号 + 一次性短信验证码自证身份后，把登录 openid 绑到该手机号的顾客档案。
 * 同档案重复绑定幂等成功；openid 已绑其他档案服务端 400 拒绝（不静默改绑）。
 */
export async function bindOpenid(openid: string, phone: string, smsCode: string): Promise<{ message: string }> {
  const res = await Taro.request({
    url: kioskUrl('openid-bind'),
    method: 'POST',
    header: { 'Content-Type': 'application/json', Accept: 'application/json' },
    data: { openid, phone, smsCode }
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw kioskError(res.data, res.statusCode, '绑定失败')
  }
  return res.data as { message: string }
}

/** 手机自助查预约：一条待到店预约（与服务端 KioskMyAppointmentsResult.AppointmentLine 同构） */
export interface MyAppointmentLine {
  appointmentId: number
  startTime: string
  itemName: string | null
  status: number
}

export interface MyAppointmentsResult {
  appointments: MyAppointmentLine[]
}

/**
 * 手机自助查预约（POST /api/v1/kiosk/my-appointments）：跨设备查自己的待到店预约，
 * 手机号+验证码双因子（同查卡 sms_verified 通道，验证码一次性）；off/其他模式服务端 400 拒绝。
 */
export async function fetchMyAppointments(phone: string, smsCode: string): Promise<MyAppointmentsResult> {
  const res = await Taro.request({
    url: kioskUrl('my-appointments'),
    method: 'POST',
    header: { 'Content-Type': 'application/json', Accept: 'application/json' },
    data: { phone, smsCode }
  })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw kioskError(res.data, res.statusCode, '查询失败')
  }
  return res.data as MyAppointmentsResult
}
