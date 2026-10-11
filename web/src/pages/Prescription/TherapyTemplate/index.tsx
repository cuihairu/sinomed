import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Form, Input, message, Modal, Popconfirm, Switch } from 'antd';
import { useRef, useState } from 'react';
import {
  createTherapyTemplate,
  deleteTherapyTemplate,
  queryTherapyTemplatePage,
  updateTherapyTemplate,
  type TherapyTemplate,
} from '@/services/ant-design-pro/therapy';
import './index.less';

/** 外治处方模板管理页：模板只存建议值，接诊页「套用外治模板」回填随单自由改（不写回模板） */
export default () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<TherapyTemplate | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ enabled: true });
    setModalOpen(true);
  };

  const openEdit = (record: TherapyTemplate) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      method: record.method,
      target: record.target,
      course: record.course,
      note: record.note,
      enabled: record.enabled === 1,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    // Switch 的布尔值转服务端 0/1 口径
    const payload = { ...values, enabled: values.enabled ? 1 : 0 };
    setSaving(true);
    try {
      const saved = editing?.id
        ? await updateTherapyTemplate({ id: editing.id, ...payload })
        : await createTherapyTemplate(payload);
      if (saved?.id) {
        message.success(editing?.id ? '模板已更新' : '模板已收录');
        setModalOpen(false);
        actionRef.current?.reload();
      }
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (record: TherapyTemplate, enabled: number) => {
    await updateTherapyTemplate({ ...record, enabled });
    message.success(enabled ? '已上架' : '已停用（接诊页不再出现）');
    actionRef.current?.reload();
  };

  const columns: ProColumns<TherapyTemplate>[] = [
    {
      title: '模板名',
      dataIndex: 'name',
      width: '18%',
      render: (_, record) => <span className="tt-nm">{record.name || '-'}</span>,
    },
    {
      title: '方法',
      dataIndex: 'method',
      width: 100,
      render: (_, record) => <span className="tt-method">{record.method}</span>,
    },
    {
      title: '取穴/部位',
      dataIndex: 'target',
      width: '22%',
      ellipsis: true,
      render: (_, record) => <span>{record.target || '—'}</span>,
    },
    {
      title: '疗程/频次',
      dataIndex: 'course',
      width: '20%',
      ellipsis: true,
      render: (_, record) => <span>{record.course || '—'}</span>,
    },
    {
      title: '注意事项',
      dataIndex: 'note',
      width: '18%',
      ellipsis: true,
      render: (_, record) => <span className="tm">{record.note || '—'}</span>,
    },
    {
      title: '上架',
      dataIndex: 'enabled',
      width: 80,
      render: (_, record) => (
        <Switch
          size="small"
          checked={record.enabled === 1}
          onChange={(next) => record.id && toggleEnabled(record, next ? 1 : 0)}
        />
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 110,
      render: (_, record) => [
        <a key="update" onClick={() => openEdit(record)}>
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title="删除模板不影响已接诊记录，确认删除？"
          onConfirm={async () => {
            if (record.id) {
              await deleteTherapyTemplate(record.id);
              message.success('已删除');
              actionRef.current?.reload();
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
      title="外治处方模板"
      content={
        <span className="tt-tip">
          耳穴压豆、穴位贴敷、艾灸、拔罐等外治法的常用组合：接诊页「套用外治模板」一键带出方法/取穴/疗程。
          <b>模板只存建议值</b>——套用后随诊疗单自由增减，不会写回模板；停用的模板不在接诊页出现。
        </span>
      }
    >
      <ProTable<TherapyTemplate>
        columns={columns}
        actionRef={actionRef}
        rowKey="id"
        search={false}
        options={false}
        request={async (params = {}) => {
          const msg = await queryTherapyTemplatePage({
            current: params.current,
            pageSize: params.pageSize,
          });
          return { data: msg.data, success: true, total: msg.total };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 个模板 · 每页 20 条` }}
        toolBarRender={() => [
          <Button key="create" type="primary" onClick={openCreate}>
            新建模板
          </Button>,
        ]}
      />
      <Modal
        title={editing?.id ? `编辑模板 · ${editing.name}` : '新建模板'}
        open={modalOpen}
        onOk={submit}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        cancelText="取消"
        width={560}
      >
        <Form form={form} layout="vertical" className="tt-form">
          <Form.Item
            name="name"
            label="模板名（唯一，如「失眠·耳穴压豆」）"
            rules={[{ required: true, message: '外治模板名不能为空' }]}
          >
            <Input placeholder="如 失眠·耳穴压豆" maxLength={20} showCount />
          </Form.Item>
          <Form.Item
            name="method"
            label="外治方法（如 耳穴压豆、穴位贴敷、艾灸、拔罐）"
            rules={[{ required: true, message: '外治方法必填' }]}
          >
            <Input placeholder="如 耳穴压豆" maxLength={10} showCount />
          </Form.Item>
          <Form.Item name="target" label="建议取穴/部位">
            <Input.TextArea
              rows={2}
              placeholder="如 神门、交感、皮质下；或 关元、气海"
              maxLength={100}
              showCount
            />
          </Form.Item>
          <Form.Item name="course" label="建议疗程/频次">
            <Input.TextArea
              rows={2}
              placeholder="如 每周 2 次 × 4 周；贴敷 3 天一换"
              maxLength={100}
              showCount
            />
          </Form.Item>
          <Form.Item name="note" label="注意事项">
            <Input.TextArea
              rows={2}
              placeholder="如 每日自行揉按 3 次，过敏停用"
              maxLength={100}
              showCount
            />
          </Form.Item>
          <Form.Item name="enabled" label="上架（停用后接诊页不再出现）" valuePropName="checked" initialValue={true}>
            <Switch size="small" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};
