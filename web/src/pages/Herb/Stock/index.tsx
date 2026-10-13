import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Table,
  Tag,
} from 'antd';
import dayjs from 'dayjs';
import { useEffect, useRef, useState } from 'react';
import {
  createStockLog,
  deleteStockLog,
  queryHerbByPage,
  queryStockBalance,
  queryStockPage,
  type Herb,
  type HerbStockBalance,
  type HerbStockLog,
} from '@/services/ant-design-pro/herb';
import './index.less';

const TYPE_TEXT: Record<number, string> = { 0: '出库', 1: '入库' };

/** 饮片出入库：余额（克）+ 效期预警看板 + 出入库登记与流水；库存口径为台账，不涉采购单据与结算 */
export default () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [herbs, setHerbs] = useState<Herb[]>([]);
  const [warnDays, setWarnDays] = useState(30);
  const [balance, setBalance] = useState<HerbStockBalance[]>([]);
  const [balanceLoading, setBalanceLoading] = useState(false);

  const loadBalance = async (days?: number) => {
    setBalanceLoading(true);
    try {
      const res = await queryStockBalance({ expiryWithinDays: days ?? warnDays });
      setBalance(res || []);
    } finally {
      setBalanceLoading(false);
    }
  };

  const loadHerbs = async () => {
    const res = await queryHerbByPage({ current: 1, pageSize: 200 });
    setHerbs(res?.data || []);
  };

  useEffect(() => {
    loadBalance();
    loadHerbs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({ type: 1 });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createStockLog({
        herbId: values.herbId,
        type: values.type,
        quantity: values.quantity,
        expiry: values.expiry ? values.expiry.format('YYYY-MM-DD') : undefined,
        supplier: values.supplier || undefined,
        note: values.note || undefined,
      });
      message.success('已登记');
      setModalOpen(false);
      actionRef.current?.reload();
      loadBalance();
    } finally {
      setSaving(false);
    }
  };

  const typeText = (type?: number) => TYPE_TEXT[type ?? 1] ?? '入库';

  const columns: ProColumns<HerbStockLog>[] = [
    {
      title: '药材',
      dataIndex: 'herbName',
      key: 'herbName',
      width: '22%',
      render: (_, record) => <span className="hb-nm">{record.herbName || '-'}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: '10%',
      render: (_, record) => (
        <Tag color={record.type === 1 ? 'green' : 'orange'}>{typeText(record.type)}</Tag>
      ),
    },
    {
      title: '数量（克）',
      dataIndex: 'quantity',
      key: 'quantity',
      width: '12%',
      align: 'right',
    },
    {
      title: '批次效期',
      dataIndex: 'expiry',
      key: 'expiry',
      width: '16%',
    },
    {
      title: '供货方',
      dataIndex: 'supplier',
      key: 'supplier',
      width: '18%',
    },
    {
      title: '备注',
      dataIndex: 'note',
      key: 'note',
      width: '16%',
      ellipsis: true,
    },
    {
      title: '登记时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: '16%',
    },
  ];

  return (
    <PageContainer>
      <div className="hs-tip">
        当前库存 = 入库合计 -
        出库合计（克）；出库按效期先进先出（FEFO）消耗批次。库存为台账口径，不涉采购单据与结算。
      </div>
      <div className="hs-toolbar">
        <span className="hs-count">效期预警阈值</span>
        <Select
          value={warnDays}
          onChange={(v) => {
            setWarnDays(v);
            loadBalance(v);
          }}
          style={{ width: 120 }}
          options={[
            { value: 30, label: '30 天' },
            { value: 60, label: '60 天' },
            { value: 90, label: '90 天' },
          ]}
        />
        <Button type="primary" onClick={openCreate}>
          出入库登记
        </Button>
      </div>
      <Table<HerbStockBalance>
        className="hs-board"
        rowKey={(row) => String(row.herbId)}
        loading={balanceLoading}
        size="small"
        pagination={false}
        dataSource={balance}
        title={() => '库存余额（仅列已有流水的药材）'}
        columns={[
          { title: '药材', dataIndex: 'name', key: 'name', width: '30%' },
          {
            title: '当前库存（克）',
            dataIndex: 'stock',
            key: 'stock',
            width: '22%',
            align: 'right' as const,
            render: (v: number) => <span className="hs-time">{v}</span>,
          },
          { title: '最早未消耗批次效期', dataIndex: 'nextExpiry', key: 'nextExpiry', width: '26%' },
          {
            title: '距效期（天）',
            dataIndex: 'expiryInDays',
            key: 'expiryInDays',
            width: '14%',
            align: 'right' as const,
            render: (v: number) => <span className="hs-time">{v}</span>,
          },
          {
            title: '预警',
            dataIndex: 'warnExpiry',
            key: 'warnExpiry',
            width: '8%',
            render: (v: boolean) =>
              v ? <Tag color="red">到期预警</Tag> : <span className="hs-none">—</span>,
          },
          {
            title: '库存线',
            dataIndex: 'warnStock',
            key: 'warnStock',
            width: 120,
            render: (_, record) =>
              record.warnStock ? (
                <span className="hs-warn">
                  {record.stockMin != null && (record.stock ?? 0) < record.stockMin
                    ? `低于下限 ${record.stockMin}g`
                    : `高于上限 ${record.stockMax}g`}
                </span>
              ) : (
                <span className="hs-ok">—</span>
              ),
          },
        ]}
      />
      <ProTable<HerbStockLog>
        className="hs-flow"
        actionRef={actionRef}
        headerTitle="出入库流水"
        toolBarRender={() => []}
        rowKey="id"
        search={false}
        options={false}
        request={async (params) => {
          const res = await queryStockPage({
            current: params.current,
            pageSize: params.pageSize,
            herbId: params.herbId,
            type: params.type,
          });
          return {
            data: res?.data || [],
            total: res?.total,
            success: true,
          };
        }}
        columns={[
          ...columns,
          {
            title: '操作',
            key: 'action',
            width: 90,
            fixed: 'right' as const,
            render: (_, record) => (
              <Popconfirm
                title="删除该流水？"
                onConfirm={async () => {
                  await deleteStockLog(record.id!);
                  message.success('已删除');
                  actionRef.current?.reload();
                  loadBalance();
                }}
              >
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            ),
          },
        ]}
      />

      <Modal
        title="出入库登记"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        confirmLoading={saving}
        okText="登记"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="herbId" label="药材" rules={[{ required: true, message: '请选择药材' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择药材"
              options={herbs.map((h) => ({ value: h.id, label: h.name }))}
            />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]} initialValue={1}>
            <Radio.Group>
              <Radio value={1}>入库</Radio>
              <Radio value={0}>出库</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="quantity"
            label="数量（克）"
            rules={[{ required: true, message: '请输入数量' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.type !== curr.type}>
            {({ getFieldValue }) =>
              getFieldValue('type') === 1 ? (
                <Form.Item
                  name="expiry"
                  label="批次效期"
                  rules={[{ required: true, message: '请选择效期' }]}
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    disabledDate={(d) => d.isBefore(dayjs(), 'day')}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="supplier" label="供货方">
            <Input placeholder="如 亳州药市" maxLength={50} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input placeholder="如 门诊领用" maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};
