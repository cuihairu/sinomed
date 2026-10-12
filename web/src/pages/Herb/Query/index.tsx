import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, message, Modal, Popconfirm } from 'antd';
import { useRef, useState } from 'react';
import { createHerb, deleteHerb, queryHerbByPage, updateHerb, type Herb } from '@/services/ant-design-pro/herb';
import './index.less';

/** 分 → 元展示（每克单价） */
const fenToYuan = (fen?: number) => `¥${((fen ?? 0) / 100).toFixed(2)}`;

export default () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<Herb | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: Herb) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      price: record.price,
      stockMin: record.stockMin,
      stockMax: record.stockMax,
      doseMax: record.doseMax,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const saved = editing?.id
        ? await updateHerb({ id: editing.id!, ...values })
        : await createHerb(values);
      if (saved?.id) {
        message.success(editing?.id ? '已更新' : '已收录');
        setModalOpen(false);
        actionRef.current?.reload();
      }
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<Herb>[] = [
    {
      title: '药材',
      dataIndex: 'name',
      width: '34%',
      render: (_, record) => <span className="hb-nm">{record.name || '-'}</span>,
    },
    {
      title: '每克价格',
      dataIndex: 'price',
      width: 140,
      render: (_, record) => <span className="price">{fenToYuan(record.price)}/克</span>,
    },
    {
      title: '单剂上限',
      dataIndex: 'doseMax',
      width: 110,
      render: (_, record) => <span className="tm">{record.doseMax != null ? `${record.doseMax} 克` : '—'}</span>,
    },
    {
      title: '收录时间',
      dataIndex: 'createTime',
      width: 180,
      render: (_, record) => <span className="tm">{record.createTime || '—'}</span>,
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 130,
      render: (_, record, __, action) => [
        <a key="update" onClick={() => openEdit(record)}>
          改价
        </a>,
        <Popconfirm
          key="delete"
          title="删除后相关处方药味转「未比价」，确认删除？"
          onConfirm={async () => {
            if (record.id) {
              await deleteHerb(record.id);
              message.success('已删除');
              action?.reload();
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
      title="药材字典"
      content={
        <span className="hb-tip">
          处方计价的比价依据：<b>药材名与处方药名精确同名匹配</b>（如「炙甘草」不会匹配「甘草」），
          价格为每克单价；未收录的药名在处方里如实标「未比价」，不计入金额。
        </span>
      }
    >
      <ProTable<Herb>
        columns={columns}
        actionRef={actionRef}
        rowKey="id"
        search={false}
        options={false}
        request={async (params = {}) => {
          const msg = await queryHerbByPage({
            current: params.current,
            pageSize: params.pageSize,
            keyword: (params as any).keyword,
          });
          return { data: msg.data, success: true, total: msg.total };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 味 · 每页 20 条` }}
        toolBarRender={() => [
          <Button key="create" type="primary" onClick={openCreate}>
            收录药材
          </Button>,
        ]}
      />
      <Modal
        title={editing?.id ? `改价 · ${editing.name}` : '收录药材'}
        open={modalOpen}
        onOk={submit}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" className="hb-form">
          <Form.Item
            name="name"
            label="药材名（唯一）"
            rules={[{ required: true, message: '药材名不能为空' }]}
          >
            <Input placeholder="如 炙甘草" maxLength={30} />
          </Form.Item>
          <Form.Item
            name="price"
            label="每克价格（分）"
            rules={[{ required: true, message: '每克价格必须大于 0' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="如 5（即 ¥0.05/克）" />
          </Form.Item>
          <Form.Item name="stockMin" label="库存下限（克）" tooltip="可空=不预警；库存余额看板对低于下限的药材标预警">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="如 500" />
          </Form.Item>
          <Form.Item name="stockMax" label="库存上限（克）" tooltip="可空=不预警；库存余额看板对高于上限的药材标预警">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="如 5000" />
          </Form.Item>
          <Form.Item name="doseMax" label="单剂上限（克）" tooltip="可空=不预警；开方时药味单剂克数严格大于该值即提示（不拦截，由医师判断）">
            <InputNumber min={0.1} precision={1} style={{ width: '100%' }} placeholder="如 10" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};
