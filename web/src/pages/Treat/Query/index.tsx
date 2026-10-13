import { EllipsisOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable, TableDropdown } from '@ant-design/pro-components';
import { Button, Dropdown, message } from 'antd';
import { useRef } from 'react';
import { fuzzyQueryTreatByPage, deleteTreat } from '@/services/ant-design-pro/treat';
import { history } from '@umijs/max';

export const waitTimePromise = async (time: number = 100) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(true);
    }, time);
  });
};

export const waitTime = async (time: number = 100) => {
  await waitTimePromise(time);
};

type TreatInfo = {
  id?: number; //
  name?: string;
  age?: number;
  gender?: number;
  phone?: string;
  createTime?: string;
  desc?: string;
};

const columns: ProColumns<TreatInfo>[] = [
  {
    dataIndex: 'index',
    valueType: 'indexBorder',
    width: 48,
  },
  {
    title: 'ID',
    dataIndex: 'id',
    ellipsis: true,
    copyable: true,
    width: '6%',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    valueType: 'dateTime',
    ellipsis: true,
  },
  {
    title: '名字',
    dataIndex: 'name',
    ellipsis: true,
    copyable: true,
    width: '8%',
  },
  {
    title: '手机号码',
    dataIndex: 'phone',
    copyable: true,
    ellipsis: true,
  },
  {
    title: '年龄',
    dataIndex: 'age',
    ellipsis: true,
    width: '6%',
  },
  {
    title: '性别',
    dataIndex: 'gender',
    valueType: 'select',
    valueEnum: {
      0: { text: '女' },
      1: { text: '男' },
    },
    fieldProps: {
      options: [
        {
          label: '女',
          value: 0,
        },
        {
          label: '男',
          value: 1,
        },
      ],
    },
    width: '6%',
    ellipsis: true,
    formItemProps: {
      rules: [
        {
          required: true,
          message: '此项为必填项',
        },
      ],
    },
  },
  {
    title: '主述',
    dataIndex: 'desc',
    ellipsis: true,
    width: '28%',
  },
  {
    title: '创建时间',
    dataIndex: 'created_at',
    valueType: 'dateRange',
    hideInTable: true,
    search: {
      transform: (value) => {
        return {
          startTime: value[0],
          endTime: value[1],
        };
      },
    },
  },
  {
    title: '主述',
    dataIndex: 'desc',
    ellipsis: true,
    width: '20%',
    hideInTable: true,
  },
  {
    title: '操作',
    valueType: 'option',
    key: 'option',
    render: (text, record, _, action) => [
      <a
        key="update"
        onClick={() => {
          if (record.id) {
            history.push(`/treat/create?treatId=${record.id}`);
          } else {
            message.error('记录id不存,该数据可不可以编辑');
          }
        }}
      >
        更新
      </a>,
      <a
        key="delete"
        onClick={() => {
          if (record.id) {
            deleteTreat(record.id).then(() => {
              message.success('删除成功');
              action?.reload();
            });
          } else {
            message.warning('记录id不存,无法删除,请刷新再试');
          }
        }}
      >
        删除
      </a>,
      <TableDropdown key="actionGroup" onSelect={() => action?.reload()} menus={[]} />,
    ],
  },
];

export default () => {
  const actionRef = useRef<ActionType>();
  return (
    <ProTable<TreatInfo>
      columns={columns}
      actionRef={actionRef}
      cardBordered
      params={{}}
      request={async (params = {}, sort, filter) => {
        console.log(sort, filter);
        const msg = await fuzzyQueryTreatByPage(params);
        return {
          data: msg.data,
          success: true,
          total: msg.total,
        };
      }}
      editable={{
        type: 'multiple',
      }}
      columnsState={{
        persistenceKey: 'pro-table-singe-demos-TreatInfo',
        persistenceType: 'localStorage',
        onChange(value) {
          console.log('value: ', value);
        },
      }}
      rowKey="id"
      search={{
        labelWidth: 'auto',
      }}
      options={{
        setting: {
          listsHeight: 400,
        },
      }}
      form={{
        // 由于配置了 transform，提交的参与与定义的不同这里需要转化一下
        syncToUrl: (values, type) => {
          if (type === 'get') {
            return {
              ...values,
              created_at: [values.startTime, values.endTime],
            };
          }
          return values;
        },
      }}
      pagination={{
        pageSize: 10,
        onChange: (page) => console.log(page),
      }}
      dateFormatter="string"
      headerTitle="已经查询到的诊断表信息:"
      toolBarRender={() => [
        <Dropdown
          key="menu"
          menu={{
            items: [
              {
                label: '1st item',
                key: '1',
              },
            ],
          }}
        >
          <Button>
            <EllipsisOutlined />
          </Button>
        </Dropdown>,
      ]}
    />
  );
};
