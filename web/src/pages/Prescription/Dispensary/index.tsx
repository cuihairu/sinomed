import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { Button, Input, Modal, Radio, message } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import moment from 'moment';
import {
  dispensePrescription,
  queryDispensePage,
  type DispenseRow,
} from '@/services/ant-design-pro/prescription';
import './index.less';

const maskPhone = (p?: string) => (p && p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p || '');

/** 状态过滤：0 待发药 / 1 已发药 / undefined 全部 */
type StatusFilter = 0 | 1 | undefined;

export default function Dispensary() {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<StatusFilter>(0);
  const [keyword, setKeyword] = useState('');
  /** 待发药/已发药计数（radio 标签用，操作后一并刷新） */
  const [counts, setCounts] = useState<{ todo: number; done: number }>({ todo: 0, done: 0 });
  /** 发药确认弹窗 */
  const [dispensing, setDispensing] = useState<DispenseRow | undefined>();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);

  const loadCounts = useCallback(async () => {
    const [todo, done] = await Promise.all([
      queryDispensePage({ current: 1, pageSize: 1, dispenseStatus: 0 }),
      queryDispensePage({ current: 1, pageSize: 1, dispenseStatus: 1 }),
    ]);
    setCounts({ todo: todo?.total || 0, done: done?.total || 0 });
  }, []);

  useEffect(() => {
    loadCounts();
  }, [loadCounts]);

  const openDispense = (row: DispenseRow) => {
    setNote('');
    setDispensing(row);
  };

  /** 发药：待发药→已发药单向，落发药流水；成功后刷新列表与计数 */
  const confirmDispense = async () => {
    if (dispensing?.id == null) return;
    setSaving(true);
    try {
      await dispensePrescription(dispensing.id, note.trim() || undefined);
      message.success(`已发药 #${dispensing.id}，请顾客核对药袋`);
      setDispensing(undefined);
      await loadCounts();
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<DispenseRow>[] = [
    { title: 'id', dataIndex: 'id', width: 60, hideInTable: true },
    {
      title: '处方',
      dataIndex: 'id',
      width: 90,
      render: (_, entity) => <span className="pid">#{entity.id}</span>,
    },
    {
      title: '顾客',
      dataIndex: 'customerName',
      width: 160,
      render: (_, entity) => (
        <div className="who">
          <div className="nm">{entity.customerName || '-'}</div>
          <div className="sub">{maskPhone(entity.customerPhone)}</div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'prescriptionType',
      width: 70,
      render: (_, entity) => (entity.prescriptionType === 1 ? '膏方' : '汤剂'),
    },
    {
      title: '剂数',
      dataIndex: 'doses',
      width: 70,
      render: (_, entity) => (entity.doses ? `${entity.doses} 付` : '-'),
    },
    {
      title: '药味',
      dataIndex: 'itemCount',
      width: 70,
      render: (_, entity) => (entity.itemCount ? `${entity.itemCount} 味` : '-'),
    },
    {
      title: '代煎',
      dataIndex: 'decoctionText',
      width: 90,
      render: (_, entity) =>
        entity.decoctionText === '无需代煎' ? (
          <span className="faint">—</span>
        ) : (
          <span className="dp decoct">{entity.decoctionText}</span>
        ),
    },
    {
      title: '膏方',
      dataIndex: 'pasteText',
      width: 90,
      render: (_, entity) =>
        entity.pasteText === '非膏方' ? (
          <span className="faint">—</span>
        ) : (
          <span className="dp paste">{entity.pasteText}</span>
        ),
    },
    {
      title: '发药',
      dataIndex: 'dispenseStatus',
      width: 150,
      render: (_, entity) =>
        entity.dispenseStatus === 1 ? (
          <span className="dp done">
            已发药{entity.dispenseTime ? ` · ${moment(entity.dispenseTime).format('MM-DD HH:mm')}` : ''}
          </span>
        ) : (
          <span className="dp todo">待发药</span>
        ),
    },
    {
      title: '开方时间',
      dataIndex: 'createTime',
      width: 120,
      render: (_, entity) => (entity.createTime ? moment(entity.createTime).format('MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 100,
      fixed: 'right',
      render: (_, entity) =>
        entity.dispenseStatus === 0 ? [
          <a key="dispense" className="op-go" onClick={() => openDispense(entity)}>
            发药
          </a>,
        ] : [
          <span key="none" className="faint">
            —
          </span>,
        ],
    },
  ];

  return (
    <PageContainer
      title="药房工作台"
      content="待发药→已发药单向确认「药交出去了」，落发药流水；代煎/膏方走「已取」领取时自动发药，无需重复点。"
    >
      <div className="dp-toolbar">
        <Radio.Group
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          optionType="button"
          buttonStyle="solid"
          options={[
            { label: `待发药 ${counts.todo}`, value: 0 },
            { label: `已发药 ${counts.done}`, value: 1 },
            { label: '全部', value: undefined },
          ]}
        />
        <Input.Search
          className="dp-search"
          placeholder="顾客姓名 / 手机号"
          allowClear
          onSearch={(v) => {
            setKeyword(v.trim());
            actionRef.current?.reload();
          }}
        />
      </div>
      <ProTable<DispenseRow>
        rowKey="id"
        columns={columns}
        actionRef={actionRef}
        search={false}
        options={false}
        request={async (params) => {
          const res = await queryDispensePage({
            current: params.current,
            pageSize: params.pageSize,
            dispenseStatus: status,
            customerKeyword: keyword || undefined,
          });
          return { data: res?.data || [], success: true, total: res?.total || 0 };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条 · 每页 20 条` }}
      />

      <Modal
        title={dispensing ? `发药确认 · #${dispensing.id}` : '发药确认'}
        open={dispensing != null}
        onCancel={() => setDispensing(undefined)}
        footer={
          <Button type="primary" loading={saving} onClick={confirmDispense}>
            确认发药
          </Button>
        }
        width={420}
      >
        {dispensing ? (
          <div className="dp-confirm">
            <div className="line">
              {dispensing.customerName || '-'}
              <span className="sub">{maskPhone(dispensing.customerPhone)}</span>
            </div>
            <div className="sub">
              {dispensing.prescriptionType === 1 ? '膏方' : '汤剂'} · {dispensing.doses ?? '-'} 付 ·{' '}
              {dispensing.itemCount ?? 0} 味
              {dispensing.decoctionText && dispensing.decoctionText !== '无需代煎'
                ? ` · 代煎${dispensing.decoctionText}`
                : ''}
            </div>
            <Input.TextArea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="备注（可选，如代煎袋数核对、取件人）"
              maxLength={100}
              showCount
              autoSize={{ minRows: 2, maxRows: 4 }}
            />
            <div className="hint">单向确认，发药后不可撤销；操作人记当前登录账号</div>
          </div>
        ) : null}
      </Modal>
    </PageContainer>
  );
}
