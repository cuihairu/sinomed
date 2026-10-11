import { useEffect, useRef, useState } from 'react'
import { useItems } from './net/api'
import { ItemGrid } from './menu/ItemGrid'
import { Checkout } from './order/Checkout'
import { Success } from './order/Success'
import type { KioskOrderResult } from './types'
import './app.css'

/** 首页（含下单页）无操作超时：清篮回首页，见 kiosk.md 交互链路 30s */
const IDLE_MS = 30_000
/** 成功回执停留时长（倒计时后自动回首页） */
const SUCCESS_MS = 10_000

type View = 'browse' | 'checkout' | 'success'

/** 顶栏实时时钟：30s 粒度足够（分钟位显示），开机常显 */
function useClock(): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const tick = window.setInterval(() => setNow(new Date()), 30_000)
    return () => window.clearInterval(tick)
  }, [])
  return now
}

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

/** 首页顶栏：金棕印章 + 宋体问候 + 实时时钟（对齐原型 kiosk-menu hero） */
function Hero() {
  const now = useClock()
  const hhmm = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  return (
    <header className="kiosk-hero">
      <span className="kiosk-seal">养</span>
      <div className="kiosk-hero-text">
        <h1>欢迎光临 Youngs 中医养生</h1>
        <p>点选想做的项目，提交后前台马上为您安排</p>
      </div>
      <div className="kiosk-clock">
        <div className="kiosk-clock-tm">{hhmm}</div>
        <div className="kiosk-clock-dt">
          {now.getMonth() + 1} 月 {now.getDate()} 日 星期{WEEKDAYS[now.getDay()]}
        </div>
      </div>
    </header>
  )
}

/** 三步流程胶囊：如实反映自助机链路（选服务 → 留手机号 → 前台接待） */
function Steps() {
  return (
    <div className="kiosk-steps">
      <span className="kiosk-step kiosk-step-on">① 选服务</span>
      <span className="kiosk-step">② 留手机号</span>
      <span className="kiosk-step">③ 前台接待</span>
    </div>
  )
}

export default function App() {
  const { items, retry } = useItems()
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [view, setView] = useState<View>('browse')
  const [receipt, setReceipt] = useState<KioskOrderResult | null>(null)
  const [countdown, setCountdown] = useState(Math.floor(SUCCESS_MS / 1000))

  // 空闲计时器：任何指针/键盘操作都续期；到点整机复位（清篮 + 回首页）
  const idleTimer = useRef<number | null>(null)
  const resetAll = () => {
    setSelected(new Set())
    setReceipt(null)
    setView('browse')
  }
  const bumpIdle = () => {
    if (idleTimer.current !== null) window.clearTimeout(idleTimer.current)
    idleTimer.current = window.setTimeout(resetAll, IDLE_MS)
  }
  useEffect(() => {
    bumpIdle()
    const bump = () => bumpIdle()
    window.addEventListener('pointerdown', bump)
    window.addEventListener('keydown', bump)
    return () => {
      if (idleTimer.current !== null) window.clearTimeout(idleTimer.current)
      window.removeEventListener('pointerdown', bump)
      window.removeEventListener('keydown', bump)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 成功页倒计时 → 自动回首页
  useEffect(() => {
    if (view !== 'success') return
    setCountdown(Math.floor(SUCCESS_MS / 1000))
    const tick = window.setInterval(() => setCountdown((s) => s - 1), 1000)
    const done = window.setTimeout(resetAll, SUCCESS_MS)
    return () => {
      window.clearInterval(tick)
      window.clearTimeout(done)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view])

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  if (items === null) {
    return (
      <div className="kiosk-center">
        <div className="kiosk-hint">菜单加载失败，请检查网络</div>
        <button type="button" className="kiosk-retry" onClick={retry}>
          重试
        </button>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="kiosk-center">
        <div className="kiosk-hint">暂无上架服务，请咨询前台</div>
      </div>
    )
  }

  if (view === 'checkout') {
    return (
      <Checkout
        items={items}
        selected={selected}
        onBack={() => setView('browse')}
        onSuccess={(next) => {
          setReceipt(next)
          setView('success')
        }}
      />
    )
  }

  if (view === 'success' && receipt !== null) {
    return <Success receipt={receipt} countdown={countdown} onDone={resetAll} />
  }

  const pickedCount = selected.size
  const pickedItems = items.filter((item) => selected.has(item.id))
  const pickedTotal = pickedItems.reduce((sum, item) => sum + (item.price || 0), 0)

  return (
    <div className="kiosk-page">
      <Hero />
      <Steps />
      <ItemGrid items={items} selected={selected} onToggle={toggle} />
      {/* 常驻底栏（原型 kiosk-menu .basket）：空选态给指引，选中后显示清单/合计/提交 */}
      <div className="basket-bar">
        {pickedCount > 0 ? (
          <>
            <div className="basket-head">
              <b>已选 {pickedCount} 项</b>
              <span className="basket-names">
                {pickedItems.map((item) => `${item.name} ×1`).join('、')}
              </span>
            </div>
            <div className="basket-row">
              <span className="basket-amount">
                <small>¥</small>
                {pickedTotal}
              </span>
              <button
                type="button"
                className="basket-go"
                onClick={() => setView('checkout')}
              >
                提交到前台
              </button>
            </div>
          </>
        ) : (
          <div className="basket-empty">点选上方想做的项目，选好后在这里提交到前台</div>
        )}
      </div>
    </div>
  )
}
