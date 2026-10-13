import { PageContainer } from '@ant-design/pro-components';
import { Button, DatePicker, Spin, Table } from 'antd';
import moment from 'moment';
import { useCallback, useEffect, useState } from 'react';
import { fetchWeekTimesheet } from '@/services/ant-design-pro/staff';
import './index.less';

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/** 周班次对照：员工 × 7 天，「应」= 员工班表计划，「实」= 当日打卡（最早上班/最晚下班、整小时） */
export default () => {
  const [weekDate, setWeekDate] = useState<moment.Moment | undefined>();
  const [view, setView] = useState<API.WeekTimesheet | undefined>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (anchor?: moment.Moment) => {
    setLoading(true);
    try {
      const res = await fetchWeekTimesheet(anchor ? anchor.format('YYYY-MM-DD') : undefined);
      setView(res);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const weekStart = view?.weekStart;
  const weekEnd = weekStart ? moment(weekStart).add(6, 'days').format('YYYY-MM-DD') : '';

  const columns = [
    {
      title: '员工',
      dataIndex: 'name',
      key: 'name',
      fixed: 'left' as const,
      width: 110,
      render: (_: unknown, row: API.WeekTimesheetStaff) => (
        <span className="wt-nm">{row.name}</span>
      ),
    },
    ...WEEKDAYS.map((text, idx) => ({
      title: text,
      key: `d${idx + 1}`,
      width: 170,
      render: (_: unknown, row: API.WeekTimesheetStaff) => {
        const day = (row.days || []).find((d) => d.weekday === idx + 1);
        return (
          <div className="wt-cell">
            <div className="wt-plan">
              {day?.planStart ? (
                <span className="wt-time">
                  应 {day.planStart}–{day.planEnd}
                </span>
              ) : (
                <span className="wt-none">—</span>
              )}
            </div>
            <div className="wt-sign">
              {day?.signStart ? (
                <span className="wt-time">
                  实 {day.signStart}–{day.signEnd || '…'}
                  {day.hours != null ? `（${day.hours}h）` : ''}
                </span>
              ) : (
                <span className="wt-none">—</span>
              )}
            </div>
          </div>
        );
      },
    })),
  ];

  return (
    <PageContainer>
      <div className="wt-tip">
        取所选日期所在周（周一至周日）：「应」为员工班表计划班次，「实」为当日打卡（同日最早上班/最晚下班）；
        时长按整小时计，历史日缺下班卡按 0 小时。 weekDate 为空取本周。
      </div>
      <div className="wt-toolbar">
        <DatePicker
          value={weekDate}
          onChange={(d) => {
            setWeekDate(d || undefined);
            load(d || undefined);
          }}
          placeholder="选日期看所在周"
        />
        <Button
          onClick={() => {
            setWeekDate(undefined);
            load();
          }}
        >
          本周
        </Button>
        <span className="wt-count">{weekStart ? `${weekStart} ~ ${weekEnd}` : ''}</span>
        {loading && <Spin size="small" />}
      </div>
      <Table
        className="wt-board"
        rowKey={(row) => String(row.staffId)}
        columns={columns}
        dataSource={view?.staffs || []}
        loading={loading}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
    </PageContainer>
  );
};
