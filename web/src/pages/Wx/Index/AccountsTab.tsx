import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Form, Input, Modal, Select, Tag, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useModel } from '@umijs/max';
import {
  createWxAccount,
  queryWxAccounts,
  updateWxAccount,
  type WxAccount,
} from '@/services/ant-design-pro/wx';

/** 公众号账号配置 tab（设计稿 §4.1 配置入口/§6.3 仅 ADMIN；密钥只回显掩码，留空=保持） */
const AccountsTab: React.FC = () => {
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.access === 'admin';
  const [accounts, setAccounts] = useState<WxAccount[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const [modal, setModal] = useState<{ open: boolean; editing?: WxAccount }>({ open: false });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setAccounts((await queryWxAccounts()) || []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({
      accountType: 'service',
      verified: 0,
      enabled: 1,
      encryptMode: 'plaintext',
    });
    setModal({ open: true });
  };

  const openEdit = (row: WxAccount) => {
    form.resetFields();
    // 密钥只回显掩码（****），表单按「留空=保持」处理：不回填掩码，仅在已有存量时提示
    form.setFieldsValue({
      appId: row.appId,
      accountName: row.accountName,
      accountType: row.accountType,
      verified: row.verified,
      enabled: row.enabled,
      encryptMode: row.encryptMode,
      remark: row.remark,
    });
    setModal({ open: true, editing: row });
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (modal.editing) {
        await updateWxAccount(modal.editing.id!, { ...values, id: modal.editing.id });
        message.success('账号已更新');
      } else {
        await createWxAccount({ ...values });
        message.success('账号已创建');
      }
      setModal({ open: false });
      load();
    } catch (e: any) {
      message.error(
        e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '保存失败',
      );
    }
  };

  const columns: ProColumns<WxAccount>[] = [
    { title: 'ID', dataIndex: 'id', width: 60, hideInSearch: true },
    { title: '名称', dataIndex: 'accountName', ellipsis: true },
    { title: 'AppID', dataIndex: 'appId', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'accountType',
      width: 90,
      hideInSearch: true,
      render: (_, r) => (r.accountType === 'service' ? '服务号' : '订阅号'),
    },
    {
      title: '认证',
      dataIndex: 'verified',
      width: 70,
      hideInSearch: true,
      render: (_, r) => (r.verified ? <Tag color="green">已认证</Tag> : <Tag>未认证</Tag>),
    },
    { title: '加密模式', dataIndex: 'encryptMode', width: 90, hideInSearch: true },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      hideInSearch: true,
      render: (_, r) => (r.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '密钥',
      dataIndex: 'appSecret',
      width: 100,
      hideInSearch: true,
      render: (_, r) => (r.appSecret ? <Tag>已配置</Tag> : <Tag color="orange">未配置</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      hideInSearch: true,
      render: (_, r) =>
        isAdmin ? (
          <Button type="link" size="small" onClick={() => openEdit(r)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <div>
      {isAdmin ? (
        <Button type="primary" style={{ marginBottom: 16 }} onClick={openCreate}>
          新建公众号账号
        </Button>
      ) : (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="账号配置仅 ADMIN 可操作（设计稿 §6.3）；当前账号只读"
        />
      )}
      <ProTable<WxAccount>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={accounts}
        search={false}
        pagination={false}
        headerTitle="公众号账号（密钥只回显掩码；编辑时留空=保持既有）"
        locale={{ emptyText: '暂无公众号账号' }}
      />
      <Modal
        title={modal.editing ? '编辑公众号账号' : '新建公众号账号'}
        open={modal.open}
        onOk={submit}
        onCancel={() => setModal({ open: false })}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="appId"
            label="AppID"
            rules={[{ required: true, message: '请输入公众号 AppID' }]}
          >
            <Input placeholder="wx1234567890abcdef" />
          </Form.Item>
          <Form.Item name="accountName" label="账号名称">
            <Input placeholder="例如：XX中医馆服务号" />
          </Form.Item>
          <Form.Item name="accountType" label="账号类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'service', label: '服务号' },
                { value: 'subscription', label: '订阅号' },
              ]}
            />
          </Form.Item>
          <Form.Item name="verified" label="微信认证" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 0, label: '未认证' },
                { value: 1, label: '已认证' },
              ]}
            />
          </Form.Item>
          <Form.Item name="enabled" label="启用状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 0, label: '停用' },
                { value: 1, label: '启用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="encryptMode" label="消息加密模式" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'plaintext', label: '明文' },
                { value: 'compatible', label: '兼容' },
                { value: 'safe', label: '安全' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="appSecret"
            label="AppSecret"
            extra={
              modal.editing?.appSecret
                ? '已配置：留空=保持既有（服务端不回显明文）'
                : '留空=不配置凭据'
            }
          >
            <Input.Password
              placeholder={modal.editing?.appSecret ? '保持既有' : '请输入 AppSecret'}
            />
          </Form.Item>
          <Form.Item
            name="token"
            label="消息推送 Token"
            extra={modal.editing?.token ? '已配置：留空=保持既有' : '留空=不配置'}
          >
            <Input.Password placeholder={modal.editing?.token ? '保持既有' : '服务器配置 Token'} />
          </Form.Item>
          <Form.Item
            name="aesKey"
            label="EncodingAESKey"
            extra={modal.editing?.aesKey ? '已配置：留空=保持既有' : '留空=不配置'}
          >
            <Input.Password
              placeholder={modal.editing?.aesKey ? '保持既有' : '43 字符 EncodingAESKey'}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AccountsTab;
