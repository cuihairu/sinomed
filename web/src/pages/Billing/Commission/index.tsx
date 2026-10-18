import React, { useCallback, useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Input, InputNumber, message, Popconfirm, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  createCommissionRule,
  deleteCommissionRule,
  queryCommissionRecordPage,
  queryCommissionRuleList,
  queryCommissionSummary,
  type CommissionRecord,
  type CommissionRule,
  type CommissionSummary,
} from '@/services/ant-design-pro/commission';
import { queryItemByPage } from '@/services/ant-design-pro/item';

/**
 * 提成管理（美容业态 c，docs/design/salon-extension.md §2-c）：
 * P0 固定比例「项目×岗位」——岗位取员工档案的职务（title），结算时按订单明细行小计自动落账，
 * 次卡抵扣实收 0 也记（按业绩不按实收）；退单写等额负向冲销，净额归零。
 */
export default function CommissionManage() {
  const [rules, setRules] = useState<CommissionRule[]>([]);
  const [records, setRecords] = useState<CommissionRecord[]>([]);
  const [recordTotal, setRecordTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [summary, setSummary] = useState<CommissionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [itemOptions, setItemOptions] = useState<{ label: string; value: number }[]>([]);
  const [itemId, setItemId] = useState<number | undefined>();
  const [title, setTitle] = useState('');
  const [ratio, setRatio] = useState<number>(20);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [ruleList, recordPage, summaryList] = await Promise.all([
        queryCommissionRuleList(),
        queryCommissionRecordPage({ current: page, pageSize: 20 }),
        queryCommissionSummary(),
      ]);
      setRules(ruleList || []);
      setRecords(recordPage?.data || []);
      setRecordTotal(recordPage?.total || 0);
      setSummary(summaryList || []);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  const loadItems = useCallback(async () => {
    if (itemOptions.length > 0) return;
    const items = await queryItemByPage({ current: 1, pageSize: 100 });
    setItemOptions(
      (items?.data || []).map((i) => ({ label: i.name || `卡项${i.id}`, value: i.id! })),
    );
  }, [itemOptions.length]);

  const add = async () => {
    if (!itemId || !title.trim() || ratio == null) {
      message.warning('请选卡项、填岗位与比例');
      return;
    }
    setCreating(true);
    try {
      const created = await createCommissionRule({
        itemId,
        title: title.trim(),
        ratio,
      });
      if (created?.id) {
        message.success('规则已建');
        setItemId(undefined);
        setTitle('');
        load();
      }
    } finally {
      setCreating(false);
    }
  };

  const ruleColumns: ColumnsType<CommissionRule> = [
    { title: 'id', dataIndex: 'id', width: 60 },
    {
      title: '卡项',
      dataIndex: 'itemId',
      render: (_, rule) =>
        itemOptions.find((o) => o.value === rule.itemId)?.label || `#${rule.itemId}`,
    },
    { title: '岗位（职务精确匹配）', dataIndex: 'title' },
    {
      title: '比例',
      dataIndex: 'ratio',
      width: 90,
      render: (_, rule) => <Tag color="gold">{rule.ratio}%</Tag>,
    },
    {
      title: '操作',
      key: 'option',
      width: 80,
      render: (_, rule) => (
        <Popconfirm
          title="删除该规则？已生成的提成记录不受影响"
          onConfirm={async () => {
            await deleteCommissionRule(rule.id!);
            message.success('删除成功');
            load();
          }}
        >
          <a>删除</a>
        </Popconfirm>
      ),
    },
  ];

  const recordColumns: ColumnsType<CommissionRecord> = [
    { title: 'id', dataIndex: 'id', width: 60 },
    { title: '订单', dataIndex: 'orderId', width: 80, render: (_, r) => `#${r.orderId}` },
    { title: '员工', dataIndex: 'staffName', width: 110 },
    { title: '卡项（快照）', dataIndex: 'itemName' },
    {
      title: '小计 × 比例',
      dataIndex: 'subtotal',
      width: 130,
      render: (_, r) => `${r.subtotal ?? 0} × ${r.ratio ?? 0}%`,
    },
    {
      title: '提成',
      dataIndex: 'amount',
      width: 90,
      render: (_, r) =>
        (r.amount ?? 0) < 0 ? (
          <span style={{ color: '#cf1322' }}>{r.amount}</span>
        ) : (
          <span style={{ color: '#3f8600' }}>{r.amount}</span>
        ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      render: (_, r) => r.remark || '-',
    },
  ];

  return (
    <PageContainer
      title="提成管理"
      content="P0 固定比例「项目×岗位」：岗位取员工档案职务（如 技师/美容师），结算时按订单明细行小计自动落账；次卡抵扣实收 0 也记（按业绩不按实收）；退单自动等额冲销。医师与技师共用本套规则，不另建。"
    >
      <h3>提成规则</h3>
      <div
        style={{
          display: 'flex',
          gap: 12,
          marginBottom: 16,
          flexWrap: 'wrap',
          maxWidth: 640,
        }}
      >
        <Select
          style={{ minWidth: 220 }}
          placeholder="选卡项"
          showSearch
          optionFilterProp="label"
          value={itemId}
          options={itemOptions}
          onFocus={loadItems}
          onChange={setItemId}
        />
        <Input
          style={{ width: 180 }}
          placeholder="岗位（如 技师）"
          value={title}
          maxLength={20}
          onChange={(e) => setTitle(e.target.value)}
        />
        <InputNumber
          style={{ width: 110 }}
          min={0}
          max={100}
          addonAfter="%"
          value={ratio}
          onChange={(v) => setRatio(v ?? 0)}
        />
        <Button type="primary" loading={creating} onClick={add}>
          新建
        </Button>
      </div>
      <Table<CommissionRule>
        rowKey="id"
        columns={ruleColumns}
        dataSource={rules}
        loading={loading}
        pagination={false}
        size="middle"
        style={{ marginBottom: 32 }}
      />

      <h3>员工合计（工资基数）</h3>
      <Table<CommissionSummary>
        rowKey="staffId"
        columns={[
          { title: '员工', dataIndex: 'staffName', render: (_, s) => s.staffName || `#${s.staffId}` },
          { title: '提成合计（元，含冲销）', dataIndex: 'total', width: 200 },
        ]}
        dataSource={summary}
        loading={loading}
        pagination={false}
        size="small"
        style={{ marginBottom: 32 }}
      />

      <h3>提成流水</h3>
      <Table<CommissionRecord>
        rowKey="id"
        columns={recordColumns}
        dataSource={records}
        loading={loading}
        size="middle"
        pagination={{
          current: page,
          pageSize: 20,
          total: recordTotal,
          showTotal: (t) => `共 ${t} 条`,
          onChange: setPage,
        }}
      />
    </PageContainer>
  );
}
