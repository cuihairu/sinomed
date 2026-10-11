import Taro from '@tarojs/taro'

/**
 * 本机最近一次自助约期（免登录口径）：
 * 只记住「本机提交成功」的预约，用于首页预约提醒与「我的」页称呼；
 * 不含卡项余次等自助查询——那属规划功能（界面如实标注，不造假数据）。
 */
export interface BookingSession {
  /** 服务端返回的预约号 */
  appointmentId: string
  /** 「YYYY-MM-DD HH:mm」 */
  startTime: string
  /** 项目名；空串 = 到店再定 */
  itemName: string
  /** 顾客称呼；空串 = 未留称呼 */
  customerName: string
  /** 提交时刻（本地 ISO），过约期后首页不再提醒 */
  savedAt: string
}

const KEY = 'sinomed_booking_session'

export function loadBookingSession(): BookingSession | null {
  try {
    const raw = Taro.getStorageSync(KEY) as BookingSession | ''
    return raw ? raw : null
  } catch {
    return null
  }
}

export function saveBookingSession(session: BookingSession) {
  try {
    Taro.setStorageSync(KEY, session)
  } catch {
    // 存储不可用（隐私模式等）不影响主流程，首页退回通用入口
  }
}
