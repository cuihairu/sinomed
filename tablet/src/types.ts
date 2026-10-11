/** 服务端 GET /api/v1/ads/playlist 返回结构（见 docs/design/tablet.md） */
export interface PlaylistItem {
  materialId: number
  name: string
  /** 叠加层眉题（可空；四项文案全空即纯素材播放） */
  kicker?: string | null
  /** 叠加层大标题（可空） */
  title?: string | null
  /** 叠加层文案副题（可空） */
  copy?: string | null
  /** 价格行，自由文本（可空） */
  price?: string | null
  /** 1 图片、2 视频 */
  type: 1 | 2
  url: string
  /** 图片停留时长（毫秒）；视频兜底计时 */
  durationMs: number
  sort: number
}

export interface Playlist {
  /** 内容戳：素材/排期最近变更时间，未变则不重拉媒体 */
  version: string
  screenId: number
  code: string
  lastSeenAt: string | null
  items: PlaylistItem[]
}

/** 服务端 GET /api/v1/calls/latest 单条叫号（见 CallController） */
export interface QueueCallItem {
  id: number
  /** 定向屏 id；null = 全部屏广播 */
  screenId: number | null
  number: string
  room: string
  patientMasked: string | null
  status: number
  calledAt: string | null
}

export interface CallsLatest {
  /** 最新游标：平板下次拉取带上；无新记录时等于传入值 */
  since: number
  calls: QueueCallItem[]
}
