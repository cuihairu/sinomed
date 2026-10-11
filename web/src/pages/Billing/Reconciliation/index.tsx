import { PageContainer } from '@ant-design/pro-components';
import { DatePicker, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import moment from 'moment';
import {
  PAY_TYPE_TEXT,
  fetchReconciliation,
  type Reconciliation,
  type ReconPayRow,
  type ReconRecharge,
  type RefundRecord,
} from '@/services/ant-design-pro/billing';
import './index.less';

/** 日对账：按自然日聚合收款/充值/退款三段；净额收银核对用（口径见 docs/design/reconciliation.md） */
export default function BillingReconciliation() {
  const [date, setDate] = useState<string>(moment().format('YYYY-MM-DD'));
  const [report, setReport] = useState<Reconciliation | undefined>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (d: string) => {
    setLoading(true);
    try {
      setReport(await fetchReconciliation(d));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(date);
  }, [date, load]);

  const payColumns: ColumnsType<ReconPayRow> = [
    { title: '支付方式', dataIndex: 'payTypeText', render: (_, r) => PAY_TYPE_TEXT[r.payType ?? 0] || r.payTypeText },
    { title: '笔数', dataIndex: 'count', width: 90, render: (_, r) => `${r.count ?? 0} 笔` },
    { title: '金额', dataIndex: 'money', width: 130, render: (_, r) => `¥${r.money ?? 0}` },
  ];

  const rechargeColumns: ColumnsType<ReconRecharge> = [
    { title: '顾客', dataIndex: 'customerName', render: (_, r) => r.customerName || '-' },
    { title: '金额', dataIndex: 'money', width: 110, render: (_, r) => <b>¥{r.money ?? 0}</b> },
    { title: '备注', dataIndex: 'note', ellipsis: true, render: (_, r) => r.note || '-' },
    {
      title: '充值时间',
      dataIndex: 'createTime',
      width: 150,
      render: (_, r) => (r.createTime ? moment(r.createTime).format('MM-DD HH:mm') : '-'),
    },
  ];

  const refundColumns: ColumnsType<RefundRecord> = [
    { title: '结算单', dataIndex: 'settlementId', width: 90, render: (_, r) => `S-${r.settlementId ?? ''}` },
    { title: '顾客', dataIndex: 'customerName', width: 110, render: (_, r) => r.customerName || '-' },
    { title: '金额', dataIndex: 'money', width: 100, render: (_, r) => <b className="rc-out">-¥{r.money ?? 0}</b> },
    { title: '原支付方式', dataIndex: 'payType', width: 110, render: (_, r) => PAY_TYPE_TEXT[r.payType ?? 0] || '-' },
    { title: '退款理由', dataIndex: 'reason', ellipsis: true },
    { title: '操作人', dataIndex: 'staffName', width: 100, render: (_, r) => r.staffName || <span className="faint">未记录</span> },
    {
      title: '退款时间',
      dataIndex: 'createTime',
      width: 130,
      render: (_, r) => (r.createTime ? moment(r.createTime).format('MM-DD HH:mm') : '-'),
    },
  ];

  return (
    <PageContainer
      title="日对账"
      content="按自然日对账：收款、充值（单列不计营收）、退款三段核对；净额 = 实收 - 退款，现金净额供钱箱核对。退款在结算台「最近结算」发起。"
    >
      <div className="rc-page">
        <div className="rc-toolbar">
          <DatePicker
            value={date ? moment(date) : undefined}
            allowClear={false}
            disabledDate={(d) => d.isAfter(moment(), 'day')}
            onChange={(d) => setDate(d ? d.format('YYYY-MM-DD') : moment().format('YYYY-MM-DD'))}
          />
          <span className="faint">对账日 {date}（充值不计营收；退款按原支付方式构成）</span>
        </div>

        <div className="rc-stats">
          <div className="stat">
            <span className="k">实收</span>
            <b>¥{report?.incomeMoney ?? 0}</b>
            <span className="sub">
              {report?.incomeCount ?? 0} 单{report?.cardCount ? ` · 次卡核销 ${report.cardCount} 单（实收 0）` : ''}
            </span>
          </div>
          <div className="stat">
            <span className="k">退款</span>
            <b className="rc-out">-¥{report?.refundMoney ?? 0}</b>
            <span className="sub">{report?.refundCount ?? 0} 笔</span>
          </div>
          <div className="stat">
            <span className="k">净额</span>
            <b>¥{report?.netMoney ?? 0}</b>
            <span className="sub">实收 - 退款</span>
          </div>
          <div className="stat">
            <span className="k">现金净额</span>
            <b>¥{report?.cashNetMoney ?? 0}</b>
            <span className="sub">现金收款 - 现金退款 · 钱箱核对</span>
          </div>
          <div className="stat">
            <span className="k">充值</span>
            <b>¥{report?.rechargeMoney ?? 0}</b>
            <span className="sub">{report?.rechargeCount ?? 0} 笔 · 单列不计营收</span>
          </div>
        </div>

        <section className="rc-block">
          <h3>收款构成</h3>
          <Table<ReconPayRow>
            rowKey={(r) => String(r.payType)}
            columns={payColumns}
            dataSource={report?.incomePayTypes || []}
            pagination={false}
            size="small"
            loading={loading}
            locale={{ emptyText: '当日无收款' }}
          />
        </section>

        <section className="rc-block">
          <h3>充值明细（不含储值支付扣减与退款补回）</h3>
          <Table<ReconRecharge>
            rowKey="id"
            columns={rechargeColumns}
            dataSource={report?.recharges || []}
            pagination={false}
            size="small"
            loading={loading}
            locale={{ emptyText: '当日无充值' }}
          />
        </section>

        <section className="rc-block">
          <h3>退款明细</h3>
          <Table<RefundRecord>
            rowKey="id"
            columns={refundColumns}
            dataSource={report?.refunds || []}
            pagination={false}
            size="small"
            loading={loading}
            locale={{ emptyText: '当日无退款' }}
          />
        </section>

        <div className="rc-note faint">
          口径：微信/支付宝退款为演示口径仅落退款记录，不真转；次卡退款作废核销流水并回补余次、金额记 0；
          储值退款补回余额（不计入充值）。订单退款后回「已接单」，可在结算台重新收款。
        </div>
      </div>
    </PageContainer>
  );
}
