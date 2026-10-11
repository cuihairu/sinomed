import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Input, InputNumber, Modal, Tag, message } from 'antd';
import { CheckCircleFilled, PrinterOutlined, ReloadOutlined } from '@ant-design/icons';
import moment from 'moment';
import { Order, ORDER_STATUS, orderBusinessNo, queryOrderPage, updateOrderRemark } from '@/services/ant-design-pro/order';
import {
  PAY_TYPE,
  PAY_TYPE_TEXT,
  PAY_ICON_BG,
  Recharge,
  Settlement,
  createRecharge,
  fetchRechargeBalance,
  querySettlementPage,
  receiptPrintUrl,
  refundSettlement,
  settleOrder,
} from '@/services/ant-design-pro/billing';
import { listCardsByCustomer, type Card } from '@/services/ant-design-pro/card';
import './index.less';

const maskPhone = (p?: string) => (p ? p.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '');

/** 队列「谁 · 什么项目」：明细行「名 ×数量」顿号串（存量单回退一行） */
const itemsBrief = (o: Order) => {
  const rows = o.items || [];
  if (rows.length === 0) return o.itemName || '';
  return rows.map((r) => `${r.itemName || `卡项${r.itemId}`} ×${r.qty ?? 1}`).join('、');
};

/**
 * 小票接口需登录（api.md 口径），window.open 裸开新窗口带不上 Bearer；
 * 改为点击时先开窗口（避开弹窗拦截），再带 token 拉 HTML 写入。
 */
