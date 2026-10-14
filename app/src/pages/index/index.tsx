import { useEffect, useState } from 'react'
import { View, Image, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { BRAND } from '../../services/brand'
import { fetchHomeItems } from '../../services/api'
import { loadBookingSession } from '../../services/session'
import type { HomeItem } from '../../services/api'
import './index.css'

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 按真实时段问候；称呼来自本机自助约期留的称呼（无接口查询顾客档案，不带称呼不造假） */
function greeting(name: string | undefined): string {
  const h = new Date().getHours()
  let part = '夜深了'
  if (h < 5) part = '夜深了'
  else if (h < 11) part = '早上好'
  else if (h < 14) part = '中午好'
  else if (h < 18) part = '下午好'
  else part = '晚上好'
  return name ? `${part}，${name}` : `${part}，欢迎光临`
}

/** 规划功能标记：无接口的区块如实标注，不造假数据（口径同管理端 plan-note） */
function PlanPill() {
  return <Text className="plan-pill">规划功能</Text>
}

export default function Index() {
  const [items, setItems] = useState<HomeItem[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [showAll, setShowAll] = useState(false)
  const [session, setSession] = useState(loadBookingSession)

  // 从预约 tab 切换回来时重读本机预约，保持横幅提醒态新鲜
  useDidShow(() => setSession(loadBookingSession()))

  const load = () => {
    setFailed(false)
    fetchHomeItems()
      .then((next) => setItems(next))
      .catch(() => setFailed(true))
  }

  useEffect(load, [])

  const today = todayStr()
  const upcoming = session && session.startTime.slice(0, 10) >= today
  const apptTitle = (() => {
    if (!session) return '在线预约'
    const date = session.startTime.slice(0, 10)
    const time = session.startTime.slice(11, 16)
    const label = date === today ? '今天' : `${Number(date.slice(5, 7))}月${Number(date.slice(8, 10))}日`
    return `${label} ${time} · ${session.itemName || '项目到店再定'}`
  })()
  const shownItems = items ? (showAll ? items : items.slice(0, 4)) : []

  return (
    <View className="home-page">
      {/* 品牌头：商家图标优先，无位图以金棕「养」印兜底；右侧圆钮进「我的」 */}
      <View className="brand-head">
        <View className="seal">
          {BRAND.iconData ? <Image className="seal-img" src={BRAND.iconData} mode="aspectFit" /> : '养'}
        </View>
        <View className="bt">
          <View className="bn">{BRAND.name}</View>
          <View className="bg">{greeting(session?.customerName || undefined)}</View>
        </View>
        <View className="bell" onClick={() => Taro.switchTab({ url: '/pages/me/index' })}>
          {session?.customerName ? session.customerName.slice(0, 1) : '客'}
        </View>
      </View>

      {/* 预约横幅：有本机未过期待到店预约则显提醒，否则为通用自助约期入口 */}
      <View className="appt" onClick={() => Taro.switchTab({ url: '/pages/booking/index' })}>
        <View className="ic">{upcoming && session && session.itemName ? session.itemName.slice(0, 1) : '预'}</View>
        <View className="appt-bd">
          <View className="tt">{upcoming ? apptTitle : '在线预约'}</View>
          <View className="ss">
            {upcoming && session
              ? `已预约 ${session.appointmentId}，到店报手机号即可`
              : '选个时段，到店免等待'}
          </View>
        </View>
        <View className="go">预约</View>
      </View>

      {/* 今日宜养：实拉上架项目（/api/v1/kiosk/items，与自助机同一份真实数据） */}
      <View className="sec">
        <Text className="sec-h3">今日宜养</Text>
        {items && items.length > 4 ? (
          <Text className="sec-link" onClick={() => setShowAll((v) => !v)}>
            {showAll ? '收起' : '全部项目'}
          </Text>
        ) : null}
      </View>
      {failed ? (
        <View className="plan-note">
          项目加载失败
          <Text className="retry" onClick={load}>重试</Text>
        </View>
      ) : items === null ? (
        <View className="plan-note">项目加载中…</View>
      ) : items.length === 0 ? (
        <View className="plan-note">暂无上架项目，请到店咨询</View>
      ) : (
        <View className="items">
          {shownItems.map((item, i) => (
            <View className="item" key={item.id}>
              <View className={`cover ${i % 2 === 0 ? 'g1' : 'g2'}`}>
                {item.cover ? (
                  <Image className="cover-img" src={item.cover} mode="aspectFill" />
                ) : (
                  <Text className="cover-zi">{item.name.slice(0, 1)}</Text>
                )}
              </View>
              <View className="bd">
                <View className="nm">{item.name}</View>
                {item.description ? <View className="ds">{item.description}</View> : null}
                <View className="pr">
                  ¥{item.price} <Text className="pr-u">/ 次</Text>
                </View>
              </View>
            </View>
          ))}
        </View>
      )}

      {/* 我的卡项：顾客购卡/余次无接口，如实规划占位（详见「卡项」页） */}
      <View className="sec">
        <Text className="sec-h3">我的卡项</Text>
        <PlanPill />
      </View>
      <View className="plan-note">
        购卡与余次查询为规划功能，上线后将在这里展示您的卡项与剩余次数；现已开卡可到前台或致电门店查询，「卡项」页有说明。
      </View>

      {/* 养生贴士：静态编辑内容 */}
      <View className="sec">
        <Text className="sec-h3">养生贴士</Text>
      </View>
      <View className="tip">
        <Text className="tip-b">秋燥宜润肺</Text>
        <Text className="tip-t">少辛增酸，早卧早起；艾灸后两小时勿碰冷水。</Text>
      </View>
    </View>
  )
}
