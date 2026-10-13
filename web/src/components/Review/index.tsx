import React from 'react';
import { Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';

interface Review {
  good?: string;
  improvement?: string;
}

interface ReviewTableProps {
  data: Review[];
}

const columns: ColumnsType<Review> = [
  {
    title: '做的好的事情',
    dataIndex: 'good',
    key: 'good',
    width: '50%',
  },
  {
    title: '有待改进的事情',
    key: 'improvement',
    dataIndex: 'improvement',
  },
];

const ReviewTable: React.FC<ReviewTableProps> = (props) => (
  <Table columns={columns} dataSource={props.data} />
);

export { Review };
export default ReviewTable;
