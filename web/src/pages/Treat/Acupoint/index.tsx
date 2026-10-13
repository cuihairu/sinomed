import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { Button, Form, Input, message, Modal, Popconfirm } from 'antd';
import { useRef, useState } from 'react';
import {
  createAcupoint,
  deleteAcupoint,
  queryAcupointPage,
  updateAcupoint,
  type Acupoint,
} from '@/services/ant-design-pro/acupoint';
import './index.less';

export default () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<Acupoint | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [keyword, setKeyword] = useState('');

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: Acupoint) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      pinyin: record.pinyin,
      meridian: record.meridian,
      location: record.location,
      indication: record.indication,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload = { ...values, pinyin: (values.pinyin || '').trim().toLowerCase() };
    setSaving(true);
    try {
      const saved = editing?.id
        ? await updateAcupoint({ id: editing.id, ...payload })
        : await createAcupoint(payload);
      if (saved?.id) {
        message.success(editing?.id ? '穴位已更新' : '穴位已收录');
        setModalOpen(false);
        actionRef.current?.reload();
      }
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<Acupoint>[] = [
    {
      title: '穴位',
      dataIndex: 'name',
      width: '14%',
      render: (_, record) => (
        <span className="ap-nm">
          {record.name || '-'}
          <i className="ap-py">{record.pinyin}</i>
        </span>
      ),
    },
    {
      title: '归经',
      dataIndex: 'meridian',
      width: '14%',
      render: (_, record) => <span className="ap-mr">{record.meridian || '—'}</span>,
    },
    {
      title: '定位',
      dataIndex: 'location',
      width: '32%',
      ellipsis: true,
      render: (_, record) => <span className="tm">{record.location || '—'}</span>,
    },
    {
      title: '主治',
      dataIndex: 'indication',
      width: '30%',
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
          title="删除穴位不影响已开接诊单，确认删除？"
          onConfirm={async () => {
            if (record.id) {
              await deleteAcupoint(record.id);
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
      title="穴位字典"
      content={
        <span className="ap-tip">
          经络穴位参考库：接诊页「取穴」按穴名或拼音（如 <code>zusanli</code>）检索选穴，
          取穴字段仍为自由文本、字典只做选穴辅助。定位与主治为文献常用口径，仅作参考。
        </span>
      }
    >
      <ProTable<Acupoint>
        columns={columns}
        actionRef={actionRef}
        rowKey="id"
        search={false}
        options={false}
        request={async (params = {}) => {
          const msg = await queryAcupointPage({
            current: params.current,
            pageSize: params.pageSize,
            keyword: keyword || undefined,
          });
          return { data: msg.data, success: true, total: msg.total };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 穴 · 每页 20 条` }}
        toolBarRender={() => [
          <Input.Search
            key="search"
            allowClear
            placeholder="穴名或拼音检索，如 足三 / zusanli"
            style={{ width: 240 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              actionRef.current?.reload();
            }}
          />,
          <Button key="create" type="primary" onClick={openCreate}>
            收录穴位
          </Button>,
        ]}
      />
      <Modal
        title={editing?.id ? `编辑穴位 · ${editing.name}` : '收录穴位'}
        open={modalOpen}
        onOk={submit}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        cancelText="取消"
        width={560}
      >
        <Form form={form} layout="vertical" className="ap-form">
          <div className="ap-meta">
            <Form.Item
              name="name"
              label="穴位名（唯一，如「足三里」）"
              rules={[{ required: true, message: '穴位名不能为空' }]}
            >
              <Input placeholder="如 足三里" maxLength={10} />
            </Form.Item>
            <Form.Item
              name="pinyin"
              label="拼音检索码（全拼小写）"
              rules={[
                { required: true, message: '拼音检索码不能为空' },
                { pattern: /^[a-z]+$/, message: '只能为小写字母' },
              ]}
            >
              <Input placeholder="如 zusanli" maxLength={30} />
            </Form.Item>
          </div>
          <Form.Item
            name="meridian"
            label="归经（如「足阳明胃经」；奇穴填「经外奇穴」）"
            rules={[{ required: true, message: '归经不能为空' }]}
          >
            <Input placeholder="如 足阳明胃经" maxLength={20} />
          </Form.Item>
          <Form.Item name="location" label="体表定位">
            <Input.TextArea
              placeholder="如 犊鼻下 3 寸，胫骨前嵴外 1 横指"
              rows={2}
              maxLength={80}
            />
          </Form.Item>
          <Form.Item name="indication" label="主治">
            <Input.TextArea
              placeholder="如 健脾和胃、扶正培元，主治胃痛、腹胀"
              rows={2}
              maxLength={80}
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};
