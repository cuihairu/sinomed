import React, { useState, useEffect } from 'react';
import { ProCard, StatisticCard } from '@ant-design/pro-components';
import { Table } from 'antd';
import { queryStaffTodayTimesheet } from '@/services/ant-design-pro/staff';

type Timesheet = {
  name: string;
  signType: string;
  time: string;
};
//
const TodayTimesheet: React.FC = () => {
  const [timesheetList, setTimesheetList] = useState<Timesheet[]>([]);
  const [totalHours, setTotalHours] = useState<string>('0 小时');
  const [startTime, setStartTime] = useState<string>('-');
  const [endTime, setEndTime] = useState<string>('-');
  const columns = [
    {
      title: '姓名',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '签到类型',
      dataIndex: 'signType',
      key: 'signType',
    },
    {
      title: '时间',
      dataIndex: 'time',
      key: 'time',
    },
  ];
  useEffect(() => {
    queryStaffTodayTimesheet().then((res) => {
      console.log(res);
      if (res.data) {
        const d: Timesheet[] = [];
        res.data.forEach((item: any) => {
          d.push({
            name: item.name,
            signType: item.signType === 1 ? '上班' : '下班',
            time: item.time,
          });
          console.log(d);
          setTimesheetList(d);
        });
      }
      if (res.total) {
        setTotalHours(`${res.total} 小时`);
      }
      if (res.startTime) {
        setStartTime(res.startTime);
      }
      if (res.endTime) {
        setEndTime(res.endTime);
      }
    });
  }, []);

  return (
    <ProCard split="horizontal" bordered ghost gutter={[0, 2]}>
      <ProCard title="今日考勤" headerBordered bordered colSpan={{ xs: 12, sm: 24 }}>
        <StatisticCard.Group>
          <StatisticCard
            statistic={{
              title: '今日工作时长',
              value: totalHours,
            }}
          />
          <StatisticCard.Divider type="vertical" />
          <StatisticCard
            statistic={{
              title: '上班卡',
              value: startTime,
            }}
          />
          <StatisticCard
            statistic={{
              title: '下班卡',
              value: endTime,
            }}
          />
        </StatisticCard.Group>
      </ProCard>

      <ProCard title="打卡记录" headerBordered bordered colSpan={{ xs: 12, sm: 24 }}>
        <Table columns={columns} dataSource={timesheetList} pagination={false} />
      </ProCard>
    </ProCard>
  );
};

export default TodayTimesheet;
