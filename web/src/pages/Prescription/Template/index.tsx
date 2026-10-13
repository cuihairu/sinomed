import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Switch } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useRef, useState } from 'react';
import {
  createTemplate,
  deleteTemplate,
  queryTemplatePage,
  updateTemplate,
  type PrescriptionTemplate,
} from '@/services/ant-design-pro/prescription';
import './index.less';

export default () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<PrescriptionTemplate | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({
      doses: 7,
      decoction: 0,
      enabled: true,
      herbs: [{ herb: '', weight: undefined }],
    });
    setModalOpen(true);
  };

  const openEdit = (record: PrescriptionTemplate) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      doses: record.doses,
      decoction: record.decoction ?? 0,
      usage: record.usage,
      remark: record.remark,
      enabled: record.enabled === 1,
      herbs: (record.herbs || []).map((h) => ({
        herb: h.herb,
        weight: h.weight,
        special: h.special,
      })),
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const herbs = (values.herbs || []).filter(
      (h: any) => (h.herb || '').trim() !== '' || h.weight != null,
    );
    // Switch 的布尔值转服务端 0/1 口径
    const payload = { ...values, enabled: values.enabled ? 1 : 0, herbs };
    setSaving(true);
    try {
      const saved = editing?.id
        ? await updateTemplate({ id: editing.id, ...payload })
        : await createTemplate(payload);
      if (saved?.id) {
        message.success(editing?.id ? '模板已更新' : '模板已收录');
        setModalOpen(false);
        actionRef.current?.reload();
      }
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (record: PrescriptionTemplate, enabled: number) => {
    await updateTemplate({ ...record, enabled });
    message.success(enabled ? '已上架' : '已停用（开方页不再出现）');
    actionRef.current?.reload();
  };

  const columns: ProColumns<PrescriptionTemplate>[] = [
    {
      title: '病症',
      dataIndex: 'name',
      width: '18%',
      render: (_, record) => <span className="tpl-nm">{record.name || '-'}</span>,
    },
    {
      title: '药味',
      dataIndex: 'herbs',
      width: '42%',
      render: (_, record) => (
        <span className="tpl-herbs">
          {(record.herbs || []).map((h, i) => (
            <span className="tpl-herb" key={i}>
              {h.herb} {h.weight}g{h.special ? `·${h.special}` : ''}
            </span>
          ))}
        </span>
      ),
    },
    {
      title: '建议剂数',
      dataIndex: 'doses',
      width: 90,
      render: (_, record) => <span>{record.doses ?? 7} 付</span>,
    },
    {
      title: '代煎',
      dataIndex: 'decoction',
      width: 70,
      render: (_, record) => <span>{record.decoction ? '代煎' : '—'}</span>,
    },
    {
      title: '用法',
      dataIndex: 'usage',
      width: '18%',
      ellipsis: true,
      render: (_, record) => <span className="tm">{record.usage || '—'}</span>,
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
          title="删除模板不影响已开处方，确认删除？"
          onConfirm={async () => {
            if (record.id) {
              await deleteTemplate(record.id);
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
      title="病症处方模板"
      content={
        <span className="tpl-tip">
          按病症归组的常用药味组合：开方页「套用模板」一键带出药味/剂数/用法。
          <b>模板只存建议值</b>——套用后随处方自由增减，不会写回模板；停用的模板不在开方页出现。
        </span>
      }
    >
      <ProTable<PrescriptionTemplate>
        columns={columns}
        actionRef={actionRef}
        rowKey="id"
        search={false}
        options={false}
        request={async (params = {}) => {
          const msg = await queryTemplatePage({
            current: params.current,
            pageSize: params.pageSize,
            name: (params as any).name,
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
        width={640}
      >
        <Form form={form} layout="vertical" className="tpl-form">
          <Form.Item
            name="name"
            label="病症名（唯一，如「风寒感冒」）"
            rules={[{ required: true, message: '病症名不能为空' }]}
          >
            <Input placeholder="如 风寒感冒" maxLength={20} />
          </Form.Item>
          <Form.Item label="药味（至少 1 味，剂量为单剂克数）" required>
            <Form.List name="herbs">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <div className="tpl-herb-row" key={field.key}>
                      <Form.Item
                        name={[field.name, 'herb']}
                        noStyle
                        rules={[{ required: true, message: '药名不能为空' }]}
                      >
                        <Input placeholder="药名，如 荆芥" style={{ width: '45%' }} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'weight']}
                        noStyle
                        rules={[{ required: true, message: '剂量必须大于 0' }]}
                      >
                        <InputNumber placeholder="克" min={0.1} style={{ width: '25%' }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'special']} noStyle>
                        <Input placeholder="特殊煎法，选填" style={{ width: '25%' }} />
                      </Form.Item>
                      <MinusCircleOutlined onClick={() => remove(field.name)} className="tpl-del" />
                    </div>
                  ))}
                  <Button
                    type="dashed"
                    onClick={() => add({ herb: '', weight: undefined })}
                    icon={<PlusOutlined />}
                  >
                    添加一味
                  </Button>
                </>
              )}
            </Form.List>
          </Form.Item>
          <div className="tpl-meta">
            <Form.Item name="doses" label="建议剂数" initialValue={7}>
              <InputNumber min={1} precision={0} addonAfter="付" />
            </Form.Item>
            <Form.Item name="decoction" label="建议代煎" initialValue={0}>
              <Select
                style={{ width: 120 }}
                options={[
                  { value: 0, label: '无需代煎' },
                  { value: 1, label: '代煎' },
                ]}
              />
            </Form.Item>
            <Form.Item name="enabled" label="上架" valuePropName="checked" initialValue={true}>
              <Switch size="small" />
            </Form.Item>
          </div>
          <Form.Item name="usage" label="建议用法">
            <Input placeholder="如 水煎服，日一剂，早晚温服" maxLength={100} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input placeholder="如 随证加减" maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};
