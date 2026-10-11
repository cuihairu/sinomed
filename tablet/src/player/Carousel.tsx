import { useEffect, useRef, useState } from 'react'
import type { PlaylistItem } from '../types'
import './carousel.css'

/**
 * 轮播引擎：按顺序播 items，图片按 durationMs 停留，视频播完即走
 * （durationMs 作为视频兜底计时，防止个别编码卡住 onended）。
 * 序列变化时从第一张重新开始（mediaVersion 作 key 触发重挂载）。
 */
export function Carousel({ items, mediaVersion }: { items: PlaylistItem[]; mediaVersion: string }) {
  const [index, setIndex] = useState(0)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    setIndex(0)
  }, [mediaVersion])

  useEffect(() => {
    if (items.length === 0) return
    const current = items[index % items.length]
    const fallback = window.setTimeout(
      () => setIndex((i) => (i + 1) % items.length),
      Math.max(current.durationMs || 8000, 1500)
    )
    timerRef.current = fallback
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    }
  }, [items, index])

  if (items.length === 0) {
    return (
      <div className="player-empty">
        <div className="player-empty-title">等待内容</div>
        <div className="player-empty-sub">素材与排期由管理端配置，配置后约 30 秒内自动开始轮播</div>
      </div>
    )
  }

  const current = items[index % items.length]
  // 排版层：素材可带 眉题/大标题/文案/价格（原型 tablet-screen.html 整版设计的适配版——
  // 媒体文件铺满舞台，文案以左下渐变底叠加；全空即纯素材播放）
  const hasCopy = Boolean(current.kicker || current.title || current.copy || current.price)
  return (
    <div className="player-stage">
      {current.type === 2 ? (
        <video
          key={`${mediaVersion}-${current.materialId}-${index}`}
          className="player-media"
          src={current.url}
          autoPlay
          muted
          playsInline
          onEnded={() => setIndex((i) => (i + 1) % items.length)}
          onError={() => setIndex((i) => (i + 1) % items.length)}
        />
      ) : (
        <img
          key={`${mediaVersion}-${current.materialId}-${index}`}
          className="player-media"
          src={current.url}
          alt={current.name}
          onError={() => setIndex((i) => (i + 1) % items.length)}
        />
      )}
      {hasCopy ? (
        <div className="player-copy">
          {current.kicker ? <span className="pc-kicker">{current.kicker}</span> : null}
          {current.title ? <h1 className="pc-title">{current.title}</h1> : null}
          {current.copy ? <p className="pc-sub">{current.copy}</p> : null}
          {current.price ? <div className="pc-price">{current.price}</div> : null}
        </div>
      ) : null}
      <div className="player-pager">
        {items.map((_, i) => (
          <i key={i} className={i === index % items.length ? 'on' : ''} />
        ))}
      </div>
    </div>
  )
}