const openReceipt = async (id: number) => {
  const win = window.open('', '_blank');
  if (!win) return;
  try {
    const token = localStorage.getItem('jwt');
    const res = await fetch(receiptPrintUrl(id), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const html = await res.text();
    win.document.open();
    win.document.write(html);
    win.document.close();
  } catch {
    win.close();
    message.error('小票加载失败，请重试');
  }
};

const Settle: React.FC = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [settlements, setSettlements] = useState<Settlement[]>([]);
  const [selected, setSelected] = useState<Order | undefined>();
  const [balance, setBalance] = useState<number | undefined>();
  const [payType, setPayType] = useState<number>(PAY_TYPE.STORED_VALUE);
  const [rechargeAmount, setRechargeAmount] = useState<number>();
  const [busy, setBusy] = useState(false);
  /** 选中订单的顾客对该订单卡项的有效余次（无卡/停用为 0） */
  const [cardRest, setCardRest] = useState<number | undefined>();
  /** 刚收完的结算单：右栏给「打印小票」入口 */
  const [done, setDone] = useState<{ id: number; money: number; pay: string } | undefined>();
  /** 整单备注（琥珀备忘块）：本地草稿，失焦写服务端 */
  const [remark, setRemark] = useState('');
  /** 退单弹窗：目标结算单 + 退款理由（必填 ≤100 字，原路退回单向） */
  const [refunding, setRefunding] = useState<Settlement | undefined>();
  const [refundReason, setRefundReason] = useState('');
  const [refundBusy, setRefundBusy] = useState(false);

  const loadQueue = async () => {
    const [queue, recent] = await Promise.all([
      queryOrderPage({ current: 1, pageSize: 50, pending: true }),
      querySettlementPage({ current: 1, pageSize: 8 }),
    ]);
    setOrders(queue?.data || []);
    setSettlements(recent?.data || []);
  };

  useEffect(() => {
    loadQueue();
  }, []);

  const choose = async (order: Order) => {
    setSelected(order);
    setRechargeAmount(undefined);
    setCardRest(undefined);
    setDone(undefined);
    setRemark(order.remark || '');
    if (order.customerId) {
      const b = await fetchRechargeBalance(order.customerId);
      setBalance(b?.balance ?? 0);
      const cards: Card[] = (await listCardsByCustomer(order.customerId)) || [];
      const hit = cards.find(
        (c) => c.itemId === order.itemId && c.status === 1 && (c.remainingTimes ?? 0) > 0,
      );
      setCardRest(hit?.remainingTimes ?? 0);
    } else {
      setCardRest(0);
    }
  };

  /** 整单备注：失焦写入（≤200 字，服务端校验） */
  const saveRemark = async (text: string) => {
    if (!selected?.id || !text || text === (selected.remark || '')) return;
    const saved = await updateOrderRemark({ id: selected.id, remark: text.slice(0, 200) });
    if (saved?.id) {
      setRemark(saved.remark || '');
      setOrders(orders.map((o) => (o.id === selected.id ? { ...o, remark: saved.remark } : o)));
      message.success('备注已保存');
    }
  };

  /** 退单：原路退回、订单回退可重收（理由必填，单向不可撤销） */
  const doRefund = async () => {
    if (!refunding?.id) return;
    const reason = refundReason.trim();
    if (!reason) {
      message.warning('请填写退款理由');
      return;
    }
    setRefundBusy(true);
    try {
      await refundSettlement(refunding.id, reason);
      message.success(`已退款 S-${refunding.id}，订单已回退待结算`);
      setRefunding(undefined);
      setRefundReason('');
      await loadQueue();
    } finally {
      setRefundBusy(false);
    }
  };

  const price = selected?.price ?? 0;
  const storedShort = payType === PAY_TYPE.STORED_VALUE && balance !== undefined && balance < price;
  const need = Math.max(price - (balance ?? 0), 0);

  const pay = async () => {
    if (!selected?.id || !selected.customerId) return;
    setBusy(true);
    try {
      if (storedShort) {
        const amount = rechargeAmount ?? need;
        if (!amount || amount <= 0) {
          message.warning('请填写充值金额');
          return;
        }
        const recharged: Recharge | undefined = await createRecharge({
          customerId: selected.customerId,
          money: amount,
        });
        if (!recharged?.id) return;
        const refreshed = await fetchRechargeBalance(selected.customerId);
        setBalance(refreshed?.balance ?? 0);
        if ((refreshed?.balance ?? 0) < price) {
          message.warning('充值后余额仍不足，请补足或改用其他支付');
          return;
        }
      }
      const receipt = await settleOrder({ orderId: selected.id, payType });
      if (receipt?.id) {
        message.success(`已收款 ¥${receipt.money}（${PAY_TYPE_TEXT[payType]}）`);
        setSelected(undefined);
        setBalance(undefined);
        setRechargeAmount(undefined);
        setDone({ id: receipt.id, money: receipt.money ?? 0, pay: PAY_TYPE_TEXT[payType] });
        await loadQueue();
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageContainer
      title="结算台"
      content="左侧为待结算队列（自助机/前台单据），选单后选支付方式收款；储值支付自动扣减余额；次卡抵扣需顾客持本单卡项的有效余次卡。"
    >
      <div className="bl-grid">
        <div className="bl-left">
          <section className="bl-panel">
            <h3>
              待结算队列<span className="hint">{orders.length} 单待收</span>
              <Button type="text" size="small" className="refresh" onClick={loadQueue} icon={<ReloadOutlined />} />
            </h3>
            {orders.length === 0 && <div className="bl-empty">队列已清空——自助机 / 前台新单会出现在这里</div>}
            {orders.map((o) => (
              <div
                key={o.id}
                className={`q-row ${selected?.id === o.id ? 'on' : ''}`}
                onClick={() => choose(o)}
              >
                <span className="oid">{orderBusinessNo(o)}</span>
                <span className="src">{o.staffId == null ? '自助机' : '前台'}</span>
                <span className="who">
                  <b>{o.customerName}</b>
                  <i>{itemsBrief(o)}</i>
                </span>
                <span className="amt">¥{o.price}</span>
                <span className={`st ${o.status === ORDER_STATUS.CONFIRMED ? 'serving' : 'wait'}`}>
                  {o.status === ORDER_STATUS.CONFIRMED ? '接待中' : '待接待'}
                </span>
              </div>
            ))}
          </section>

          <section className="bl-panel">
            <h3>最近结算</h3>
            {settlements.length === 0 && <div className="bl-empty">暂无结算记录</div>}
            {settlements.map((s) => (
              <div key={s.id} className="s-row">
                <span className="oid">S-{s.id}</span>
                <span className="who">
                  <b>{s.customerName}</b>
                </span>
                <span className="pay">{PAY_TYPE_TEXT[s.payType ?? 0]}</span>
                <span className="amt">¥{s.money}</span>
                <span className="tm">{s.createTime ? moment(s.createTime).format('MM-DD HH:mm') : ''}</span>
                {s.refunded ? (
                  <Tag className="s-refunded">已退款</Tag>
                ) : (
                  <Button
                    type="text"
                    size="small"
                    className="s-refund"
                    onClick={() => {
                      setRefundReason('');
                      setRefunding(s);
                    }}
                  >
                    退款
                  </Button>
                )}
                <Button
                  type="text"
                  size="small"
                  className="s-print"
                  icon={<PrinterOutlined />}
                  title="补打小票"
                  onClick={() => openReceipt(s.id ?? 0)}
                />
              </div>
            ))}
          </section>
        </div>

        <div className="bl-right">
          <section className="bl-due">
            {!selected && done && (
              <div className="done">
                <CheckCircleFilled className="ok" />
                <div className="tt">已收款 ¥{done.money}（{done.pay}）</div>
                <div className="op">
                  <Button
                    icon={<PrinterOutlined />}
                    onClick={() => openReceipt(done.id)}
                  >
                    打印小票
                  </Button>
                  <Button type="text" onClick={() => setDone(undefined)}>
                    继续收款
                  </Button>
                </div>
                <div className="hp">80mm 版式，浏览器新窗口打开即可打印；历史小票可在左侧「最近结算」补打。</div>
              </div>
            )}
            {!selected && !done && <div className="bl-empty tall">从左侧队列选一单开始收款</div>}
            {selected && (
              <>
                <div className="head">
                  <div className="avatar">{(selected.customerName || '?').slice(0, 1)}</div>
                  <div className="who">
                    <div className="nm">{selected.customerName}</div>
                    <div className="mt">
                      {maskPhone(selected.customerPhone)} · 单 {orderBusinessNo(selected)} ·{' '}
                      {selected.createTime ? moment(selected.createTime).format('HH:mm') : ''} 下单
                    </div>
                  </div>
                  <div className="bal">
                    <div className="k">储值卡余额</div>
                    <div className="v">¥{balance ?? '—'}</div>
                  </div>
                </div>

                {/* 结算明细多行表（项目/数量/小计），次卡抵扣行实收 ¥0 */}
                <table className="lines">
                  <thead>
                    <tr>
                      <th>项目</th>
                      <th className="c">数量</th>
                      <th className="r">小计</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(selected.items?.length
                      ? selected.items
                      : [{ itemName: selected.itemName, price: selected.price, qty: 1, subtotal: selected.price }]
                    ).map((r, i) => (
                      <tr key={i}>
                        <td>{r.itemName || '-'}</td>
                        <td className="c">×{r.qty ?? 1}</td>
                        <td className="r">¥{r.subtotal ?? 0}</td>
                      </tr>
                    ))}
                    {payType === PAY_TYPE.CARD && (cardRest ?? 0) > 0 && (
                      <tr className="free">
                        <td>卡项抵扣（次卡扣 1 次）</td>
                        <td className="c">1</td>
                        <td className="r">¥0</td>
                      </tr>
                    )}
                  </tbody>
                </table>

                {/* 整单备注（琥珀备忘块，失焦保存） */}
                <Input.TextArea
                  className="memo"
                  value={remark}
                  onChange={(e) => setRemark(e.target.value)}
                  onBlur={(e) => saveRemark(e.target.value)}
                  placeholder="整单备注：交代事项、约定、特殊要求（失焦保存，≤200 字）"
                  maxLength={200}
                  autoSize={{ minRows: 2, maxRows: 4 }}
                />

                <div className="k">应收合计</div>
                <div className="total">¥{price}</div>
                <div className="sub">
                  {payType === PAY_TYPE.CARD && (cardRest ?? 0) > 0
                    ? '卡项抵扣 ¥0 实收已含；支付后余次 -1'
                    : payType === PAY_TYPE.STORED_VALUE && balance !== undefined
                      ? `本单拟用储值支付，支付后余额 ¥${Math.max(balance - price, 0)}`
                      : '到店收银，支付方式仅作记录'}
                </div>

                <div className="pays">
                  {[PAY_TYPE.STORED_VALUE, PAY_TYPE.WECHAT, PAY_TYPE.ALIPAY, PAY_TYPE.CASH, PAY_TYPE.CARD].map((t) => (
                    <div
                      key={t}
                      className={`pay ${payType === t ? 'on' : ''} ${t === PAY_TYPE.STORED_VALUE && storedShort ? 'short' : ''}`}
                      onClick={() => setPayType(t)}
                    >
                      <span className="pi" style={{ background: PAY_ICON_BG[t] }}>
                        {PAY_TYPE_TEXT[t].slice(0, 1)}
                      </span>
                      <div>
                        <div className="pn">{PAY_TYPE_TEXT[t]}</div>
                        <div className="ps">
                          {t === PAY_TYPE.STORED_VALUE
                            ? balance !== undefined
                              ? `余额 ¥${balance}`
                              : '查询中'
                            : t === PAY_TYPE.CASH
                              ? '收银找零'
                              : t === PAY_TYPE.CARD
                                ? cardRest === undefined
                                  ? '查询中'
                                  : cardRest > 0
                                    ? `扣 1 次 · 余 ${cardRest} 次`
                                    : '无有效次卡'
                                : '记录支付方式'}
                        </div>
                      </div>
                      <span className="ck" />
                    </div>
                  ))}
                </div>

                {storedShort && (
                  <div className="recharge">
                    <div className="tip">
                      储值余额不足（差 <b>¥{need}</b>），先充值再一并收款：
                    </div>
                    <div className="row">
                      <InputNumber
                        min={1}
                        max={100000}
                        precision={0}
                        value={rechargeAmount ?? need}
                        onChange={(v) => setRechargeAmount(v ?? undefined)}
                        addonBefore="充值 ¥"
                      />
                      {[need || 500, 500, 1000]
                        .filter((v, i, arr) => v > 0 && arr.indexOf(v) === i)
                        .map((v) => (
                          <Button key={v} size="small" onClick={() => setRechargeAmount(v)}>
                            {v}
                          </Button>
                        ))}
                    </div>
                  </div>
                )}

                <Button type="primary" className="btn-pay" loading={busy} onClick={pay}>
                  {storedShort ? `充值 ¥${rechargeAmount ?? need} 并收款 ¥${price}` : `收款 ¥${price}`}
                </Button>
                <div className="note">演示口径：微信/支付宝仅记录支付方式；次卡抵扣扣 1 次、实收记 0（需顾客持本单卡项的有效余次卡）；收款后可打印 80mm 小票，历史小票可补打。</div>
              </>
            )}
          </section>
        </div>
      </div>

      <Modal
        title={refunding ? `退单确认 · S-${refunding.id}` : '退单确认'}
        open={refunding != null}
        onCancel={() => setRefunding(undefined)}
        confirmLoading={refundBusy}
        okText="确认退款"
        okButtonProps={{ disabled: !refundReason.trim() }}
        onOk={doRefund}
        width={420}
      >
        {refunding ? (
          <div className="refund-confirm">
            <div className="line">
              {refunding.customerName || '-'}
              <span className="sub">
                {PAY_TYPE_TEXT[refunding.payType ?? 0]} · ¥{refunding.money}
              </span>
            </div>
            <Input.TextArea
              value={refundReason}
              onChange={(e) => setRefundReason(e.target.value)}
              placeholder="退款理由（必填，退款必须能答「为什么」）"
              maxLength={100}
              showCount
              autoSize={{ minRows: 2, maxRows: 4 }}
            />
            <div className="hp">原路退回：储值补回余额、次卡回补次数、现金当面退回；订单回退后可重新收款。单向操作，不可撤销。</div>
          </div>
        ) : null}
      </Modal>
    </PageContainer>
  );
};

export default Settle;
