import React from 'react';
import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';

interface CustomerReview {
  name?: string;
  cost?: number;
  done?: string;
  advice?: string;
}

interface CustomerReviewTableProps {
  data: CustomerReview[];
}

const columns: ColumnsType<CustomerReview> = [
  {
    title: '姓名',
    dataIndex: 'name',
    key: 'name',
    render: (text) => <a>{text}</a>,
  },
  {
    title: '最近访问',
    dataIndex: 'last',
    key: 'last',
  },
  {
    title: '反馈/建议',
    key: 'advice',
    dataIndex: 'advice',
  },
];

const CustomerReviewTable: React.FC<CustomerReviewTableProps> = (props) => (
  <Table columns={columns} dataSource={props.data} />
);

export { CustomerReview };
export default CustomerReviewTable;
