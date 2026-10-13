import { ProLayoutProps } from '@ant-design/pro-components';

/**
 * @name 全局默认设置
 * 品牌口径与 docs/design/mockups/tokens.css 同源：石墨×古金（logo #666666/#C7A674），
 * 宣纸底、宋体标题；token 里的色值与 tokens.css 变量一一对应。
 */
const Settings: ProLayoutProps & {
  pwa?: boolean;
  logo?: string;
} = {
  navTheme: 'light',
  // 石墨（logo 灰加深），金棕点缀 #c7a674
  colorPrimary: '#353a37',
  layout: 'side',
  contentWidth: 'Fluid',
  fixedHeader: false,
  fixSiderbar: true,
  colorWeak: false,
  title: 'Youngs 医馆',
  pwa: true,
  logo: '/logo.svg',
  iconfontUrl: '',
  token: {
    // 与 tokens.css 同源：paper #f7f7f4 / tint #eceeee / ink #262a28 / text #4b4f4d
    header: {
      colorBgHeader: '#f7f7f4',
      heightLayoutHeader: 56,
    },
    sider: {
      colorTextMenu: '#4b4f4d',
      colorBgMenuItemHover: '#f0f1ef',
      colorTextMenuItemHover: '#262a28',
      colorBgMenuItemSelected: '#eceeee',
      colorTextMenuSelected: '#262a28',
      colorTextMenuActive: '#262a28',
    },
    pageContainer: {
      paddingBlockPageContainerContent: 16,
      paddingInlinePageContainerContent: 28,
    },
  },
};

export default Settings;
