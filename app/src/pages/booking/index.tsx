import { useMemo, useState } from 'react'
import { View, Text, Input } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { fetchHomeItems, bookAppointment } from '../../services/api'
import { saveBookingSession } from '../../services/session'
import type { HomeItem } from '../../services/api'
import './index.css'

/** 自助约期口径见 docs/design/app-booking.md：整点 09:00–17:00、时长固定 60、频控 1 条/日/手机号 */
const BOOKABLE_HOURS = Array.from({ length: 9 }, (_, i) => 9 + i)
const WEEK = ['日', '一', '二', '三', '四', '五', '六']

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export default function Booking() {
  // 日期条：今天起 7 天
  const days = useMemo(
    () =>
      Array.from({ length: 7 }, (_, i) => {
        const d = new Date()
        d.setDate(d.getDate() + i)
        return {
          date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
          label: i === 0 ? '今天' : i === 1 ? '明天' : `周${WEEK[d.getDay()]}`,
          sub: `${d.getMonth() + 1}/${d.getDate()}`
        }
      }),
    []
  )
  const [dayIdx, setDayIdx] = useState(0)
  const [hour, setHour] = useState<number | null>(null)
  const [items, setItems] = useState<HomeItem[] | null>(null)
  const [itemsFailed, setItemsFailed] = useState(false)
  const [itemIdx, setItemIdx] = useState<number | null>(null) // null = 到店再定
  const [phone, setPhone] = useState('')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<Awaited<ReturnType<typeof bookAppointment>> | null>(null)

  // 今日已过时段禁选（时段须晚于当前时刻，与服务端校验一致）
  const nowHour = new Date().getHours()
  const isHourDisabled = (h: number) => dayIdx === 0 && h <= nowHour

  const loadItems = () => {
    setItemsFailed(false)
    fetchHomeItems()
      .then((next) => setItems(next))
      .catch(() => setItemsFailed(true))
  }
  useMemo(loadItems, [])

  const pickItem = (i: number | null) => {
    setItemIdx(i)
  }

  const submit = () => {
    setError('')
    if (!/^1\d{10}$/.test(phone)) {
      setError('手机号格式不对：应为 1 开头的 11 位数字')
      return
    }
    if (hour === null) {
      setError('请先选一个时段')
      return
    }
    setSubmitting(true)
    bookAppointment({
      phone,
      name: name.trim() || undefined,
      itemId: itemIdx === null || !items ? undefined : items[itemIdx].id,
      startTime: `${days[dayIdx].date} ${pad(hour!)}:00`
    })
      .then((r) => {
        // 本机记住这次自助约期：首页横幅提醒与「我的」页身份都用它
        saveBookingSession({
          appointmentId: String(r.appointmentId),
          startTime: r.startTime,
          itemName: r.itemName || '',
          customerName: r.customerName || '',
          savedAt: new Date().toISOString()
        })
        setResult(r)
      })
      .catch((e) => setError(e.message || '提交失败，请稍后再试'))
      .finally(() => setSubmitting(false))
  }

  if (result) {
    return (
      <View className="bk-page">
        <View className="bk-done">
          <View className="bk-done-ic">✓</View>
          <View className="bk-done-tt">预约成功</View>
          <View className="bk-done-card">
            <View className="bk-row">
              <Text className="bk-k">预约号</Text>
              <Text className="bk-v bk-strong">{result.appointmentId}</Text>
            </View>
            <View className="bk-row">
              <Text className="bk-k">时段</Text>
              <Text className="bk-v bk-strong">{result.startTime}</Text>
            </View>
            <View className="bk-row">
              <Text className="bk-k">项目</Text>
              <Text className="bk-v">{result.itemName || '到店再定'}</Text>
            </View>
            <View className="bk-row">
              <Text className="bk-k">称呼</Text>
              <Text className="bk-v">{result.customerName}</Text>
            </View>
          </View>
          <View className="bk-done-tip">到店后报手机号，前台为您接待；如需改期请致电门店。</View>
          <View className="bk-submit" onClick={() => Taro.switchTab({ url: '/pages/index/index' })}>
            返回首页
          </View>
        </View>
      </View>
    )
  }

  return (
    <View className="bk-page">
      {/* 日期条：今天起 7 天 */}
      <View className="bk-sec">
        <Text className="bk-h3">选日期</Text>
      </View>
      <View className="bk-days">
        {days.map((d, i) => (
          <View
            key={d.date}
            className={`bk-day ${i === dayIdx ? 'on' : ''}`}
            onClick={() => {
              setDayIdx(i)
              setHour(null)
            }}
          >
            <Text className="bk-day-l">{d.label}</Text>
            <Text className="bk-day-d">{d.sub}</Text>
          </View>
        ))}
      </View>

      {/* 时段网格：9:00–17:00 整点全部展示为可约（服务端不做占用查询，冲突到店人工调和） */}
      <View className="bk-sec">
        <Text className="bk-h3">选时段</Text>
        <Text className="bk-sec-note">整点 · 60 分钟</Text>
      </View>
      <View className="bk-hours">
        {BOOKABLE_HOURS.map((h) => {
          const disabled = isHourDisabled(h)
          return (
            <View
              key={h}
              className={`bk-hour ${hour === h ? 'on' : ''} ${disabled ? 'off' : ''}`}
              onClick={() => {
                if (!disabled) setHour(h)
              }}
            >
              <Text>{pad(h)}:00</Text>
            </View>
          )
        })}
      </View>

      {/* 项目：实拉上架项目，可跳过到店再定 */}
      <View className="bk-sec">
        <Text className="bk-h3">选项目</Text>
        <Text className="bk-sec-note">可到店再定</Text>
      </View>
      {itemsFailed ? (
        <View className="bk-note">
          项目加载失败
          <Text className="bk-retry" onClick={loadItems}>重试</Text>
        </View>
      ) : items === null ? (
        <View className="bk-note">项目加载中…</View>
      ) : (
        <View className="bk-items">
          <View className={`bk-item ${itemIdx === null ? 'on' : ''}`} onClick={() => pickItem(null)}>
            到店再定
          </View>
          {items.map((it, i) => (
            <View
              key={it.id}
              className={`bk-item ${itemIdx === i ? 'on' : ''}`}
              onClick={() => pickItem(i)}
            >
              {it.name} <Text className="bk-item-p">¥{it.price}</Text>
            </View>
          ))}
        </View>
      )}

      {/* 联系方式：手机号即身份（免登录口径，同自助机） */}
      <View className="bk-sec">
        <Text className="bk-h3">联系方式</Text>
      </View>
      <View className="bk-form">
        <Input
          className="bk-input"
          type="number"
          maxlength={11}
          placeholder="手机号（到店凭此接待）"
          value={phone}
          onInput={(e) => setPhone(e.detail.value)}
        />
        <Input
          className="bk-input"
          type="text"
          maxlength={10}
          placeholder="称呼（可不填）"
          value={name}
          onInput={(e) => setName(e.detail.value)}
        />
      </View>

      {error ? <View className="bk-err">{error}</View> : null}

      <View
        className={`bk-submit ${submitting ? 'off' : ''}`}
        onClick={() => {
          if (!submitting) submit()
        }}
      >
        {submitting ? '提交中…' : '确认预约'}
      </View>
      <View className="bk-foot">提交即预约到店时段；支付与取消请联系门店。</View>
    </View>
  )
}
