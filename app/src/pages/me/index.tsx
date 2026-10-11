import React, { useState } from 'react'
import { View, Image, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { BRAND } from '../../services/brand'
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

  // 切回本页时重读本机预约（首页/预约页都可能更新它）
  useDidShow(() => setSession(loadBookingSession()))

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
            仅记住本机提交的预约；卡项余次请到店或致电门店查询，见「卡项」页。
          </Text>
        </View>
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
