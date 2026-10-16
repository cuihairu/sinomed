import { useState } from 'react'
import { View, Image, Text, Input } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { BRAND } from '../../services/brand'
import { cancelAppointment, fetchMyAppointments, requestSmsCode, type MyAppointmentsResult } from '../../services/api'
import { loadBookingSession } from '../../services/session'
import { THEMES, applyTheme, readTheme, type ThemeKey } from '../../services/theme'
import './index.css'

/**
 * 我的 tab：免登录口径下的顾客身份与快捷入口。
 * 身份只含本机自助约期留下的称呼/预约（不提供顾客档案查询接口）；
 * 白标自检从顾客首页挪到此处（工程验收用，不再暴露在顾客首页）。
 */
export default function Me() {
  const [session, setSession] = useState(loadBookingSession)
  const [theme, setTheme] = useState<ThemeKey>(readTheme)
  // 跨设备查预约（手机号+验证码，同卡项页的双因子口径）
  const [qPhone, setQPhone] = useState('')
  const [qCode, setQCode] = useState('')
  const [sending, setSending] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [qError, setQError] = useState('')
  const [qResult, setQResult] = useState<MyAppointmentsResult | null>(null)
  // 自助取消预约：正在取消的预约 id（null=空闲）；成功提示与错误就地展示
  const [cancelling, setCancelling] = useState<number | null>(null)
  const [notice, setNotice] = useState('')

  // 切回本页时重读本机预约（首页/预约页都可能更新它）
  useDidShow(() => setSession(loadBookingSession()))

  const getCode = () => {
    setQError('')
    if (!/^1\d{10}$/.test(qPhone)) {
      setQError('手机号格式不对：应为 1 开头的 11 位数字')
      return
    }
    setSending(true)
    requestSmsCode(qPhone)
      .then(() => setQError(''))
      .catch((e) => setQError(e.message))
      .finally(() => setSending(false))
  }

  const query = () => {
    setQError('')
    if (!/^1\d{10}$/.test(qPhone)) {
      setQError('手机号格式不对：应为 1 开头的 11 位数字')
      return
    }
    if (!qCode.trim()) {
      setQError('请先获取并填写短信验证码')
      return
    }
    setSubmitting(true)
    fetchMyAppointments(qPhone, qCode.trim())
      .then((r) => setQResult(r))
      .catch((e) => setQError(e.message))
      .finally(() => setSubmitting(false))
  }

  const cancel = (appointmentId: number) => {
    setQError('')
    setNotice('')
    if (!/^1\d{10}$/.test(qPhone)) {
      setQError('手机号格式不对：应为 1 开头的 11 位数字')
      return
    }
    if (!qCode.trim()) {
      setQError('请先获取并填写短信验证码（取消需重新取码，验证码一次性）')
      return
    }
    setCancelling(appointmentId)
    cancelAppointment({ phone: qPhone, smsCode: qCode.trim(), appointmentId })
      .then((r) => {
        setNotice(r.message)
        setQResult((prev) =>
          prev ? { appointments: prev.appointments.filter((a) => a.appointmentId !== appointmentId) } : prev
        )
      })
      .catch((e) => setQError(e.message))
      .finally(() => setCancelling(null))
  }

  const pickTheme = (key: ThemeKey) => {
    applyTheme(key)
    setTheme(key)
  }

  const initial = session?.customerName ? session.customerName.slice(0, 1) : '客'

  return (
    <View className="me-page">
      <View className="brand-head">
        <View className="seal">
          {BRAND.iconData ? <Image className="seal-img" src={BRAND.iconData} mode="aspectFit" /> : '养'}
        </View>
        <View className="bt">
          <View className="bn">{session?.customerName || '尊敬的顾客'}</View>
          <View className="bg">到店报手机号即可接待，无需注册</View>
        </View>
        <View className="bell">{initial}</View>
      </View>

      <View className="sec">
        <Text className="sec-h3">本机预约</Text>
      </View>
      <View className="me-card">
        <View className="me-row">
          <Text className="me-k">称呼</Text>
          <Text className="me-v">{session?.customerName || '未留称呼'}</Text>
        </View>
        <View className="me-row">
          <Text className="me-k">最近预约</Text>
          <Text className="me-v">
            {session ? `${session.startTime.slice(0, 16)} · ${session.itemName || '到店再定'}` : '暂无'}
          </Text>
        </View>
        <View className="me-row">
          <Text className="me-k">说明</Text>
          <Text className="me-v">
            仅记住本机提交的预约；换设备可用手机号+验证码查待到店预约（下方「查我的预约」），卡项余次见「卡项」页。
          </Text>
        </View>
      </View>

      {/* 跨设备查预约：手机号+验证码双因子（与卡项页同一套一次性验证码通道），off 模式服务端拒绝 */}
      <View className="sec">
        <Text className="sec-h3">查我的预约</Text>
      </View>
      <View className="mq-card">
        <Input
          className="mq-input"
          type="number"
          maxlength={11}
          placeholder="手机号"
          value={qPhone}
          onInput={(e) => setQPhone(e.detail.value)}
        />
        <View className="mq-row">
          <Input
            className="mq-input mq-code"
            type="number"
            maxlength={6}
            placeholder="验证码"
            value={qCode}
            onInput={(e) => setQCode(e.detail.value)}
          />
          <View className={`mq-get ${sending ? 'off' : ''}`} onClick={() => { if (!sending) getCode() }}>
            {sending ? '发送中…' : '获取验证码'}
          </View>
        </View>
        <View className={`mq-submit ${submitting ? 'off' : ''}`} onClick={() => { if (!submitting) query() }}>
          {submitting ? '查询中…' : '查询预约'}
        </View>
        {qError ? <View className="mq-err">{qError}</View> : null}
        {notice ? <View className="mq-ok">{notice}</View> : null}
        {qResult ? (
          qResult.appointments.length === 0 ? (
            <View className="mq-empty">该手机号当前没有待到店预约</View>
          ) : (
            qResult.appointments.map((a) => (
              <View className="mq-appt" key={a.appointmentId}>
                <View className="mq-line">
                  <View className="mq-time">{a.startTime.slice(0, 16)}</View>
                  <View
                    className={`mq-cancel${cancelling === a.appointmentId ? ' off' : ''}`}
                    onClick={() => { if (cancelling === null) cancel(a.appointmentId) }}
                  >
                    {cancelling === a.appointmentId ? '取消中…' : '取消'}
                  </View>
                </View>
                <View className="mq-item">{a.itemName || '项目到店再定'}</View>
              </View>
            ))
          )
        ) : null}
        <View className="mq-note">手机号+验证码验证后展示待到店预约；无预约如实显示，不造数据；每次查询与取消留痕；取消需重新获取验证码（验证码一次性），只能取消待到店且未过期的预约</View>
      </View>

      <View className="sec">
        <Text className="sec-h3">快捷</Text>
      </View>
      <View className="me-actions">
        <View className="me-act" onClick={() => Taro.switchTab({ url: '/pages/booking/index' })}>
          去预约
        </View>
        <View className="me-act" onClick={() => Taro.switchTab({ url: '/pages/index/index' })}>
          回首页
        </View>
      </View>

      {/* 四主题切换：仅 H5 端（data-theme 挂 documentElement 整页换色）；
          小程序端 WXSS 无运行时变量能力，固定石墨，不放假开关 */}
      {process.env.TARO_ENV === 'h5' ? (
        <>
          <View className="sec">
            <Text className="sec-h3">主题</Text>
          </View>
          <View className="me-card theme-row">
            {THEMES.map((t) => (
              <View
                key={t.key}
                className={`theme-opt${theme === t.key ? ' on' : ''}`}
                onClick={() => pickTheme(t.key)}
              >
                <View className="theme-dot" style={{ background: t.color }} />
                <Text className="theme-label">{t.label}</Text>
              </View>
            ))}
          </View>
        </>
      ) : null}

      {/* 白标自检（工程验收用） */}
      <View className="sec">
        <Text className="sec-h3">白标自检</Text>
      </View>
      <View className="brand-card">
        <View className="brand-row">
          <View className="brand-label">主题色</View>
          <View className="brand-value">
            <View className="brand-swatch" style={{ background: BRAND.themeColor }} />
            <View className="brand-mono">{BRAND.themeColor}</View>
          </View>
        </View>
        <View className="brand-row">
          <View className="brand-label">后端域名</View>
          <View className="brand-value">
            <View className="brand-mono">{BRAND.apiBase}</View>
          </View>
        </View>
        <View className="brand-row">
          <View className="brand-label">页内图标</View>
          <View className="brand-value">
            <View className="brand-mono">{BRAND.iconData ? '已注入（商家 icon）' : '未注入位图'}</View>
          </View>
        </View>
      </View>
      <View className="brand-tip">
        以上参数由构建脚本注入，改参数不改代码；换商家出包见仓库 docs/app-white-label.md。
      </View>
    </View>
  )
}
