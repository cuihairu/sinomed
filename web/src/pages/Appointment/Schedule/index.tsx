import { PageContainer } from '@ant-design/pro-components';
import { Button, message } from 'antd';
import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { LeftOutlined, RightOutlined, ReloadOutlined } from '@ant-design/icons';
import moment from 'moment';
import {
  queryAppointmentPage,
  updateAppointmentStatus,
  APPOINTMENT_STATUS,
  type Appointment,
} from '@/services/ant-design-pro/appointment';
import { listCardsByCustomers } from '@/services/ant-design-pro/card';
import { queryStaffByPage } from '@/services/ant-design-pro/staff';
import { queryShiftPage } from '@/services/ant-design-pro/shift';
import './index.less';

/** 排班时段：9:00–18:00 整点（对齐设计原型 admin-booking.html 的时段网格） */
const HOURS = Array.from({ length: 10 }, (_, i) => 9 + i);
const UNASSIGNED = '未指派';

/** 网格里的短标签：已接待 = 已到店 */
const SLOT_TEXT: Record<number, string> = {
  [APPOINTMENT_STATUS.BOOKED]: '待到店',
  [APPOINTMENT_STATUS.RECEIVED]: '已到店',
  [APPOINTMENT_STATUS.NO_SHOW]: '过号',
  [APPOINTMENT_STATUS.CANCELLED]: '已取消',
};
const SLOT_CLASS: Record<number, string> = {
  [APPOINTMENT_STATUS.BOOKED]: 'wait',
  [APPOINTMENT_STATUS.RECEIVED]: 'in',
  [APPOINTMENT_STATUS.NO_SHOW]: 'pass',
  [APPOINTMENT_STATUS.CANCELLED]: 'off',
};

