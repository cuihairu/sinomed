import { EllipsisOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable, TableDropdown } from '@ant-design/pro-components';
import {Button, Dropdown, message} from 'antd';
import { useRef } from 'react';
import { useSearchParams,history } from '@umijs/max';
import {deleteTreat, queryTreatByPage} from "@/services/ant-design-pro/treat";

export const waitTimePromise = async (time: number = 100) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(true);
    }, time);
  });
};


type TreatInfo = {
  id?: number; // 诊断单id
  createTime?: string; //创建时间
  desc?: string; //主述
  diagnose?: string; //辨证
  plan?: string; //治法/方案
  acuCourse?: string; //疗程
  acupointLeft?: string; //取穴（左）
  acupointRight?: string; //取穴（右）
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
    hideInSearch: true,
    width: '8%'
  },
  {
    title: '主述',
    dataIndex: 'desc',
    ellipsis: true,
    hideInSearch: true,
    width: '18%'
  },
  {
    title: '辨证',
    dataIndex: 'diagnose',
    ellipsis: true,
    hideInSearch: true,
    width: '16%'
  },
  {
    title: '治法/方案',
    dataIndex: 'plan',
    ellipsis: true,
    hideInSearch: true,
  },
  {
    title: '疗程',
    dataIndex: 'acuCourse',
    ellipsis: true,
    hideInSearch: true,
    width: '12%'
  },
  {
    title: '取穴',
    hideInSearch: true,
    width: '14%',
    render: (_, record) =>
      [record.acupointLeft, record.acupointRight]
        .filter(Boolean)
        .join(' / ') || '—',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    valueType: 'dateTime',
    hideInTable: false,
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
            history.push(`/treat/create?treatId=${record.id}`);
          }else{
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
            deleteTreat(record.id).then( () => {
              message.success('删除成功');
              action?.reload();
            })
          }
        }}
      >
        删除
      </a>,
      <TableDropdown
        key="actionGroup"
        onSelect={() => action?.reload()}
        menus={[
        ]}
      />,
    ],
  },
];

export default () => {
  const [searchParams ] = useSearchParams();
  const actionRef = useRef<ActionType>();
  const customerId = searchParams.get("customerId");
  if ((!customerId) || isNaN(parseInt(customerId))) {
    message.error("没有传入顾客id,请选择顾客,再选择诊断创建诊断表")
    history.push("/customer/query")
    return null;
  }
  const id = parseInt(customerId);

  return (
    <ProTable<TreatInfo>
      columns={columns}
      actionRef={actionRef}
      params={{
        customerId: id
      }}
      cardBordered
      request={async (params = {}, sort, filter) => {
        console.log(sort, filter);
        const msg = await queryTreatByPage({
          customerId: params.customerId,
          pageSize: params?.pageSize,
          current: params?.current,
          startTime: params?.startTime,
          endTime: params?.endTime,
        });
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
        persistenceKey: 'pro-table-singe-demos',
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
