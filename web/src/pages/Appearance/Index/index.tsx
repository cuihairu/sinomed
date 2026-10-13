import { PageContainer } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import React from 'react';
import {
  AMBER,
  applyTheme,
  SettingsWithToken,
  siderTokenOverrides,
  THEMES,
  themeDef,
  readTheme,
} from '@/theme';
// themeDef 用于 select() 时同步 settings.colorPrimary（ProLayout 内部 ConfigProvider 用它）
import './index.less';

/**
 * 外观设置 —— 对齐 docs/design/mockups/admin-appearance.html。
 * 四套主题色即时切换：data-theme 换全部 var(--brand) 系样式；antd 主色经 ThemeProvider
 * 调 useAntdConfigSetter 注入插件 ConfigProvider，ProLayout 域内另随 settings.colorPrimary/
 * token.sider 下发（其内部 ConfigProvider 更深，须同步）。
 * 纯前端（localStorage 持久化，启动时 getInitialState 回放），无后端接口。
 */
const AppearancePage: React.FC = () => {
  const { setInitialState } = useModel('@@initialState');
  const current = readTheme();

  const select = (key: (typeof THEMES)[number]['key']) => {
    if (key === current) return;
    applyTheme(key);
    // bump initialState.settings：colorPrimary + 侧栏 token 让 ProLayout（含其内部 ConfigProvider）即时生效
    setInitialState((pre) => ({
      ...pre,
      settings: {
        ...(pre?.settings as SettingsWithToken),
        colorPrimary: themeDef(key).brand,
        token: {
          ...(pre?.settings as SettingsWithToken)?.token,
          sider: siderTokenOverrides(key),
        },
      },
    }));
  };

  return (
    <PageContainer
      title="外观设置"
      className="ap-page"
      content={
        <span className="ap-sub">
          主题色四套可选，点击即时切换；金棕点缀与底色全主题一致，取自品牌 logo。
        </span>
      }
    >
      <section className="ap-panel">
        <h3>
          主题色
          <span className="hint">
            当前：{themeDef(current).label}（{themeDef(current).desc.split(' · ')[0]}）
          </span>
        </h3>
        <div className="ap-themes">
          {THEMES.map((t) => (
            <div
              key={t.key}
              className={`theme${current === t.key ? ' on' : ''}`}
              onClick={() => select(t.key)}
            >
              <div className="swatch" style={{ background: t.swatchBg }}>
                <span className="bar" style={{ background: t.brand }} />
                <span className="dot" style={{ background: AMBER }} />
              </div>
              <div className="tn">{t.label}</div>
              <div className="td">{t.desc}</div>
              {current === t.key ? <div className="ck">✓</div> : null}
            </div>
          ))}
        </div>
      </section>

      <section className="ap-panel">
        <h3>
          实时预览<span className="hint">顾客档案页随主题即时变化</span>
        </h3>
        <div className="pv">
          <div className="pv-side">
            <div className="pv-logo">
              <div className="pv-mark">养</div>
              <div className="pv-name">Youngs 医馆</div>
            </div>
            <div className="pv-nav">
              <i />
              工作台
            </div>
            <div className="pv-nav on">
              <i />
              顾客
            </div>
            <div className="pv-nav">
              <i />
              接诊
            </div>
            <div className="pv-nav">
              <i />
              卡项
            </div>
            <div className="pv-nav">
              <i />
              订单
            </div>
          </div>
          <div className="pv-body">
            <div className="pv-card">
              <div className="pv-head">
                <div className="pv-av">王</div>
                <div className="pv-who">
                  <div className="nm">王女士</div>
                  <div className="mt">137****3333 · 最近到店 3 天前</div>
                </div>
                <span className="pv-tag">金卡会员</span>
              </div>
            </div>
            <div className="pv-stats">
              <div className="pv-stat">
                <div className="k">累计消费</div>
                <div className="v">¥4,860</div>
              </div>
              <div className="pv-stat">
                <div className="k">诊疗记录</div>
                <div className="v">
                  8 <span className="u">次</span>
                </div>
              </div>
              <div className="pv-stat">
                <div className="k">下次回访</div>
                <div className="v gd">10-12</div>
              </div>
            </div>
            <div className="pv-rows">
              <div className="pv-row">
                <span className="d">15</span>
                <span className="t">艾灸调理</span>
                <span className="pill">第 4 疗程</span>
                <span className="s">王中医师 · 40 分钟</span>
              </div>
              <div className="pv-row">
                <span className="d">02</span>
                <span className="t">腹部推拿</span>
                <span className="pill">复诊</span>
                <span className="s">沈中医师 · 30 分钟</span>
              </div>
            </div>
            <div className="pv-actions">
              <span className="pv-btn primary">发起接诊</span>
              <span className="pv-btn">打印档案</span>
            </div>
            <div className="pv-note">
              预览即真实渲染：按钮、选中态、徽标、数值强调全部走主题变量。
            </div>
          </div>
        </div>
      </section>
    </PageContainer>
  );
};

export default AppearancePage;