export default function AppointmentSchedule() {
  const [day, setDay] = useState(moment());
  const [list, setList] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(false);
  // 疗程标记：`${customerId}|${itemName}` → 第 N 疗程（同项目第 N 张卡，按购入先后；无卡不标）
  const [course, setCourse] = useState<Record<string, number>>({});
  // 医师名册（staffs.title 职务语义）：在职且职务含「医师」
  const [roster, setRoster] = useState<{ id: number; name: string }[]>([]);
  // 当日在班医师 id 集（staff_shifts 按当日星期，班表×排班联动）
  const [onDuty, setOnDuty] = useState<Set<number>>(new Set());

  const dayStr = day.format('YYYY-MM-DD');

  const load = useCallback(async (target: string) => {
    setLoading(true);
    try {
      // 排班页看整日全部预约（含已取消/过号），不做状态过滤
      const res = await queryAppointmentPage({ current: 1, pageSize: 200, date: target });
      const rows = res?.data || [];
      setList(rows);
      // 疗程标记：按顾客批量拉持卡一次拉齐；同项目第 N 张卡=第 N 疗程，无卡项不标
      const ids = Array.from(new Set(rows.map((a) => a.customerId).filter((x): x is number => !!x)));
      if (ids.length === 0) {
        setCourse({});
        return;
      }
      const cards = await listCardsByCustomers(ids);
      setCourse(
        (cards || []).reduce<Record<string, number>>((acc, c) => {
          if (!c.customerId || !c.itemName) return acc;
          const key = `${c.customerId}|${c.itemName}`;
          acc[key] = (acc[key] || 0) + 1;
          return acc;
        }, {}),
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(dayStr);
  }, [dayStr, load]);

  useEffect(() => {
    // 医师名册一次拉齐：在职（status 1）且职务含「医师」；名册为空回退「当日有约」动态列
    queryStaffByPage({ current: 1, pageSize: 200 }).then((res) => {
      setRoster(
        (res?.data || [])
          .filter((s) => s.status === 1 && !!s.title && s.title.includes('医师'))
          .map((s) => ({ id: s.id!, name: s.name || '' }))
          .filter((s) => s.name),
      );
    });
  }, []);

  useEffect(() => {
    // 班表联动：拉全量班次，取当日星期（1 周一…7 周日）在班的医师 id
    const weekday = day.isoWeekday();
    queryShiftPage({ current: 1, pageSize: 200, weekday }).then((res) => {
      setOnDuty(new Set((res?.data || []).map((s) => s.staffId!).filter(Boolean)));
    });
  }, [dayStr]);

  const move = (n: number) => setDay((d) => d.clone().add(n, 'day'));

  /** 列（医师）：名册全列（在职且职务含「医师」）∪ 本日有约指派（名册外仍可见）；未指派恒最后；名册空回退本日有约 */
  const columns = useMemo(() => {
    const booked = new Set<string>();
    list.forEach((a) => booked.add(a.staffName || UNASSIGNED));
    const names = roster.map((s) => s.name);
    if (names.length === 0) {
      return Array.from(booked).sort((a, b) =>
        a === UNASSIGNED ? 1 : b === UNASSIGNED ? -1 : a.localeCompare(b),
      );
    }
    // 在班医师排前（班表联动），名册其余医师随后，当日有约名册外兜底，未指派恒最后
    const onDutyNames = roster
      .filter((s) => onDuty.has(s.id))
      .map((s) => s.name)
      .sort((a, b) => a.localeCompare(b));
    const offDutyNames = roster
      .filter((s) => !onDuty.has(s.id))
      .map((s) => s.name)
      .sort((a, b) => a.localeCompare(b));
    const extra = Array.from(booked)
      .filter((n) => n !== UNASSIGNED && !names.includes(n))
      .sort((a, b) => a.localeCompare(b));
    return [
      ...onDutyNames,
      ...offDutyNames,
      ...extra,
      ...(booked.has(UNASSIGNED) ? [UNASSIGNED] : []),
    ];
  }, [list, roster, onDuty]);

  /** 格位索引：`${hh}|${staffName}` → 预约 */
  const cell = useMemo(() => {
    const map: Record<string, Appointment> = {};
    list.forEach((a) => {
      if (!a.startTime) return;
      const hh = moment(a.startTime).hour();
      const key = `${hh}|${a.staffName || UNASSIGNED}`;
      // 同格多条（历史冲突数据）取第一条，其余在详情里看
      if (!map[key]) map[key] = a;
    });
    return map;
  }, [list]);

  const totalInCell = useMemo(() => {
    const map: Record<string, number> = {};
    list.forEach((a) => {
      if (!a.startTime) return;
      const key = `${moment(a.startTime).hour()}|${a.staffName || UNASSIGNED}`;
      map[key] = (map[key] || 0) + 1;
    });
    return map;
  }, [list]);

  /** 右栏「今日预约」四格：总数 / 已到店 / 待到店 / 过号（对齐原型 stat4） */
  const counts = useMemo(() => {
    const c = { total: list.length, in: 0, wait: 0, pass: 0 };
    list.forEach((a) => {
      if (a.status === APPOINTMENT_STATUS.RECEIVED) c.in += 1;
      else if (a.status === APPOINTMENT_STATUS.BOOKED) c.wait += 1;
      else if (a.status === APPOINTMENT_STATUS.NO_SHOW) c.pass += 1;
    });
    return c;
  }, [list]);

  /** 右栏「待到店」行：按开时段先后排 */
  const pending = useMemo(
    () =>
      list
        .filter((a) => a.status === APPOINTMENT_STATUS.BOOKED)
        .sort((x, y) => (x.startTime || '').localeCompare(y.startTime || '')),
    [list],
  );

  const receive = async (a: Appointment) => {
    if (!a.id) return;
    const res = await updateAppointmentStatus({ id: a.id, status: APPOINTMENT_STATUS.RECEIVED });
    if (res?.id) {
      message.success('已接待，可发起接诊');
      load(dayStr);
    }
  };

  return (
    <PageContainer
      title="排班视图"
      content="按日看医师×时段占用：建约留空档，到店在此一键接待。每时段限约 1 位由后端校验（同医师同日同小时），一格多条为历史数据，取第一条。"
    >
      <div className="sch-toolbar">
        <div className="sch-nav">
          <Button type="text" icon={<LeftOutlined />} onClick={() => move(-1)} title="前一天" />
          <span className="d">{day.format('YYYY-MM-DD dddd')}</span>
          <Button type="text" icon={<RightOutlined />} onClick={() => move(1)} title="后一天" />
          <Button size="small" onClick={() => setDay(moment())} disabled={day.isSame(moment(), 'day')}>
            今天
          </Button>
          <Button
            type="text"
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => load(dayStr)}
            title="刷新"
          />
        </div>
      </div>

      <div className="sch-cols">
        <div className="sch-grid-wrap">
          <div
            className="sch-grid"
            style={{ gridTemplateColumns: `64px repeat(${Math.max(columns.length, 1)}, 1fr)` }}
          >
            <span className="hd tm-hd" />
            {columns.length === 0 ? (
              <span className="hd">——</span>
            ) : (
              columns.map((name) => {
                const staff = roster.find((s) => s.name === name);
                const offduty = !!staff && !onDuty.has(staff.id);
                return (
                  <span
                    className={`hd${name === UNASSIGNED ? ' unassigned' : ''}${offduty ? ' offduty' : ''}`}
                    key={name}
                    title={offduty ? '今日无班次（班表联动）' : undefined}
                  >
                    {name}
                  </span>
                );
              })
            )}

            {HOURS.map((h) => (
              <Fragment key={h}>
                <span className="tm">{String(h).padStart(2, '0')}:00</span>
                {(columns.length === 0 ? [UNASSIGNED] : columns).map((name) => {
                  const a = cell[`${h}|${name}`];
                  if (!a) {
                    return (
                      <div className="slot empty" key={`${h}-${name}`}>
                        可预约
                      </div>
                    );
                  }
                  const more = (totalInCell[`${h}|${name}`] || 1) - 1;
                  const off = a.status === APPOINTMENT_STATUS.CANCELLED;
                  return (
                    <div className={`slot${off ? ' off' : ''}`} key={`${h}-${name}`}>
                      <div className="bk">
                        <div className="n">
                          {a.customerName || `顾客${a.customerId}`}
                          <span className={`st ${SLOT_CLASS[a.status ?? 0]}`}>{SLOT_TEXT[a.status ?? 0]}</span>
                          {more > 0 ? <span className="more">+{more}</span> : null}
                        </div>
                        <div className="s">
                          {a.itemName || '到店再定'}
                          {a.customerId && a.itemName && course[`${a.customerId}|${a.itemName}`] ? (
                            <span className="course"> · 第{course[`${a.customerId}|${a.itemName}`]}疗程</span>
                          ) : null}
                        </div>
                      </div>
                      {a.status === APPOINTMENT_STATUS.BOOKED ? (
                        <button type="button" className="go" onClick={() => receive(a)}>
                          接待
                        </button>
                      ) : null}
                    </div>
                  );
                })}
              </Fragment>
            ))}
          </div>
          <div className="sch-legend">
            {Object.entries(SLOT_TEXT).map(([k, text]) => (
              <span key={k} className={`lg ${SLOT_CLASS[Number(k)]}`}>
                {text}
              </span>
            ))}
            <span className="lg-empty">虚线 = 可预约空档</span>
          </div>
        </div>

        <div className="sch-stack">
          <section className="sch-panel">
            <h3>今日预约</h3>
            <div className="stat4">
              <div className="s">
                <div className="k">预约总数</div>
                <div className="v">{counts.total}</div>
              </div>
              <div className="s">
                <div className="k">已到店</div>
                <div className="v">{counts.in}</div>
              </div>
              <div className="s">
                <div className="k">待到店</div>
                <div className="v gd">{counts.wait}</div>
              </div>
              <div className="s">
                <div className="k">过号</div>
                <div className="v">{counts.pass}</div>
              </div>
            </div>
          </section>

          <section className="sch-panel">
            <h3>
              待到店<span className="hint">到店后一键转接诊</span>
            </h3>
            {pending.length === 0 ? (
              <div className="sch-none">本日暂无待到店预约</div>
            ) : (
              pending.map((a) => (
                <div className="rv" key={a.id}>
                  <span className="tm">{a.startTime ? moment(a.startTime).format('HH:mm') : '--'}</span>
                  <span className="n">{a.customerName || `顾客${a.customerId}`}</span>
                  <span className="it">
                    {a.itemName || '到店再定'}
                    {a.customerId && a.itemName && course[`${a.customerId}|${a.itemName}`] ? (
                      <span className="course"> · 第{course[`${a.customerId}|${a.itemName}`]}疗程</span>
                    ) : null}
                  </span>
                  <span className="pill">{a.staffName || UNASSIGNED}</span>
                  <button type="button" className="go" onClick={() => receive(a)}>
                    接待
                  </button>
                </div>
              ))
            )}
          </section>

          <section className="sch-panel">
            <h3>
              小程序联动<span className="hint">规划</span>
            </h3>
            <div className="sch-note">
              顾客可在小程序自助约时段、改期与取消；到店扫码打卡自动转「已到店」并进入接诊队列。
            </div>
          </section>
        </div>
      </div>
    </PageContainer>
  );
}
