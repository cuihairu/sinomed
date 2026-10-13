import React from 'react';
import { sign } from '@/services/ant-design-pro/staff';
import { ProForm, ProFormSelect } from '@ant-design/pro-components';
import { message } from 'antd';
import { history } from '@umijs/max';
const Create: React.FC = () => {
  return (
    <ProForm<{
      signType: number; // 1 上班 0 下班
    }>
      grid
      onFinish={async (values) => {
        const result = await sign(values);
        console.log(result);
        if (result.signType === 0) {
          message.success(`下班签到成功:${result.signTime}`);
        } else {
          message.success(`上班签到成功:${result.signTime}`);
        }
        history.push('/staff/sign/timesheet/today');
        return true;
      }}
    >
      <ProFormSelect
        colProps={{ md: 6, xl: 4 }}
        name="signType"
        label="签到类型"
        valueEnum={{ 0: '下班', 1: '上班' }}
        placeholder="上班"
      />
    </ProForm>
  );
};

export default Create;
