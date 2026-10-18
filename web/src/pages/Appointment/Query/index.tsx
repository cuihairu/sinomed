import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { message, Popconfirm } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { history } from '@umijs/max';
import moment from 'moment';
import {
  deleteAppointment,
  queryAppointmentPage,
  updateAppointmentStatus,
  APPOINTMENT_STATUS,
  type Appointment,
} from '@/services/ant-design-pro/appointment';
import './index.less';

/** 状态文案：0 待到店 / 1 已接待 / 8 过号 / 9 已取消（接待后从接诊页建单；过号/取消为终态） */
const STATUS_TEXT: Record<number, string> = {
  [APPOINTMENT_STATUS.BOOKED]: '待到店',
  [APPOINTMENT_STATUS.RECEIVED]: '已接待',
  [APPOINTMENT_STATUS.NO_SHOW]: '过号',
  [APPOINTMENT_STATUS.CANCELLED]: '已取消',
};
const STATUS_CLASS: Record<number, string> = {
  [APPOINTMENT_STATUS.BOOKED]: 'wait',
  [APPOINTMENT_STATUS.RECEIVED]: 'serving',
  [APPOINTMENT_STATUS.NO_SHOW]: 'pass',
  [APPOINTMENT_STATUS.CANCELLED]: 'cancel',
};
const TABS = [
  { key: undefined, label: '全部' },
  { key: APPOINTMENT_STATUS.BOOKED, label: '待到店' },
  { key: APPOINTMENT_STATUS.RECEIVED, label: '已接待' },
  { key: APPOINTMENT_STATUS.NO_SHOW, label: '过号' },
  { key: APPOINTMENT_STATUS.CANCELLED, label: '已取消' },
];

const maskPhone = (p?: string) =>
  p && p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p || '';

const statusPill = (status?: number) => {
  if (status == null || STATUS_TEXT[status] === undefined) return '-';
  return <span className={`st ${STATUS_CLASS[status]}`}>{STATUS_TEXT[status]}</span>;
};

