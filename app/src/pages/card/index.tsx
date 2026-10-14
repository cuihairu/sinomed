import { useEffect, useState } from 'react'
import { View, Text, Input } from '@tarojs/components'
import { lookupCards, requestSmsCode, type CardLookupBody, type CardLookupResult } from '../../services/api'
import { currentOpenid, saveCardSession } from '../../services/cards'
import './index.css'

/**
 * 我的卡项：手机查卡（免登录，身份绑定后才行）。
 * 服务端按 sinomed.phone-lookup.mode 走：off 全拒（裸手机号查卡不做，隐私红线）、
 * openid_only 需 openid（登录态）、sms_verified 需手机号+验证码（双因子）。
 * openid 路径：weapp 登录换取（凭据未配置/失败时静默回退短信路径）；H5 只走短信路径。
 * 身份带齐就带上（openid+手机号+验证码），服务端按 mode 取用——客户端不知道后台开关。
 * 每次尝试（含拒绝）服务端落审计。口径见 docs/design/kiosk.md 与 2026-10-14 裁定单。
 */
export default function Card() {
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [sending, setSending] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<CardLookupResult | null>(null)
  const [openid, setOpenid] = useState<string | null>(null)

  useEffect(() => {
    currentOpenid().then((o) => {
      if (o) setOpenid(o)
    })
  }, [])

  const getCode = () => {
    setError('')
    if (!/^1\d{10}$/.test(phone)) {
      setError('手机号格式不对：应为 1 开头的 11 位数字')
      return
    }
    setSending(true)
    requestSmsCode(phone)
      .then(() => setError(''))
      .catch((e) => setError(e.message))
      .finally(() => setSending(false))
  }

  const submit = () => {
    setError('')
    const phoneOk = /^1\d{10}$/.test(phone)
    const codeOk = !!code.trim()
    if (!openid && !phoneOk) {
      setError('手机号格式不对：应为 1 开头的 11 位数字')
      return
    }
    if (!openid && !codeOk) {
      setError('请先获取并填写短信验证码')
      return
    }
    setSubmitting(true)
    setResult(null)
    const body: CardLookupBody = {}
    if (openid) body.openid = openid
    if (phoneOk) body.phone = phone
    if (codeOk) body.smsCode = code.trim()
    lookupCards(body)
      .then((r) => {
        setResult(r)
        // 只落称呼+持卡数+查询时间供首页入口回显，明细与余次不落本机（见 services/cards.ts）
        if (r.customerName) {
          saveCardSession({
            customerName: r.customerName,
            cardCount: r.cards.length,
            queriedAt: new Date().toISOString(),
          })
        }
      })
      .catch((e) => setError(e.message))
      .finally(() => setSubmitting(false))
  }

  return (
    <View className="card-page">
      <View className="sec">
        <Text className="sec-h3">我的卡项</Text>
      </View>

      {openid ? (
        <View className="lk-openid">已识别微信登录身份，可直接查询；未绑定顾客档案时请到前台登记绑定</View>
      ) : null}

      <View className="lk-form">
        <Input
          className="lk-input"
          type="number"
          maxlength={11}
          placeholder="手机号"
          value={phone}
          onInput={(e) => setPhone(e.detail.value)}
        />
        <View className="lk-row">
          <Input
            className="lk-input lk-code"
            type="number"
            maxlength={6}
            placeholder="验证码"
            value={code}
            onInput={(e) => setCode(e.detail.value)}
          />
          <View className={`lk-get ${sending ? 'off' : ''}`} onClick={() => { if (!sending) getCode() }}>
            {sending ? '发送中…' : '获取验证码'}
          </View>
        </View>
      </View>

      {error ? <View className="lk-err">{error}</View> : null}

      <View
        className={`lk-submit ${submitting ? 'off' : ''}`}
        onClick={() => { if (!submitting) submit() }}
      >
        {submitting ? '查询中…' : '查询卡项'}
      </View>

      {result ? (
        <View className="lk-result">
          <View className="lk-who">{result.customerName} 的持卡（{result.cards.length}）</View>
          {result.cards.length === 0 ? (
            <View className="lk-empty">暂无持卡记录</View>
          ) : (
            result.cards.map((c) => (
              <View className="lk-card" key={c.cardId}>
                <View className="lk-card-name">{c.itemName ?? '卡项已删除'}</View>
                <View className="lk-card-meta">
                  剩余 {c.remainingTimes}/{c.totalTimes} 次
                  {c.status === 0 ? '（已停用）' : ''}
                </View>
                {c.validUntil ? (
                  <View className="lk-card-meta">有效期至 {c.validUntil.slice(0, 10)}</View>
                ) : null}
              </View>
            ))
          )}
        </View>
      ) : null}

      <View className="sec">
        <Text className="sec-h3">说明</Text>
      </View>
      <View className="how-card">
        <View className="how-row">
          <Text className="how-k">查卡</Text>
          <Text className="how-v">微信登录 openid 或手机号+短信验证码双因子，验证后展示您的持卡与剩余次数；每次查询都留痕可查</Text>
        </View>
        <View className="how-row">
          <Text className="how-k">核销</Text>
          <Text className="how-v">卡项由门店开具、到店核销；预约到店可走「预约」页</Text>
        </View>
      </View>
    </View>
  )
}
