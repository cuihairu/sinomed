import { EllipsisOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable, TableDropdown } from '@ant-design/pro-components';
import { Button, Dropdown, message } from 'antd';
import { useRef } from 'react';
import { queryStaffByPage, deleteStaff } from '@/services/ant-design-pro/staff';
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

type Staff = {
  id?: number;
  name?: string;
  age?: number;
  gender?: number;
  phone?: string;
  address?: string;
  title?: string;
  famousLevel?: number;
  birthday?: string;
  createTime?: string;
};

/** 名医等级徽章文案 */
const FAMOUS_BADGE: Record<number, string> = {
  1: '一级名医',
  2: '二级名医',
  3: '三级名医',
};

const columns: ProColumns<Staff>[] = [
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
    tip: '目前只会根据员工的名字和手机号码查询',
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
    title: '角色',
    dataIndex: 'role',
    valueType: 'select',
    valueEnum: {
      0: { text: '管理员' },
      1: { text: '领导' },
      99: { text: '员工' },
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
    title: '名医等级',
    dataIndex: 'famousLevel',
    valueType: 'select',
    valueEnum: {
      0: { text: '普通' },
      1: { text: '一级名医' },
      2: { text: '二级名医' },
      3: { text: '三级名医' },
    },
    ellipsis: true,
    tip: '仅影响列表排序与标识徽章',
    formItemProps: {
      rules: [],
    },
    render: (_, record) => {
      const level = record.famousLevel ?? 0;
      if (level === 0) return '普通';
      return FAMOUS_BADGE[level] || '普通';
    },
  },
  {
    title: '手机号码',
    dataIndex: 'phone',
    copyable: true,
    ellipsis: true,
    tip: '目前只会根据员工的名字和手机号码查询',
    formItemProps: {
      rules: [],
    },
  },
  {
    title: '年龄',
    dataIndex: 'age',
    ellipsis: true,
    formItemProps: {
      rules: [],
    },
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
    sorter: false,
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
        key="update"
        onClick={() => {
          if (record.id) {
            history.push(`/staff/update?staffId=${record.id}`);
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
            deleteStaff(record.id).then((res) => {
              if (res.id) {
                message.success('删除成功');
                action?.reload();
              } else {
                message.warning('没有找到该员工');
              }
            });
          } else {
            message.warning('记录id不存,无法删除,请刷新再试');
          }
        }}
      >
        删除
      </a>,
      <TableDropdown
        key="actionGroup"
        onSelect={() => action?.reload()}
        menus={[{ key: 'copy', name: '复制' }]}
      />,
    ],
  },
];

export default () => {
  const actionRef = useRef<ActionType>();
  return (
    <ProTable<Staff>
      columns={columns}
      actionRef={actionRef}
      cardBordered
      request={async (params = {}, sort, filter) => {
        console.log(sort, filter);
        const msg = await queryStaffByPage(params);
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
        persistenceKey: 'pro-table-singe-demos-staff-query',
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
      headerTitle="已经查询到的员工信息:"
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
