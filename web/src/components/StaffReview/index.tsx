import React from 'react';
import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';

interface StaffReview {
  name?: string;
  cost?: number;
  done?: string;
  advice?: string;
}

interface StaffReviewTableProps {
  data: StaffReview[];
}

const columns: ColumnsType<StaffReview> = [
  {
    title: '姓名',
    dataIndex: 'name',
    key: 'name',
    render: (text) => <a>{text}</a>,
  },
  {
    title: '耗卡',
    dataIndex: 'cost',
    key: 'cost',
  },
  {
    title: '卡项已有/已做',
    dataIndex: 'done',
    key: 'done',
  },
  {
    title: '成单/建议',
    key: 'advice',
    dataIndex: 'advice',
  },
];

const StaffReviewTable: React.FC<StaffReviewTableProps> = (props) => (
  <Table columns={columns} dataSource={props.data} />
);

export { StaffReview };
export default StaffReviewTable;
