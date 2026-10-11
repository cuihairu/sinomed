import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  updateCustomer,
  fetchCustomerById,
  listCustomerHistories,
  addCustomerHistory,
  deleteCustomerHistory,
  type CustomerHistory,
} from '@/services/ant-design-pro/customer';
import { queryTreatByPage } from '@/services/ant-design-pro/treat';
import { fetchOrderSummary } from '@/services/ant-design-pro/order';
import type { OrderSummary } from '@/services/ant-design-pro/order';
import { issueCard, listCardsByCustomer, setCardStatus, listCardUsages, type Card, type CardUsage } from '@/services/ant-design-pro/card';
import { queryItemByPage } from '@/services/ant-design-pro/item';
import { fetchCustomerReviews } from '@/services/ant-design-pro/review';
import {
  PageContainer,
  ProForm,
  ProFormText,
  ProFormTextArea,
  ProFormDatePicker,
  ProFormSelect,
  ProFormInstance,
} from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, InputNumber, message, Modal, Popconfirm, Radio, Select } from 'antd';
import { history, useSearchParams } from '@umijs/max';
import moment from 'moment';
import './index.less';

/** 会员等级展示口径：1 普通 / 2 银卡 / 3 金卡（演示种子数据即此三档） */
const LEVEL_TEXT: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员' };
const levelText = (level?: number) => LEVEL_TEXT[level ?? 0] ?? (level ? `会员 L${level}` : '普通会员');
const maskPhone = (p?: string) => (p && p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p || '—');

type TreatRow = { id?: number; createTime?: string; diagnose?: string; desc?: string; plan?: string };

