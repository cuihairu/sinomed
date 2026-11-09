import { PageContainer } from '@ant-design/pro-components';
import { Tabs } from 'antd';
import React from 'react';
import AccountsTab from './AccountsTab';
import MaterialsTab from './MaterialsTab';
import MenusTab from './MenusTab';
import MessageLogsTab from './MessageLogsTab';
import RulesTab from './RulesTab';

const WxPage: React.FC = () => (
  <PageContainer
    title="公众号管理"
    content={
      <span>
        规则与菜单不再手工改库；素材台账只读；消息流水只读、行内仅提供管理端解绑（线下换绑场景，设计稿
        §4.7）；推送到微信仍依附公众号凭据
      </span>
    }
  >
    <Tabs
      items={[
        { key: 'accounts', label: '公众号账号', children: <AccountsTab /> },
        { key: 'rules', label: '自动回复规则', children: <RulesTab /> },
        { key: 'menus', label: '自定义菜单', children: <MenusTab /> },
        { key: 'logs', label: '消息流水', children: <MessageLogsTab /> },
        { key: 'materials', label: '素材台账', children: <MaterialsTab /> },
      ]}
    />
  </PageContainer>
);

export default WxPage;
