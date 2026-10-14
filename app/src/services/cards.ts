import Taro from '@tarojs/taro'
import { fetchOpenidSession } from './api'

/**
 * 本机最近一次手机查卡摘要：首页「我的卡项」入口回显用。
 * 只存称呼 + 持卡数 + 查询时间，不存卡项明细与余次——
 * 验证过的明文结果落本机缓存，共享设备上的下一位顾客就能读到，
 * 与「裸手机号查卡不做」同一红线；明细与余次留在验证后的「卡项」页。
 * 有效期 7 天，超期按未查过处理，不显示过期数据。
 */
export interface CardSession {
  customerName: string
  cardCount: number
  /** 查询时间，ISO 串（YYYY-MM-DD 可读） */
  queriedAt: string
}

const KEY = 'sinomed_card_session'
const TTL_DAYS = 7

export function loadCardSession(): CardSession | null {
  try {
    const raw = Taro.getStorageSync(KEY) as CardSession | ''
    if (!raw || typeof raw.queriedAt !== 'string') return null
    const ageDays = (Date.now() - new Date(raw.queriedAt).getTime()) / 86400000
    return ageDays <= TTL_DAYS ? raw : null
  } catch {
    return null
  }
}

export function saveCardSession(session: CardSession) {
  try {
    Taro.setStorageSync(KEY, session)
  } catch {
    // 存储不可用（隐私模式等）不影响主流程
  }
}

/** openid 查卡路径的身份：仅存内存（不落存储——白标换 appid 后旧 openid 即失效，避免脏缓存） */
let cachedOpenid: string | null = null

/**
 * openid 查卡路径的身份：wx.login → 服务端 jscode2session 换 openid。
 * H5 无 wx.login、后台凭据未配置、模式不允许或换取失败时返回 null，调用方回退短信路径。
 */
export async function currentOpenid(): Promise<string | null> {
  if (cachedOpenid) return cachedOpenid
  if (process.env.TARO_ENV !== 'weapp') return null
  try {
    const { code } = await Taro.login()
    const { openid } = await fetchOpenidSession(code)
    cachedOpenid = openid
    return openid
  } catch {
    return null
  }
}