export default function AppointmentQuery() {
  const actionRef = useRef<ActionType>();
  const statusRef = useRef<number | undefined>(undefined);
  const [activeTab, setActiveTab] = useState<number | undefined>(undefined);
  /** 只看一天还是全部：默认「今日」——预约页是按天用的工作台 */
  const [scope, setScope] = useState<'today' | 'all'>('today');
  const scopeRef = useRef<'today' | 'all'>('today');
  const [counts, setCounts] = useState<Record<string, number>>({});

  // 原型 tab 带计数：按状态各查一次 total（pageSize=1 只取 total，数据全真实）
  const loadCounts = useCallback(async () => {
    const [a, b, n, c] = await Promise.all(
      [
        APPOINTMENT_STATUS.BOOKED,
        APPOINTMENT_STATUS.RECEIVED,
        APPOINTMENT_STATUS.NO_SHOW,
        APPOINTMENT_STATUS.CANCELLED,
      ].map((s) => queryAppointmentPage({ current: 1, pageSize: 1, status: s })),
    );
    setCounts({
      all: (a?.total || 0) + (b?.total || 0) + (n?.total || 0) + (c?.total || 0),
      0: a?.total || 0,
      1: b?.total || 0,
      8: n?.total || 0,
      9: c?.total || 0,
    });
  }, []);

  useEffect(() => {
    loadCounts();
  }, [loadCounts]);

  const switchTab = (key: number | undefined) => {
    statusRef.current = key;
    setActiveTab(key);
    actionRef.current?.reloadAndRest?.();
  };

  const switchScope = (key: 'today' | 'all') => {
    scopeRef.current = key;
    setScope(key);
    actionRef.current?.reloadAndRest?.();
  };

  const flow = async (id: number, status: number, tip: string) => {
    const res = await updateAppointmentStatus({ id, status });
    if (res?.id) {
      message.success(tip);
      loadCounts();
      actionRef.current?.reload();
    }
  };

  const columns: ProColumns<Appointment>[] = [
    { title: 'id', dataIndex: 'id', width: 60, hideInTable: true },
    {
      title: '预约',
      dataIndex: 'id',
      width: 120,
      hideInSearch: true,
      render: (_, entity) => <span className="aid">#{entity.id}</span>,
    },
    {
      title: '顾客',
      dataIndex: 'customerName',
      width: 130,
      render: (_, entity) =>
        entity.customerName ? (
          <div className="who">
            <div className="nm">{entity.customerName}</div>
            <div className="tel">{maskPhone(entity.customerPhone)}</div>
          </div>
        ) : (
          '-'
        ),
    },
    {
      title: '项目',
      dataIndex: 'itemName',
      width: 160,
      render: (_, entity) => entity.itemName || <span className="faint">到店再定</span>,
    },
    {
      title: '接待',
      dataIndex: 'staffName',
      width: 90,
      render: (_, entity) => entity.staffName || '-',
    },
    {
      title: '工位',
      dataIndex: 'roomName',
      width: 100,
      render: (_, entity) => entity.roomName || '-',
    },
    {
      title: '时段',
      dataIndex: 'startTime',
      width: 170,
      hideInSearch: true,
      render: (_, entity) => {
        if (!entity.startTime) return '-';
        const m = moment(entity.startTime);
        return (
          <div className="slot">
            <div className="t">{m.format('MM-DD HH:mm')}</div>
            <div className="d">{entity.duration ? `${entity.duration} 分钟` : '—'}</div>
          </div>
        );
      },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (_, entity) => entity.remark || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (_, entity) => statusPill(entity.status),
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 200,
      fixed: 'right',
      render: (_, entity) => {
        if (!entity.id) return [];
        const actions: ReactNode[] = [];
        if (entity.status === APPOINTMENT_STATUS.BOOKED) {
          actions.push(
            <a
              key="receive"
              className="op-go"
              onClick={() => flow(entity.id!, APPOINTMENT_STATUS.RECEIVED, '已接待，可发起接诊')}
            >
              到店接待
            </a>,
            <Popconfirm
              key="noshow"
              title="标记这条预约过号？"
              onConfirm={() => flow(entity.id!, APPOINTMENT_STATUS.NO_SHOW, '已标记过号')}
            >
              <a className="op-stop">过号</a>
            </Popconfirm>,
            <Popconfirm
              key="cancel"
              title="取消这条预约？"
              onConfirm={() => flow(entity.id!, APPOINTMENT_STATUS.CANCELLED, '已取消')}
            >
              <a className="op-stop">取消</a>
            </Popconfirm>,
          );
        }
        if (entity.status === APPOINTMENT_STATUS.RECEIVED && entity.customerId) {
          actions.push(
            <a
              key="treat"
              className="op-go amber"
              onClick={() => history.push(`/treat/create?customerId=${entity.customerId}`)}
            >
              发起接诊
            </a>,
          );
        }
        if (entity.status === APPOINTMENT_STATUS.CANCELLED) {
          actions.push(
            <Popconfirm
              key="delete"
              title="删除这条已取消的预约？"
              onConfirm={async () => {
                await deleteAppointment(entity.id!);
                message.success('删除成功');
                loadCounts();
                actionRef.current?.reload();
              }}
            >
              <a className="op-stop">删除</a>
            </Popconfirm>,
          );
        }
        if (actions.length === 0) actions.push(<span key="none">-</span>);
        return actions;
      },
    },
  ];

  return (
    <PageContainer
      title="预约排班"
      content="前台/馆长建约，顾客到店点「到店接待」，再从接诊页建单；过号/取消为终态。每时段限约 1 位由后端校验（同医师同日同小时）。"
    >
      <div className="ap-toolbar">
        <div className="ap-tabs">
          {TABS.map((t) => (
            <button
              key={String(t.key)}
              type="button"
              className={`tab${activeTab === t.key ? ' on' : ''}`}
              onClick={() => switchTab(t.key)}
            >
              {t.label} <b>{t.key === undefined ? counts.all : counts[String(t.key)]}</b>
            </button>
          ))}
        </div>
        <div className="ap-scope">
          <button
            type="button"
            className={`tab${scope === 'today' ? ' on' : ''}`}
            onClick={() => switchScope('today')}
          >
            今日
          </button>
          <button
            type="button"
            className={`tab${scope === 'all' ? ' on' : ''}`}
            onClick={() => switchScope('all')}
          >
            全部
          </button>
        </div>
      </div>
      <ProTable<Appointment>
        rowKey="id"
        columns={columns}
        actionRef={actionRef}
        search={false}
        options={false}
        rowClassName={(entity) =>
          entity.status === APPOINTMENT_STATUS.CANCELLED ? 'ap-cancel' : ''
        }
        request={async (params) => {
          const res = await queryAppointmentPage({
            current: params.current,
            pageSize: params.pageSize,
            status: statusRef.current,
            date: scopeRef.current === 'today' ? moment().format('YYYY-MM-DD') : undefined,
          });
          return { data: res?.data || [], success: true, total: res?.total || 0 };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条 · 每页 20 条` }}
        dateFormatter="string"
      />
    </PageContainer>
  );
}
