import { PageContainer } from '@ant-design/pro-components';
import React, { useEffect, useState } from 'react';
import { DatePicker, Spin, message } from 'antd';
import moment from 'moment';
import {
  fetchSettlementMonthReport,
  fetchSettlementTrend,
  type SettlementMonthReport,
  type SettlementTrendRow,
} from '@/services/ant-design-pro/billing';
import './index.less';

/** 月度收费报表：按结算时间聚合当月结算单；次卡核销实收 0，单列不计入实收 */
const MonthDashboard: React.FC = () => {
  const [month, setMonth] = useState<moment.Moment>(moment());
  const [report, setReport] = useState<SettlementMonthReport>();
  const [loading, setLoading] = useState(false);
  /** 经营趋势：近 6 月（含当月），与所选月份无关，进页一次 */
  const [trend, setTrend] = useState<SettlementTrendRow[]>([]);

  useEffect(() => {
    let alive = true;
    fetchSettlementTrend(6)
      .then((data) => {
        if (alive) {
          setTrend(data?.rows || []);
        }
      })
      .catch(() => {
        if (alive) {
          setTrend([]);
        }
      });
    return () => {
      alive = false;
    };
  }, []);

  const maxTrendMoney = Math.max(1, ...trend.map((r) => r.money ?? 0));
  const maxTreatCount = Math.max(1, ...trend.map((r) => r.treatCount ?? 0));

  useEffect(() => {
    let alive = true;
    setLoading(true);
    fetchSettlementMonthReport(month.format('YYYY-MM'))
      .then((data) => {
        if (alive) {
          setReport(data);
        }
      })
      .catch(() => {
        if (alive) {
          setReport(undefined);
          message.warning('报表加载失败');
        }
      })
      .finally(() => {
        if (alive) {
          setLoading(false);
        }
      });
    return () => {
      alive = false;
    };
  }, [month]);

  return (
    <PageContainer
      title="月度收费报表"
      content="按结算时间聚合当月结算单：逐日单数与实收、支付方式构成；次卡核销实收为 0、单列次数。数据来自收费结算，储值充值本身不计入营收。"
    >
      <div className="mo-report">
        <div className="pick">
          <DatePicker
            picker="month"
            allowClear={false}
            value={month}
            onChange={(v) => v && setMonth(v)}
          />
        </div>
        <Spin spinning={loading}>
          <div className="stats">
            <div className="stat">
              <span className="k">当月实收</span>
              <span className="v amber">
                ¥{report?.totalMoney ?? 0}
                <small>元</small>
              </span>
            </div>
            <div className="stat">
              <span className="k">结算单</span>
              <span className="v">
                {report?.totalCount ?? 0}
                <small>单</small>
              </span>
            </div>
            <div className="stat">
              <span className="k">次卡核销</span>
              <span className="v">
                {report?.cardCount ?? 0}
                <small>次（实收 0）</small>
              </span>
            </div>
          </div>

          <div className="panel">
            <h3>近 6 月趋势（实收 · 接诊）</h3>
            {trend.length === 0 ? (
              <div className="empty">暂无趋势数据</div>
            ) : (
              <div className="trend-bars">
                {trend.map((r, idx) => (
                  <div className={`bar${idx === trend.length - 1 ? ' on' : ''}`} key={r.month}>
                    <span className="money">¥{r.money ?? 0}</span>
                    <div className="pole">
                      <i
                        className="m"
                        style={{ height: `${Math.max(3, Math.round(((r.money ?? 0) / maxTrendMoney) * 72))}px` }}
                      />
                      <i
                        className="t"
                        style={{ height: `${Math.max(3, Math.round(((r.treatCount ?? 0) / maxTreatCount) * 40))}px` }}
                      />
                    </div>
                    <span className="mo">
                      {(r.month || '').slice(2)}
                      <br />
                      {r.treatCount ?? 0} 诊 / {r.count ?? 0} 单
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="panel">
            <h3>支付方式构成</h3>
            {(report?.payTypes?.length ?? 0) === 0 ? (
              <div className="empty">当月暂无结算单</div>
            ) : (
              <div className="pays">
                {report!.payTypes!.map((p) => (
                  <div className="pay" key={p.payType}>
                    <span className="tag">{p.payTypeText}</span>
                    <span className="num">¥{p.money ?? 0}</span>
                    <span className="cnt">{p.count} 单</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="panel">
            <h3>逐日明细</h3>
            {(report?.days?.length ?? 0) === 0 ? (
              <div className="empty">当月暂无结算单</div>
            ) : (
              <div className="days">
                {report!.days!.map((d) => (
                  <div className="day" key={d.day}>
                    <span className="d">{d.day}</span>
                    <span className="c">{d.count} 单</span>
                    <span className="m">¥{d.money ?? 0}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="note">
            演示口径：微信/支付宝仅记录支付方式，不拉起真实收银台；次卡核销单实收记 0（抵扣即收费）。
          </div>
        </Spin>
      </div>
    </PageContainer>
  );
};

export default MonthDashboard;
