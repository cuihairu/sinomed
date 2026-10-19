import { PageContainer } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Table, Tag } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createAutoReply,
  deleteAutoReply,
  queryAutoRepliesByPage,
  queryWxAccounts,
  updateAutoReply,
  type WxAccount,
  type WxAutoReply,
} from '@/services/ant-design-pro/wx';
import moment from 'moment';

/** 规则类型标签（服务端校验同口径：subscribe/keyword/fallback） */
const RULE_TYPE_LABELS: Record<string, string> = {
  subscribe: '关注欢迎语',
  keyword: '关键词',
  fallback: '兜底回复',
};
const RULE_TYPES = Object.keys(RULE_TYPE_LABELS);

/** subscribe/fallback 同账号同时最多一条启用（服务层约束；新建时若已有启用规则需先停用旧的） */
const SINGLE_ENABLED_TYPES = ['subscribe', 'fallback'];

const MATCH_TYPE_LABELS: Record<string, string> = {
  exact: '全等',
  contains: '包含',
};

const WxPage: React.FC = () => {
  const [accounts, setAccounts] = useState<WxAccount[]>([]);
  const [accountId, setAccountId] = useState<number>();
  const [rules, setRules] = useState<WxAutoReply[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterRuleType, setFilterRuleType] = useState<string>();
  const [filterEnabled, setFilterEnabled] = useState<number>();

  const [ruleForm] = Form.useForm();
  const watchRuleType = Form.useWatch('ruleType', ruleForm) as WxAutoReply['ruleType'] | undefined;
  const [ruleModal, setRuleModal] = useState<{ open: boolean; editing?: WxAutoReply }>({ open: false });

  const loadRules = useCallback(async () => {
    if (accountId == null) return;
    setLoading(true);
    try {
      const res = await queryAutoRepliesByPage({
        current: 1,
        pageSize: 100,
        accountId,
        ruleType: filterRuleType,
        enabled: filterEnabled,
      });
      setRules(res?.data || []);
    } finally {
      setLoading(false);
    }
  }, [accountId, filterRuleType, filterEnabled]);

  useEffect(() => {
    queryWxAccounts().then((list) => {
      setAccounts(list || []);
      // 单店一期通常单账号：默认选中第一个，手工切换
      const first = (list || [])[0];
      if (first?.id != null) setAccountId(first.id);
    });
  }, []);

  useEffect(() => {
    loadRules();
  }, [loadRules]);

  const openCreate = () => {
    setRuleModal({ open: true });
    ruleForm.resetFields();
    ruleForm.setFieldsValue({ ruleType: 'keyword', matchType: 'exact', priority: 0 });
  };

  const openEdit = (entity: WxAutoReply) => {
    setRuleModal({ open: true, editing: entity });
    ruleForm.setFieldsValue(entity);
  };

  const saveRule = async () => {
    if (accountId == null) {
      message.error('请先选择公众号账号');
      return;
    }
    const values = await ruleForm.validateFields();
    const payload: WxAutoReply = { ...values, accountId };
    const res = ruleModal.editing
      ? await updateAutoReply({ ...ruleModal.editing, ...payload })
      : await createAutoReply(payload);
    if (res?.id) {
      message.success(ruleModal.editing ? '已更新' : '已创建');
      setRuleModal({ open: false });
      loadRules();
    }
  };

  const toggleRule = async (entity: WxAutoReply) => {
    const next = entity.enabled === 1 ? 0 : 1;
    const res = await updateAutoReply({ ...entity, enabled: next });
    if (res?.id) {
      message.success(next === 1 ? '已启用' : '已停用');
      loadRules();
    }
  };

  const removeRule = async (entity: WxAutoReply) => {
    await deleteAutoReply(entity.id!);
    message.success('已删除');
    loadRules();
  };

  const columns = useMemo(
    () => [
      { title: '优先级', dataIndex: 'priority', width: 80, render: (v: number) => v ?? 0 },
      {
        title: '类型',
        dataIndex: 'ruleType',
        width: 110,
        render: (v: string) => RULE_TYPE_LABELS[v] || v,
      },
      {
        title: '关键词 / 匹配',
        dataIndex: 'keyword',
        width: 200,
        render: (_: unknown, entity: WxAutoReply) =>
          entity.ruleType === 'keyword' ? (
            <span>
              {entity.keyword}{' '}
              <Tag>{MATCH_TYPE_LABELS[entity.matchType || 'exact'] || entity.matchType}</Tag>
            </span>
          ) : (
            <span style={{ color: '#aaa' }}>—</span>
          ),
      },
      {
        title: '回复内容',
        dataIndex: 'content',
        ellipsis: true,
      },
      {
        title: '状态',
        dataIndex: 'enabled',
        width: 90,
        render: (_: unknown, entity: WxAutoReply) => (
          <Popconfirm
            title={entity.enabled === 1 ? '停用该规则？' : '启用该规则？'}
            onConfirm={() => toggleRule(entity)}
            disabled={
              entity.enabled !== 1 &&
              SINGLE_ENABLED_TYPES.includes(entity.ruleType || '') &&
              rules.some(
                (r) =>
                  r.enabled === 1 &&
                  r.ruleType === entity.ruleType &&
                  r.id !== entity.id,
              )
            }
          >
            <Tag color={entity.enabled === 1 ? 'green' : 'default'}>
              {entity.enabled === 1 ? '启用' : '停用'}
            </Tag>
          </Popconfirm>
        ),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 150,
        render: (v: string) => (v ? moment(v).format('YYYY-MM-DD HH:mm') : '-'),
      },
      {
        title: '操作',
        key: 'actions',
        width: 130,
        render: (_: unknown, entity: WxAutoReply) => (
          <>
            <a onClick={() => openEdit(entity)}>编辑</a>
            <Popconfirm title="删除该规则？" onConfirm={() => removeRule(entity)}>
              <a style={{ marginLeft: 12, color: '#cf1322' }}>删除</a>
            </Popconfirm>
          </>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rules],
  );

  return (
    <PageContainer title="公众号管理" content={<span>自动回复规则编辑（第二期：规则不再手工改库；菜单 / 素材页待后续交付）</span>}>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Select
          style={{ width: 260 }}
          placeholder="选择公众号账号"
          value={accountId}
          onChange={setAccountId}
          options={accounts.map((a) => ({
            value: a.id!,
            label: `${a.accountName || a.appId}（${a.accountType === 'service' ? '服务号' : '订阅号'}${
              a.verified === 1 ? '·已认证' : ''
            }）`,
          }))}
        />
        <Select
          style={{ width: 140 }}
          allowClear
          placeholder="全部类型"
          value={filterRuleType}
          onChange={setFilterRuleType}
          options={RULE_TYPES.map((t) => ({ value: t, label: RULE_TYPE_LABELS[t] }))}
        />
        <Select
          style={{ width: 120 }}
          allowClear
          placeholder="全部状态"
          value={filterEnabled}
          onChange={setFilterEnabled}
          options={[
            { value: 1, label: '启用' },
            { value: 0, label: '停用' },
          ]}
        />
        <Button type="primary" disabled={accountId == null} onClick={openCreate} style={{ marginLeft: 'auto' }}>
          新建规则
        </Button>
      </div>

      <Table<WxAutoReply>
        rowKey="id"
        loading={loading}
        dataSource={rules}
        columns={columns}
        pagination={false}
        size="middle"
        locale={{ emptyText: accountId == null ? '暂无公众号账号（先在 wx_accounts 落账号行）' : '暂无规则' }}
      />

      <Modal
        title={ruleModal.editing ? '编辑规则' : '新建规则'}
        open={ruleModal.open}
        onOk={saveRule}
        onCancel={() => setRuleModal({ open: false })}
        destroyOnClose
        width={520}
      >
        <Form form={ruleForm} layout="vertical">
          <Form.Item
            name="ruleType"
            label="规则类型"
            rules={[{ required: true, message: '请选择规则类型' }]}
            extra={
              watchRuleType && SINGLE_ENABLED_TYPES.includes(watchRuleType)
                ? '该类型同账号同时只有一条启用（服务端校验：先停用旧规则再启用新的）'
                : undefined
            }
          >
            <Select options={RULE_TYPES.map((t) => ({ value: t, label: RULE_TYPE_LABELS[t] }))} />
          </Form.Item>
          {watchRuleType === 'keyword' && (
            <>
              <Form.Item
                name="keyword"
                label="关键词"
                rules={[{ required: true, whitespace: true, message: '关键词规则必须填写关键词' }]}
              >
                <Input maxLength={64} placeholder="如：营业时间" />
              </Form.Item>
              <Form.Item name="matchType" label="匹配方式" initialValue="exact">
                <Select
                  options={[
                    { value: 'exact', label: '全等（消息与关键词完全相同才命中）' },
                    { value: 'contains', label: '包含（消息里出现关键词即命中）' },
                  ]}
                />
              </Form.Item>
            </>
          )}
          <Form.Item
            name="content"
            label="回复内容"
            rules={[{ required: true, whitespace: true, message: '回复内容不能为空' }]}
          >
            <Input.TextArea rows={3} maxLength={600} showCount placeholder="粉丝命中规则后收到的文本回复" />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue={0} extra="数字小的先匹配；同账号关键词按此排序">
            <InputNumber min={0} max={9999} style={{ width: 120 }} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default WxPage;
