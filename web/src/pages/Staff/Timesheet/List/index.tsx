import type { ProColumns } from '@ant-design/pro-components';
import { ProCard, ProTable } from '@ant-design/pro-components';
import React, { useState } from 'react';
import { fetchMouthTimesheet } from '@/services/ant-design-pro/staff';
import { message } from 'antd';

export type Timesheet = {
  staffId?: number;
  name?: string; // 姓名
  total?: number; // 总计
  1?: string;
  2?: string; // 2
  3?: string; // 3
  4?: string;
  5?: string;
  6?: string;
  7?: string;
  8?: string;
  9?: string;
  10?: string;
  11?: string;
  12?: string;
  13?: string;
  14?: string;
  15?: string;
  16?: string;
  17?: string;
  18?: string;
  19?: string;
  20?: string;
  21?: string;
  22?: string;
  23?: string;
  24?: string;
  25?: string;
  26?: string;
  27?: string;
  28?: string;
  29?: string;
  30?: string;
  31?: string; // 1
};

interface TimesheetProps {
  month: number;
  onChange: (month: number) => void;
}

const TimesheetTable: React.FC<TimesheetProps> = (props) => {
  const { month, onChange } = props;

  const columns: ProColumns<Timesheet>[] = [
    {
      title: '名字',
      key: 'name',
      dataIndex: 'name',
    },
    {
      title: '总计',
      key: 'total',
      dataIndex: 'total',
    },
    {
      title: '1',
      key: '1',
      dataIndex: '1',
    },
    {
      title: '2',
      key: '2',
      dataIndex: '2',
    },
    {
      title: '3',
      key: '3',
      dataIndex: '3',
    },
    {
      title: '4',
      key: '4',
      dataIndex: '4',
    },
    {
      title: '5',
      key: '5',
      dataIndex: '5',
    },
    {
      title: '6',
      key: '6',
      dataIndex: '6',
    },
    {
      title: '7',
      key: '7',
      dataIndex: '7',
    },
    {
      title: '8',
      key: '8',
      dataIndex: '8',
    },
    {
      title: '9',
      key: '9',
      dataIndex: '9',
    },
    {
      title: '10',
      key: '10',
      dataIndex: '10',
    },
    {
      title: '11',
      key: '11',
      dataIndex: '11',
    },
    {
      title: '12',
      key: '12',
      dataIndex: '12',
    },
    {
      title: '13',
      key: '13',
      dataIndex: '13',
    },
    {
      title: '14',
      key: '14',
      dataIndex: '14',
    },
    {
      title: '15',
      key: '15',
      dataIndex: '15',
    },
    {
      title: '16',
      key: '16',
      dataIndex: '16',
    },
    {
      title: '17',
      key: '17',
      dataIndex: '17',
    },
    {
      title: '18',
      key: '18',
      dataIndex: '18',
    },
    {
      title: '19',
      key: '19',
      dataIndex: '19',
    },
    {
      title: '20',
      key: '20',
      dataIndex: '20',
    },
    {
      title: '21',
      key: '21',
      dataIndex: '21',
    },
    {
      title: '22',
      key: '22',
      dataIndex: '22',
    },
    {
      title: '23',
      key: '23',
      dataIndex: '23',
    },
    {
      title: '24',
      key: '24',
      dataIndex: '24',
    },
    {
      title: '25',
      key: '25',
      dataIndex: '25',
    },
    {
      title: '26',
      key: '26',
      dataIndex: '26',
    },
    {
      title: '27',
      key: '27',
      dataIndex: '27',
    },
    {
      title: '28',
      key: '28',
      dataIndex: '28',
    },
    {
      title: '29',
      key: '29',
      dataIndex: '29',
    },
    {
      title: '30',
      key: '30',
      dataIndex: '30',
    },
    {
      title: '31',
      key: '31',
      dataIndex: '31',
    },
  ];
  return (
    <ProTable<Timesheet>
      scroll={{ x: 'max-content' }}
      headerTitle={`上班签到表${month}月`}
      columns={columns}
      params={{
        month,
      }}
      //dataSource={data}
      request={async (params, sorter, filter) => {
        // 表单搜索项会从 params 传入，传递给后端接口。
        console.log(params, sorter, filter);
        const res = await fetchMouthTimesheet(params.month);
        console.log(res);
        const ret: Timesheet[] = [];
        if (res?.data) {
          for (let i = 0; i < res.data.length; i++) {
            const staffSheet = res.data[i];
            let d: Timesheet = {
              name: staffSheet.name,
              total: staffSheet.total,
              staffId: staffSheet.staffId,
            };
            const data = staffSheet.data || [];
            for (let j = 0; j < data.length; j++) {
              const daySheet = data[j];
              if (daySheet?.day) {
                // @ts-ignore
                d[`${daySheet.day}`] = `${daySheet.startTime}~${daySheet.endTime}`;
              }
            }
            ret.push(d);
          }
        }
        return {
          data: ret,
          success: true,
        };
      }}
      rowKey="name"
      toolbar={{
        multipleLine: true,
        search: {
          onSearch: (value) => {
            const m = parseInt(value);
            if (isNaN(m) || m > 12 || m < 1) {
              message.error('请输入正确的月份，比如6');
            } else {
              onChange(m);
            }
          },
        },
        actions: [],
      }}
      options={false}
      pagination={false}
      search={false}
      onRow={(record) => {
        return {
          onClick: () => {
            if (record.name) {
              console.log(record.name);
            }
          },
        };
      }}
    />
  );
};

const TimesheetMonthList: React.FC = () => {
  const date = new Date();
  const currentMonth = date.getMonth() + 1;
  const [month, setMonth] = useState(currentMonth);

  return (
    <ProCard split="vertical">
      <ProCard ghost bordered headerBordered headStyle={{ textAlign: 'center' }}>
        <TimesheetTable
          onChange={(m) => {
            setMonth(m);
          }}
          month={month}
        />
      </ProCard>
    </ProCard>
  );
};

export default TimesheetMonthList;
