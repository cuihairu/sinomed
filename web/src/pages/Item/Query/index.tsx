import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, message, Popconfirm, Switch } from 'antd';
import { useRef } from 'react';
import { queryItemByPage, deleteItem, updateItem } from '@/services/ant-design-pro/item';
import { history } from '@umijs/max';
import './index.less';

type Item = {
  id?: number;
  name?: string;
  price?: number;
  description?: string;
  /** 1 上架、0 下架（顾客 Kiosk 只展示上架项） */
  enabled?: number;
  cover?: string;
  sort?: number;
  /** 服务时长（分钟），卡项详情与预约下单展示用 */
  durationMinutes?: number;
  /** 0 医馆服务（缺省）/ 1 皮肤管理 / 2 身体护理 / 3 中医美容 */
  category?: number;
  createTime?: string;
  updateTime?: string;
};

/** 首字封面渐变轮换（g1 石墨 / g2 金棕 / g3 黛蓝 / g4 藤紫，同原型四色循环） */
const COVER_GRADS = ['g1', 'g2', 'g3', 'g4'];

/** 类目名（美容业态 a）；0 医馆服务为存量缺省，列表不重复标注 */
const CATEGORY_LABELS: Record<number, string> = {
  1: '皮肤管理',
  2: '身体护理',
  3: '中医美容',
};

export default () => {
  const actionRef = useRef<ActionType>();

  const toggleEnabled = async (record: Item, next: boolean) => {
    const res = await updateItem({ ...record, enabled: next ? 1 : 0 });
    if (res?.id) {
      message.success(next ? '已上架，顾客可在自助机看到' : '已下架，自助机不再展示');
      actionRef.current?.reload();
    }
  };

  const columns: ProColumns<Item>[] = [
    {
      title: '卡项',
      dataIndex: 'name',
      width: '42%',
      render: (_, record, index) => (
        <div className="it">
          {record.cover ? (
            <img className="cv img" src={record.cover} alt="" />
          ) : (
            <div className={`cv ${COVER_GRADS[index! % 4]}`}>
              {(record.name || '卡').slice(0, 1)}
            </div>
          )}
          <div>
            <div className="nm">{record.name || '-'}</div>
            <div className="ds">
              {[
                CATEGORY_LABELS[record.category ?? 0] || '',
                record.description || '',
                record.durationMinutes ? `${record.durationMinutes} 分钟` : '',
              ]
                .filter(Boolean)
                .join(' · ') || '—'}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: '价格',
      dataIndex: 'price',
      width: 110,
      render: (_, record) =>
        record.price == null ? '-' : <span className="price">¥{record.price}</span>,
    },
    {
      title: '自助机上架',
      dataIndex: 'enabled',
      width: 150,
      render: (_, record) => (
        <span className={`tg${record.enabled === 0 ? ' off' : ''}`}>
          <Switch
            size="small"
            checked={record.enabled !== 0}
            onChange={(checked) => toggleEnabled(record, checked)}
          />
          <span>{record.enabled === 0 ? '已下架' : '上架中'}</span>
        </span>
      ),
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 80,
      render: (_, record) => <span className="sort">{record.sort ?? 0}</span>,
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 130,
      render: (_, record, __, action) => [
        <a
          key="update"
          onClick={() => {
            if (record.id) {
              history.push(`/item/create?itemId=${record.id}`);
            } else {
              message.error('记录id不存,该数据可不可以编辑');
            }
          }}
        >
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title="删除这张卡项？"
          onConfirm={async () => {
            if (record.id) {
              deleteItem(record.id).then((res) => {
                if (res.id) {
                  message.success('删除成功');
                  action?.reload();
                } else {
                  message.warning('没有找到该卡项');
                }
              });
            } else {
              message.warning('记录id不存,无法删除,请刷新再试');
            }
          }}
        >
          <a className="del">删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer
      title="卡项管理"
      content={
        <span className="it-tip">
          <b>上架中</b>的卡项会出现在门店自助机浏览页，顾客可自行勾选下单；
          <b>排序</b>小者在前，封面为空时自助机以名称首字生成封面。
        </span>
      }
    >
      <ProTable<Item>
        columns={columns}
        actionRef={actionRef}
        search={false}
        options={false}
        rowKey="id"
        request={async (params = {}) => {
          const msg = await queryItemByPage({
            current: params.current,
            pageSize: params.pageSize,
          });
          return { data: msg.data, success: true, total: msg.total };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条 · 每页 20 条` }}
        dateFormatter="string"
        toolBarRender={() => [
          <Button key="create" type="primary" onClick={() => history.push('/item/create')}>
            新建卡项
          </Button>,
        ]}
      />
    </PageContainer>
  );
};
