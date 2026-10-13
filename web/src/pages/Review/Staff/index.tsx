import type { EditableFormInstance, ProColumns, ProFormInstance } from '@ant-design/pro-components';
import moment from 'moment';
import { EditableProTable, ProForm, ProFormRadio } from '@ant-design/pro-components';
import React, { useEffect, useRef, useState } from 'react';
import {
  createReviewStaffBulk,
  fetchReviewStaffBulk,
  deleteReviewStaff,
} from '@/services/ant-design-pro/review';
import { message } from 'antd';

type DataSourceType = {
  id: React.Key;
  name?: string;
  cost?: number;
  done?: string;
  staffId?: number;
  day?: Date;
  advice?: string;
  createTime?: string;
  updateTime?: string;
  children?: DataSourceType[];
};

export default () => {
  const today = new Date();
  const [editableKeys, setEditableRowKeys] = useState<React.Key[]>(() => []);
  const formRef = useRef<ProFormInstance<any>>();
  const editorFormRef = useRef<EditableFormInstance<DataSourceType>>();
  const columns: ProColumns<DataSourceType>[] = [
    {
      title: '姓名',
      dataIndex: 'name',
      formItemProps: () => {
        return {
          rules: [{ required: true, message: '此项为必填项' }],
        };
      },
      width: '12%',
    },
    {
      title: '耗卡',
      key: 'cost',
      dataIndex: 'cost',
      width: '10%',
    },
    {
      title: '卡项已有/已做',
      dataIndex: 'done',
    },
    {
      title: '成单/建议',
      dataIndex: 'advice',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (text, record, _, action) => [
        <a
          key="editable"
          onClick={() => {
            action?.startEditable?.(record.id);
          }}
        >
          编辑
        </a>,
        <a
          key="delete"
          onClick={() => {
            const tableDataSource = formRef.current?.getFieldValue('table') as DataSourceType[];
            if (typeof record.id === 'number' && record.id > 0) {
              deleteReviewStaff(record.id)
                .then(() => {
                  message.success('数据删除成功');
                })
                .catch(() => {
                  message.error('数据删除失败，请刷新再试');
                });
            }
            formRef.current?.setFieldsValue({
              table: tableDataSource.filter((item) => item.id !== record.id),
            });
          }}
        >
          删除
        </a>,
      ],
    },
  ];
  useEffect(() => {
    fetchReviewStaffBulk(new Date()).then((data) => {
      formRef.current?.setFieldsValue({ table: data });
    });
  }, []);

  return (
    <ProForm<{
      table: DataSourceType[];
    }>
      formRef={formRef}
      initialValues={{
        table: [],
      }}
      validateTrigger="onBlur"
      onFinish={async (value) => {
        console.log(value);
        for (let i = 0; i < value.table.length; i++) {
          value.table[i].day = today;
        }
        const ret = await createReviewStaffBulk(value.table as API.ReviewStaff[]);
        if (ret?.message && ret.message === 'ok') {
          message.success('保存数据成功');
          return true;
        }
        message.error('保存数据失败');
        return false;
      }}
    >
      <EditableProTable<DataSourceType>
        rowKey="id"
        scroll={{
          x: 960,
        }}
        editableFormRef={editorFormRef}
        headerTitle={`当日报表:${moment(today).format('YYYY-MM-DD')}`}
        maxLength={10000}
        name="table"
        recordCreatorProps={{
          position: 'bottom',
          record: () => ({ id: -(Math.random() * 1000000000).toFixed(0) }),
        }}
        toolBarRender={() => [
          <ProFormRadio.Group
            key="render"
            fieldProps={{
              style: {
                marginBlockEnd: 0,
              },
              value: 'bottom',
            }}
            options={[]}
          />,
        ]}
        columns={columns}
        editable={{
          type: 'multiple',
          editableKeys,
          onChange: setEditableRowKeys,
          actionRender: (row, config, defaultDom) => {
            return [defaultDom.save, defaultDom.delete || defaultDom.cancel];
          },
        }}
      />
    </ProForm>
  );
};
