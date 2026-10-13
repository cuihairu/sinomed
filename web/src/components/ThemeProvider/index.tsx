import { useAntdConfigSetter } from '@umijs/max';
import React, { useEffect } from 'react';
import { antdTokenOverrides, readTheme, subscribeTheme } from '@/theme';

/**
 * 运行时主题 Provider：把 antd 主色 token 注入 umi antd 插件自带的 ConfigProvider。
 * 不能用内嵌 ConfigProvider —— 插件的 Provider 包在应用外层（rootContainer 后应用），
 * 嵌套 token 会被其静态配置（石墨）覆盖；useAntdConfigSetter 深合并改的正是它本身，
 * 主色/圆点色随主题换、borderRadius 等静态 token 保留。订阅 applyTheme 广播即时生效。
 */
const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const setAntdConfig = useAntdConfigSetter();
  // 插件每次渲染都重建 setter 函数，不能进依赖数组（否则 setState→新 setter→effect 重跑 死循环），
  // 用 ref 持有最新引用，effect 只挂载跑一次
  const setterRef = React.useRef(setAntdConfig);
  setterRef.current = setAntdConfig;
  useEffect(() => {
    const apply = () => setterRef.current({ theme: { token: antdTokenOverrides(readTheme()) } });
    apply();
    const stop = subscribeTheme(apply);
    return () => {
      stop();
    };
  }, []);
  return <>{children}</>;
};

export default ThemeProvider;
