import React from 'react';
import { createCustomer } from '@/services/ant-design-pro/customer';
import {
  ProForm,
  ProFormText,
  ProFormTextArea,
  ProFormDatePicker,
  ProFormSelect,
} from '@ant-design/pro-components';
import { message } from 'antd';

const Create: React.FC = () => {
  return (
    <ProForm<{
      name: string;
      age: number;
      gender: number;
      phone: string;
      address: string;
      level: number;
      birthday: string;
    }>
      grid
      omitNil
      layout={'vertical'}
      onFinish={async (values) => {
        const result = await createCustomer(values);
        console.log(result);
        if (result.id) {
          message.success('创建成功');
        }
        return true;
      }}
    >
      <ProFormText
        colProps={{ md: 12, xl: 8 }}
        name="name"
        label="姓名"
        tooltip="长度范围2-20字符,选填项"
        placeholder="输入顾客姓名"
        required={true}
      />
      <ProFormText
        colProps={{ md: 12, xl: 4 }}
        name="age"
        label="年龄"
        tooltip="年龄"
        placeholder="38"
      />
      <ProFormSelect
        colProps={{ md: 12, xl: 4 }}
        name="gender"
        label="性别"
        valueEnum={{ 0: '女', 1: '男' }}
        placeholder={'女'}
      />
      <ProFormText
        colProps={{ md: 12, xl: 8 }}
        name="phone"
        label="手机号码"
        tooltip="方便按手机查找"
        placeholder="输入手机号码"
      />
      <ProFormTextArea colProps={{ span: 24 }} name="address" label="详细的工作地址或家庭住址" />
      <ProFormSelect
        colProps={{ md: 12, xl: 4 }}
        name="level"
        label="会员等级"
        tooltip="会员等级，打折之类的使用"
        valueEnum={{ 1: '普通会员', 2: '银卡会员', 3: '金卡会员' }}
        placeholder="普通会员"
      />
      <ProFormDatePicker colProps={{ xl: 8, md: 12 }} label="生日" name="birthday" />
    </ProForm>
  );
};

export default Create;
