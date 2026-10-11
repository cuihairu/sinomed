import { EllipsisOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {Button, Dropdown, message} from 'antd';
import { history } from '@umijs/max';
import { useRef } from 'react';
import { queryCustomerByPage,deleteCustomer} from '@/services/ant-design-pro/customer';
import ScannerInput from '@/components/ScannerInput';
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

type Customer = {
  id?: number;
  name?: string;
  age?: number;
  gender?: number;
  phone?: string;
  address?: string;
  level?: number;
  birthday?: string;
  createTime?: string;
};

const columns: ProColumns<Customer>[] = [
  {
    dataIndex: 'index',
    valueType: 'indexBorder',
    width: 48,
  },
  {
    title: '名字',
    dataIndex: 'name',
    copyable: true,
    ellipsis: true,
    tip: '目前只会根据客人的名字和手机号码查询',
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
    title: '会员等级',
    dataIndex: 'level',
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
    title: '手机号码',
    dataIndex: 'phone',
    copyable: true,
    ellipsis: true,
    tip: '目前只会根据客人的名字和手机号码查询',
    formItemProps: {
      rules: [
        {
          required: true,
          message: '此项为必填项',
        },
      ],
    },
  },{
    title: '年龄',
    dataIndex: 'age',
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
    title: '性别',
    dataIndex: 'gender',
    valueType: 'select',
    valueEnum: {
      0: {text: '女'},
      1: {text: '男'}
    },
    fieldProps:{
      options:[{
        label: '女',
        value: 0
      },{
        label: '男',
        value: 1
      }],
    },
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
    title: '生日',
    key: 'birthday',
    dataIndex: 'birthday',
    valueType: 'date',
    sorter: true,
    hideInSearch: true,
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
    title: '操作',
    valueType: 'option',
    key: 'option',
    render: (text, record, _, action) => [
      <a
        key="createTreat"
        onClick={() => {
          if (record.id) {
              history.push(`/treat/create?customerId=${record.id}`);
          }else{
            message.error('记录id不存,该数据可不可以编辑');
          }
        }}
      >
        诊断
      </a>,
      <a
        key="update"
        onClick={() => {
          if (record.id) {
            history.push(`/customer/update?customerId=${record.id}`);
          }else{
            message.error('记录id不存,该数据可不可以编辑');
          }
        }}
      >
        更新
      </a>,
      <a
        key="history"
        onClick={() => {
          if (record.id) {
            history.push(`/treat/history?customerId=${record.id}`);
          }else{
            message.error('记录id不存,该数据可不可以编辑');
          }
        }}
      >
        历史
      </a>,
      <a
        key="plan"
        onClick={() => {
          if (record.id) {
            history.push(`/review/plan?customerId=${record.id}`);
          }else{
            message.error('记录id不存,该数据可不可以编辑');
          }
        }}
      >
        回访
      </a>,
      <a
        key="delete"
        onClick={() => {
          if (record.id) {
            deleteCustomer(record.id).then(() => {
                message.success('删除成功');
                action?.reload();
            });
          }else{
            message.warning('记录id不存,无法删除,请刷新再试');
          }
        }}
      >
        删除
      </a>,
    ],
  },
];

export default () => {
  const actionRef = useRef<ActionType>();
  return (
    <>
      <ScannerInput />
      <ProTable<Customer>
      columns={columns}
      actionRef={actionRef}
      cardBordered
      request={async (params = {}, sort, filter) => {
        console.log(sort, filter);
         const msg = await queryCustomerByPage(params);
         return {
           data: msg.data,
           success: true,
           total: msg.total,
         }
      }}
      editable={{
        type: 'multiple',
      }}
      columnsState={{
        persistenceKey: 'pro-table-singe-demos-Customer',
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
      headerTitle="已经查询到的客人信息:"
      toolBarRender={() => [
        <Dropdown
          key="menu"
          menu={{
            items: [
            ],
          }}
        >
          <Button>
            <EllipsisOutlined />
          </Button>
        </Dropdown>,
      ]}
    />
    </>
  );
};
