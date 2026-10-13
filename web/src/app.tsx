import { Footer, Question, SelectLang, AvatarDropdown, AvatarName } from '@/components';
import { LinkOutlined } from '@ant-design/icons';
import { SettingDrawer } from '@ant-design/pro-components';
import type { RunTimeLayoutConfig } from '@umijs/max';
import { history, Link } from '@umijs/max';
import defaultSettings from '../config/defaultSettings';
import { errorConfig } from './requestErrorConfig';
import { currentUser as queryCurrentUser } from '@/services/ant-design-pro/api';
import ThemeProvider from '@/components/ThemeProvider';
import {
  antdTokenOverrides,
  readTheme,
  restoreTheme,
  SettingsWithToken,
  siderTokenOverrides,
  themeDef,
} from '@/theme';
import React from 'react';
const isDev = process.env.NODE_ENV === 'development';
const loginPath = '/user/login';

/** 启动回放已存主题：data-theme 属性 + ProLayout colorPrimary/侧栏 token（外观设置页可即时改） */
function themedSettings(): SettingsWithToken {
  restoreTheme();
  const key = readTheme();
  return {
    ...defaultSettings,
    // ProLayout 会把 colorPrimary 转成内部 ConfigProvider token，须与主题同步（否则盖回石墨）
    colorPrimary: themeDef(key).brand,
    token: {
      ...defaultSettings.token,
      sider: {
        ...defaultSettings.token?.sider,
        ...siderTokenOverrides(key),
      },
    },
  } as SettingsWithToken;
}

/**
 * @see  https://umijs.org/zh-CN/plugins/plugin-initial-state
 * */
export async function getInitialState(): Promise<{
  settings?: SettingsWithToken;
  currentUser?: API.CurrentUser;
  loading?: boolean;
  fetchUserInfo?: () => Promise<API.CurrentUser | undefined>;
}> {
  const fetchUserInfo = async () => {
    try {
      return await queryCurrentUser({
        skipErrorHandler: true,
      });
    } catch (error) {
      history.push(loginPath);
    }
    return undefined;
  };
  // 如果不是登录页面，执行
  const { location } = history;
  if (location.pathname !== loginPath) {
    const currentUser = await fetchUserInfo();
    return {
      fetchUserInfo,
      currentUser,
      settings: themedSettings(),
    };
  }
  return {
    fetchUserInfo,
    settings: themedSettings(),
  };
}

/** 运行时主题 · antd 初始 token：umi antd 插件首次建 Provider 前即回放已存主题（免首帧闪石墨） */
export function antd(config: Record<string, any>) {
  return {
    ...config,
    theme: {
      ...config.theme,
      token: { ...config.theme?.token, ...antdTokenOverrides(readTheme()) },
    },
  };
}

/** 运行时主题 · 切换广播：订阅 applyTheme，把 token 深合并进插件自带的 ConfigProvider */
export function rootContainer(container: React.ReactNode) {
  return <ThemeProvider>{container}</ThemeProvider>;
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({ initialState, setInitialState }) => {
  return {
    actionsRender: () => [<Question key="doc" />, <SelectLang key="SelectLang" />],
    avatarProps: {
      src: initialState?.currentUser?.avatar,
      title: <AvatarName />,
      render: (_, avatarChildren) => {
        return <AvatarDropdown>{avatarChildren}</AvatarDropdown>;
      },
    },
    footerRender: () => <Footer />,
    onPageChange: () => {
      const { location } = history;
      // 如果没有登录，重定向到 login
      if (!initialState?.currentUser && location.pathname !== loginPath) {
        history.push(loginPath);
      }
    },
    links: isDev
      ? [
          <Link key="openapi" to="/umi/plugin/openapi" target="_blank">
            <LinkOutlined />
            <span>OpenAPI 文档</span>
          </Link>,
        ]
      : [],
    menuHeaderRender: undefined,
    // 自定义 403 页面
    // unAccessible: <div>unAccessible</div>,
    // 增加一个 loading 的状态
    childrenRender: (children) => {
      // if (initialState?.loading) return <PageLoading />;
      return (
        <>
          {children}
          {isDev && (
            <SettingDrawer
              disableUrlParams
              enableDarkTheme
              settings={initialState?.settings}
              onSettingChange={(settings) => {
                setInitialState((preInitialState) => ({
                  ...preInitialState,
                  settings,
                }));
              }}
            />
          )}
        </>
      );
    },
    ...initialState?.settings,
  };
};

/**
 * @name request 配置，可以配置错误处理
 * 它基于 axios 和 ahooks 的 useRequest 提供了一套统一的网络请求和错误处理方案。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const request = {
  ...errorConfig,
};
