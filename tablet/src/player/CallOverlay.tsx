import { useEffect } from 'react'
import type { QueueCallItem } from '../types'
import { playChime } from './chime'
import { speakCall } from './speech'
import './call.css'

/**
 * 叫号条（对齐原型 tablet-screen .call）：底部悬浮横条，轮播继续可见，
 * 停留 holdMs（默认 15 秒）后回调父级换下一条/回轮播。
 * 语音播报与提示音不变；横条不拦截触摸（pointer-events:none，全屏手势仍可用）。
 */
export function CallOverlay({
  call,
  holdMs,
  onDone,
}: {
  call: QueueCallItem
  holdMs: number
  onDone: () => void
}) {
  useEffect(() => {
    playChime()
    speakCall(call)
    const timer = window.setTimeout(onDone, holdMs)
    return () => window.clearTimeout(timer)
  }, [call.id])

  return (
    <div className="call-bar">
      <span className="call-label">请就诊</span>
      <span className="call-who">
        {call.number} 号{call.patientMasked ? ` ${call.patientMasked}` : ''}
      </span>
      <span className="call-room">
        到<b>{call.room}</b>
      </span>
    </div>
  )
}
