import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Popconfirm, Tag, message } from 'antd';
import React, { useRef } from 'react';
import moment from 'moment';
import { queryMessageLogs, unbindFan, type WxMessageLog } from '@/services/ant-design-pro/wx';
import {
  DIRECTION_ENUM,
  DIRECTION_LABELS,
  MSG_TYPE_ENUM,
  MSG_TYPE_LABELS,
  RESULT_ENUM,
  RESULT_LABELS,
  timeRangeColumn,
} from './constants';
import useAccountOptions from './useAccountOptions';

const MessageLogsTab: React.FC = () => {
  const { options, nameOf } = useAccountOptions();
  const actionRef = useRef<ActionType>();

  /**
   * 管理端解绑（设计稿 §4.7「换人关注属线下场景走管理端」；纯本地库操作不触微信）：
   * 行内取粉丝 openid——in 行 fromUser、out 行 toUser（服务端记日志时交换，reject 行可能双空不显示）；
   * 成功后刷新，顾客列随之回到「未绑定」；no_fan/not_bound 的 400 原样透出。
   */
  const unbindFanRow = async (accountId: number, openid: string) => {
    try {
      const res = await unbindFan(accountId, openid);
      message.success(res?.message || '已解除粉丝与顾客档案的绑定');
      actionRef.current?.reload();
    } catch (e: any) {
      message.error(
        e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '解绑失败',
      );
    }
  };

  const columns: ProColumns<WxMessageLog>[] = [
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 150,
      hideInSearch: true,
      render: (_, entity) =>
        entity.createTime ? moment(entity.createTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '账号',
      dataIndex: 'accountId',
      width: 130,
      valueType: 'select',
      fieldProps: { options, showSearch: true },
      render: (_, entity) => nameOf(entity.accountId),
    },
    {
      title: '方向',
      dataIndex: 'direction',
      width: 90,
      valueType: 'select',
      valueEnum: DIRECTION_ENUM,
      render: (_, entity) => (
        <Tag>{DIRECTION_LABELS[entity.direction || ''] || entity.direction}</Tag>
      ),
    },
    {
      title: '类型',
      dataIndex: 'msgType',
      width: 90,
      valueType: 'select',
      valueEnum: MSG_TYPE_ENUM,
      render: (_, entity) => MSG_TYPE_LABELS[entity.msgType || ''] || entity.msgType || '-',
    },
    { title: '粉丝', dataIndex: 'fromUser', ellipsis: true },
    {
      title: '顾客',
      dataIndex: 'customerName',
      width: 110,
      hideInSearch: true,
      render: (_, entity) => entity.customerName || <span style={{ color: '#aaa' }}>未绑定</span>,
    },
    { title: '内容', dataIndex: 'content', ellipsis: true, hideInSearch: true },
    {
      title: '结果',
      dataIndex: 'result',
      width: 130,
      valueType: 'select',
      valueEnum: RESULT_ENUM,
      render: (_, entity) => (
        <Tag
          color={
            entity.result === 'ok' ? 'green' : entity.direction === 'reject' ? 'red' : 'default'
          }
        >
          {RESULT_LABELS[entity.result || ''] || entity.result}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      hideInSearch: true,
      render: (_, entity) => {
        const openid = entity.direction === 'out' ? entity.toUser : entity.fromUser;
        if (!openid || entity.accountId == null) {
          return <span style={{ color: '#aaa' }}>—</span>;
        }
        return (
          <Popconfirm
            title="解绑该粉丝的顾客档案？"
            description="线下换绑场景（设计稿 §4.7）：清绑定保留粉丝行并落审计，换绑需重新走 H5 绑定流程"
            okText="解绑"
            cancelText="取消"
            onConfirm={() => unbindFanRow(entity.accountId!, openid)}
          >
            <a style={{ color: '#cf1322' }}>解绑</a>
          </Popconfirm>
        );
      },
    },
    timeRangeColumn('createTime'),
  ];

  return (
    <ProTable<WxMessageLog>
      rowKey="id"
      columns={columns}
      actionRef={actionRef}
      headerTitle="消息流水（含拒绝审计，最新在前）"
      dateFormatter="string"
      search={{ labelWidth: 'auto' }}
      request={async (params) => {
        // 时间范围列经 search.transform 已折成 startTime/endTime，其余键与服务端参数同名直传
        const res = await queryMessageLogs(params as any);
        return { data: res?.data || [], success: true, total: res?.total || 0 };
      }}
      pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
      locale={{ emptyText: '暂无消息流水（公众号模块未启用或尚未收到消息）' }}
    />
  );
};

export default MessageLogsTab;
