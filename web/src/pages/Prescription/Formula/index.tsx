import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, message, Modal, Popconfirm } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useRef, useState } from 'react';
import {
  createFormula,
  deleteFormula,
  queryFormulaPage,
  updateFormula,
  type Formula,
} from '@/services/ant-design-pro/formula';
import './index.less';

export default () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<Formula | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [keyword, setKeyword] = useState('');

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ herbs: [{ herb: '', weight: undefined }] });
    setModalOpen(true);
  };

  const openEdit = (record: Formula) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      pinyin: record.pinyin,
      source: record.source,
      indication: record.indication,
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
    const payload = { ...values, pinyin: (values.pinyin || '').trim().toLowerCase(), herbs };
    setSaving(true);
    try {
      const saved = editing?.id
        ? await updateFormula({ id: editing.id, ...payload })
        : await createFormula(payload);
      if (saved?.id) {
        message.success(editing?.id ? '方剂已更新' : '方剂已收录');
        setModalOpen(false);
        actionRef.current?.reload();
      }
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<Formula>[] = [
    {
      title: '方名',
      dataIndex: 'name',
      width: '16%',
      render: (_, record) => (
        <span className="fm-nm">
          {record.name || '-'}
          <i className="fm-py">{record.pinyin}</i>
        </span>
      ),
    },
    {
      title: '药味组成',
      dataIndex: 'herbs',
      width: '44%',
      render: (_, record) => (
        <span className="fm-herbs">
          {(record.herbs || []).map((h, i) => (
            <span className="fm-herb" key={i}>
              {h.herb} {h.weight}g{h.special ? `·${h.special}` : ''}
            </span>
          ))}
        </span>
      ),
    },
    {
      title: '出处',
      dataIndex: 'source',
      width: '14%',
      ellipsis: true,
      render: (_, record) => <span className="tm">{record.source || '—'}</span>,
    },
    {
      title: '功效主治',
      dataIndex: 'indication',
      width: '20%',
      ellipsis: true,
      render: (_, record) => <span className="tm">{record.indication || '—'}</span>,
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
          title="删除方剂不影响已开处方，确认删除？"
          onConfirm={async () => {
            if (record.id) {
              await deleteFormula(record.id);
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
      title="方剂库"
      content={
        <span className="fm-tip">
          经典方剂参考库：开方页「方剂库」按方名或拼音（如 <code>xiaoyao</code>）检索，
          <b>一键带出全方</b>
          ——带出后随处方自由增减，不写回方剂库。药味克数为文献常用量口径，仅作参考。
        </span>
      }
    >
      <ProTable<Formula>
        columns={columns}
        actionRef={actionRef}
        rowKey="id"
        search={false}
        options={false}
        request={async (params = {}) => {
          const msg = await queryFormulaPage({
            current: params.current,
            pageSize: params.pageSize,
            keyword: keyword || undefined,
          });
          return { data: msg.data, success: true, total: msg.total };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 首方剂 · 每页 20 条` }}
        toolBarRender={() => [
          <Input.Search
            key="search"
            allowClear
            placeholder="方名或拼音检索，如 逍遥 / xiaoyao"
            style={{ width: 240 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              actionRef.current?.reload();
            }}
          />,
          <Button key="create" type="primary" onClick={openCreate}>
            收录方剂
          </Button>,
        ]}
      />
      <Modal
        title={editing?.id ? `编辑方剂 · ${editing.name}` : '收录方剂'}
        open={modalOpen}
        onOk={submit}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        cancelText="取消"
        width={640}
      >
        <Form form={form} layout="vertical" className="fm-form">
          <div className="fm-meta">
            <Form.Item
              name="name"
              label="方名（唯一，如「逍遥散」）"
              rules={[{ required: true, message: '方名不能为空' }]}
            >
              <Input placeholder="如 逍遥散" maxLength={20} />
            </Form.Item>
            <Form.Item
              name="pinyin"
              label="拼音检索码（全拼小写）"
              rules={[
                { required: true, message: '拼音检索码不能为空' },
                { pattern: /^[a-z]+$/, message: '只能为小写字母' },
              ]}
            >
              <Input placeholder="如 xiaoyaosan" maxLength={40} />
            </Form.Item>
          </div>
          <Form.Item label="药味组成（至少 1 味，剂量为单剂克数）" required>
            <Form.List name="herbs">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <div className="fm-herb-row" key={field.key}>
                      <Form.Item
                        name={[field.name, 'herb']}
                        noStyle
                        rules={[{ required: true, message: '药名不能为空' }]}
                      >
                        <Input placeholder="药名，如 柴胡" style={{ width: '45%' }} />
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
                      <MinusCircleOutlined onClick={() => remove(field.name)} className="fm-del" />
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
          <Form.Item name="source" label="出处">
            <Input placeholder="如 太平惠民和剂局方" maxLength={30} />
          </Form.Item>
          <Form.Item name="indication" label="功效主治">
            <Input placeholder="如 疏肝健脾养血，用于肝郁血虚脾弱" maxLength={60} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};