const Update: React.FC = () => {
  const [searchParams] = useSearchParams();
  const customerId = searchParams.get('customerId');
  const formRef = useRef<ProFormInstance>();
  const [customer, setCustomer] = useState<API.Customer | undefined>();
  const [treats, setTreats] = useState<TreatRow[]>([]);
  const [treatTotal, setTreatTotal] = useState(0);
  const [summary, setSummary] = useState<OrderSummary>();
  const [editOpen, setEditOpen] = useState(false);
  const [cards, setCards] = useState<Card[]>([]);
  const [issueOpen, setIssueOpen] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [issueForm] = Form.useForm();
  const [usageCard, setUsageCard] = useState<Card | null>(null);
  const [usages, setUsages] = useState<CardUsage[]>([]);
  const [usageLoading, setUsageLoading] = useState(false);
  /** 病史（过敏/既往）：档案内逐条增删 */
  const [histories, setHistories] = useState<CustomerHistory[]>([]);
  const [hisType, setHisType] = useState<number>(0);
  const [hisText, setHisText] = useState('');
  const [hisBusy, setHisBusy] = useState(false);
  /** 回访计划：按顾客倒序，首条即最近安排 */
  const [reviews, setReviews] = useState<API.ReviewCustomer[]>([]);

  const loadHistories = useCallback(async () => {
    if (!customerId) return;
    const list = await listCustomerHistories(customerId);
    setHistories(list || []);
  }, [customerId]);

  const activeCardCount = cards.filter((c) => c.status === 1).length;

  const load = useCallback(async () => {
    if (!customerId) return;
    const v = await fetchCustomerById(customerId);
    setCustomer(v);
    const res = await queryTreatByPage({ customerId: Number(customerId), pageSize: 4, current: 1 });
    setTreats(res?.data || []);
    setTreatTotal(res?.total || 0);
    const s = await fetchOrderSummary(Number(customerId));
    setSummary(s);
    const cs = await listCardsByCustomer(Number(customerId));
    setCards(cs || []);
    const rv = await fetchCustomerReviews(Number(customerId));
    setReviews(rv || []);
  }, [customerId]);

  useEffect(() => {
    if (customerId === null || customerId === undefined || customerId === '') {
      message.error('没有传入顾客id');
      history.push('/customer/query');
      return;
    }
    load();
    loadHistories();
  }, [customerId, load, loadHistories]);

  const openEdit = () => {
    formRef.current?.setFieldsValue(customer);
    setEditOpen(true);
  };

  const [itemOptions, setItemOptions] = useState<{ label: string; value: number }[]>([]);

  const openIssue = async () => {
    issueForm.resetFields();
    setIssueOpen(true);
    if (itemOptions.length === 0) {
      const res = await queryItemByPage({ current: 1, pageSize: 100 });
      setItemOptions((res?.data || []).map((i) => ({ label: i.name || `卡项${i.id}`, value: i.id! })));
    }
  };

  const submitIssue = async () => {
    const values = await issueForm.validateFields();
    setIssuing(true);
    try {
      const created = await issueCard({
        customerId: Number(customerId),
        itemId: values.itemId,
        totalTimes: values.totalTimes,
      });
      if (created?.id) {
        message.success('已发卡');
        setIssueOpen(false);
        await load();
      }
    } finally {
      setIssuing(false);
    }
  };

  const openUsages = async (card: Card) => {
    setUsageCard(card);
    setUsages([]);
    setUsageLoading(true);
    try {
      const list = card.id ? await listCardUsages(card.id) : [];
      setUsages(list || []);
    } finally {
      setUsageLoading(false);
    }
  };

  const submitHistory = async () => {
    const text = hisText.trim();
    if (!text) {
      message.warning('请输入病史内容');
      return;
    }
    setHisBusy(true);
    try {
      await addCustomerHistory(Number(customerId), { type: hisType, content: text });
      message.success('已记录');
      setHisText('');
      await loadHistories();
    } finally {
      setHisBusy(false);
    }
  };

  const removeHistory = async (hid: number) => {
    await deleteCustomerHistory(hid);
    message.success('已删除');
    await loadHistories();
  };

  const lastVisit = treats
    .map((t) => t.createTime)
    .filter(Boolean)
    .sort()
    .pop();
  const lastVisitText = lastVisit
    ? moment().diff(moment(lastVisit), 'days') === 0
      ? '今天到店'
      : `最近到店 ${moment().diff(moment(lastVisit), 'days')} 天前`
    : '暂无到店记录';

  // 下次回访：今天起最近一条计划；无未来计划则如实显示「无计划」（接口按回访日倒序返回）
  const todayStart = moment().startOf('day');
  const upcomingReview = reviews
    .filter((r) => r.day && moment(r.day as any).isSameOrAfter(todayStart))
    .sort((a, b) => moment(a.day as any).valueOf() - moment(b.day as any).valueOf())[0];
  const nextReviewText = upcomingReview?.day ? moment(upcomingReview.day as any).format('MM-DD') : '无计划';
  const reviewNoteText = upcomingReview?.advice || '到店回访，可提前一天确认到店时间';

  return (
    <PageContainer
      title={customer ? `${customer.name || ''} · 顾客档案` : '顾客档案'}
      breadcrumb={{ items: [{ title: '顾客' }, { title: <a href="/customer/query">顾客查询</a> }, { title: customer?.name || '—' }] }}
    >
      <div className="cu-head">
        <div className="avatar">{(customer?.name || '客').slice(0, 1)}</div>
        <div className="who">
          <h2>
            {customer?.name || '—'}
            <span className="tier">{levelText(customer?.level)}</span>
          </h2>
          <p>
            <span>{maskPhone(customer?.phone)}</span>
            <span>
              {customer?.gender === 1 ? '男' : '女'} · {customer?.age ?? '—'} 岁
            </span>
            {customer?.address ? <span>{customer.address}</span> : null}
            <span>{lastVisitText}</span>
          </p>
        </div>
        <div className="head-actions">
          <Button onClick={() => window.print()}>打印档案</Button>
          <Button onClick={openEdit}>编辑资料</Button>
          <Button
            type="primary"
            onClick={() => history.push(`/treat/create?customerId=${customerId}`)}
          >
            发起接诊
          </Button>
        </div>
      </div>

      <div className="cu-stats">
        <div className="stat">
          <div className="k">诊疗记录</div>
          <div className="v">
            {treatTotal}
            <small>次</small>
          </div>
        </div>
        <div className="stat">
          <div className="k">累计消费</div>
          <div className="v">{summary ? <>¥{summary.amount ?? 0}</> : '—'}</div>
        </div>
        <div className="stat">
          <div className="k">持卡卡项</div>
          <div className="v">
            {activeCardCount}
            <small>张有效</small>
          </div>
        </div>
        <div className="stat">
          <div className="k">下次回访</div>
          <div className={`v${upcomingReview ? ' amber' : ' hold'}`}>{nextReviewText}</div>
        </div>
      </div>

      <div className="cu-cols">
        <section className="cu-panel">
          <h3>
            诊疗记录
            {treatTotal > 4 ? (
              <a onClick={() => history.push(`/treat/history?customerId=${customerId}`)}>
                查看全部 {treatTotal} 次
              </a>
            ) : null}
          </h3>
          {treats.length === 0 ? (
            <div className="cu-empty">暂无接诊记录，可从「发起接诊」建立第一张接诊单</div>
          ) : (
            treats.map((t) => {
              // 摘要对齐原型（医师·时长·证治取穴）：接诊单无医师/时长字段，先出 治法 + 取穴；疗程标签取 acuCourse
              const acupoints = [t.acupointLeft, t.acupointRight].filter(Boolean).join(' / ');
              const summary = [
                t.plan || '',
                acupoints ? `取穴 ${acupoints}` : '',
              ]
                .filter(Boolean)
                .join('，');
              return (
                <div className="visit" key={t.id}>
                  <div className="v-date">
                    <div className="d">{moment(t.createTime).date()}</div>
                    <div className="m">{moment(t.createTime).format('M 月')}</div>
                  </div>
                  <div className="v-body">
                    <div className="t">
                      {t.diagnose || t.acuMethod || '接诊记录'}
                      {t.acuCourse ? <span className="tag">{t.acuCourse}</span> : null}
                    </div>
                    <div className="s">{summary || t.desc || '—'}</div>
                  </div>
                </div>
              );
            })
          )}
        </section>
        <aside className="cu-panel">
          <h3>
            持卡卡项
            <a onClick={openIssue}>发卡</a>
          </h3>
          {cards.length === 0 ? (
            <div className="plan-note">暂无持卡记录，可点「发卡」为顾客登记次卡（余次与结算抵扣联动）。</div>
          ) : (
            cards.map((c) => (
              <div className="card-row" key={c.id}>
                <div className="cd-main">
                  <div className="cd-nm">{c.itemName || '卡项'}</div>
                  <div className="cd-sub">
                    已用 {(c.totalTimes ?? 0) - (c.remainingTimes ?? 0)}/{c.totalTimes ?? 0} 次
                    {c.status === 0 ? ' · 已停用' : ''}
                  </div>
                  <div className="cd-bar">
                    <i
                      style={{
                        width: `${Math.min(100, Math.round(((c.remainingTimes ?? 0) / (c.totalTimes || 1)) * 100))}%`,
                      }}
                    />
                  </div>
                </div>
                <div className="cd-ops">
                  <a onClick={() => openUsages(c)}>核销记录</a>
                  <Popconfirm
                    title={c.status === 1 ? '停用后不能用于抵扣，确认停用？' : '恢复该卡为有效？'}
                    onConfirm={async () => {
                      if (c.id) {
                        await setCardStatus(c.id, c.status === 1 ? 0 : 1);
                        message.success(c.status === 1 ? '已停用' : '已恢复');
                        await load();
                      }
                    }}
                  >
                    <a>{c.status === 1 ? '停用' : '恢复'}</a>
                  </Popconfirm>
                </div>
              </div>
            ))
          )}
          {upcomingReview ? (
            <div className="plan-note">
              <b>回访提醒 · {nextReviewText}</b>
              {reviewNoteText}
            </div>
          ) : null}
        </aside>
      </div>

      <section className="cu-panel his-panel">
        <h3>
          病史
          <span className="his-hint">过敏史与既往史逐条记录，开方/接诊时调阅</span>
        </h3>
        <div className="his-add">
          <Radio.Group
            value={hisType}
            onChange={(e) => setHisType(e.target.value as number)}
            optionType="button"
            buttonStyle="solid"
            options={[
              { label: '过敏', value: 0 },
              { label: '既往', value: 1 },
            ]}
          />
          <Input
            className="his-input"
            placeholder="如「青霉素过敏」「高血压 8 年，规律服药」"
            maxLength={200}
            value={hisText}
            onChange={(e) => setHisText(e.target.value)}
            onPressEnter={submitHistory}
          />
          <Button type="primary" loading={hisBusy} onClick={submitHistory}>
            记录
          </Button>
        </div>
        {histories.length === 0 ? (
          <div className="plan-note">暂无病史记录；有过敏史或慢性病的顾客建议先记上，开方时调阅。</div>
        ) : (
          <div className="his-list">
            {histories.map((h) => (
              <div className="his-row" key={h.id}>
                <span className={`his-tag t${h.type ?? 0}`}>{h.type === 0 ? '过敏' : '既往'}</span>
                <span className="his-content">{h.content}</span>
                <span className="his-time">{h.createTime ? moment(h.createTime).format('YYYY-MM-DD') : '—'}</span>
                <Popconfirm title="删除这条病史？" onConfirm={() => h.id && removeHistory(h.id)}>
                  <a className="his-del">删除</a>
                </Popconfirm>
              </div>
            ))}
          </div>
        )}
      </section>

      <Modal
        title="发卡"
        open={issueOpen}
        onOk={submitIssue}
        confirmLoading={issuing}
        onCancel={() => setIssueOpen(false)}
        okText="发卡"
        cancelText="取消"
      >
        <Form form={issueForm} layout="vertical">
          <Form.Item name="itemId" label="卡项（次卡）" rules={[{ required: true, message: '请选择卡项' }]}>
            <Select options={itemOptions} placeholder="选择卡项" />
          </Form.Item>
          <Form.Item
            name="totalTimes"
            label="总次数"
            initialValue={10}
            rules={[{ required: true, message: '总次数必须 ≥ 1' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`核销记录 · ${usageCard?.itemName || '次卡'}`}
        open={!!usageCard}
        footer={null}
        onCancel={() => setUsageCard(null)}
      >
        {usageLoading ? (
          <div className="plan-note">加载中…</div>
        ) : usages.length === 0 ? (
          <div className="plan-note">
            暂无核销记录；结算台选「次卡抵扣」收款后，每次抵扣在这里落一条。
          </div>
        ) : (
          <div className="usage-list">
            {usages.map((u) => (
              <div className="usage-row" key={u.id}>
                <span className="u-n">第 {u.timesUsed ?? '—'} 次</span>
                <span className="u-t">{u.createTime ? moment(u.createTime).format('YYYY-MM-DD HH:mm') : '—'}</span>
                <span className="u-s">{u.staffName ? `服务：${u.staffName}` : '服务：—'}</span>
                <span className="u-o">{u.orderId ? `订单 #${u.orderId}` : '历史补录'}</span>
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Drawer title="编辑顾客资料" width={560} open={editOpen} onClose={() => setEditOpen(false)}>
        <ProForm<API.Customer>
          formRef={formRef}
          grid
          layout={'vertical'}
          onFinish={async (values) => {
            values.id = Number(customerId);
            const result = await updateCustomer(values);
            if (result?.id) {
              message.success('更新成功');
              setEditOpen(false);
              load();
            }
            return true;
          }}
        >
          <ProFormText
            colProps={{ md: 12, xl: 12 }}
            name="name"
            label="姓名"
            tooltip="长度范围2-20字符,选填项"
            placeholder="输入顾客姓名"
            required={true}
          />
          <ProFormText colProps={{ md: 12, xl: 6 }} name="age" label="年龄" placeholder="20" />
          <ProFormSelect
            colProps={{ md: 12, xl: 6 }}
            name="gender"
            label="性别"
            options={[
              { value: 0, label: '女' },
              { value: 1, label: '男' },
            ]}
            placeholder={'男'}
          />
          <ProFormText
            colProps={{ md: 12, xl: 12 }}
            name="phone"
            label="手机号码"
            tooltip="方便按手机查找"
            placeholder="输入手机号码"
          />
          <ProFormText
            colProps={{ md: 12, xl: 12 }}
            name="level"
            label="会员等级"
            tooltip="1 普通会员 / 2 银卡会员 / 3 金卡会员"
            placeholder="1"
          />
          <ProFormDatePicker colProps={{ md: 12, xl: 12 }} label="生日" name="birthday" />
          <ProFormTextArea colProps={{ span: 24 }} name="address" label="详细的工作地址或家庭住址" />
        </ProForm>
      </Drawer>
    </PageContainer>
  );
};

export default Update;
