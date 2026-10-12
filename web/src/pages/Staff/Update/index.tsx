
import React, {useRef} from 'react';
import { fetchStaffById,updateStaff } from '@/services/ant-design-pro/staff';
import {
  ProForm,
  ProFormText,
  ProFormTextArea,
  ProFormDatePicker,
  ProFormSelect,
  ProFormInstance
} from '@ant-design/pro-components'
import {message} from "antd";
import { useSearchParams,history } from '@umijs/max';

type Staff = {
  id:number;
  account: string;
  password: string;
  name: string;
  age: number;
  phone: string;
  gender: number;
  address: string;
  role: number;
  birthday: string;
};
const Update: React.FC = () => {
  let [searchParams, ] = useSearchParams();
  const formRef = useRef<ProFormInstance>();
  let staffId = searchParams.get("staffId");
  if (staffId === null || staffId === undefined || staffId === "") {
    message.error("没有传入员工id")
    history.push("/staff/query")
    return null;
  }
  const id =  parseInt(staffId);
  fetchStaffById(staffId).then((value) => {
    console.log(value)
    formRef.current?.setFieldsValue({
      id: id,
      account: value.account,
      name: value.name,
      age: value.age,
      gender: value.gender,
      phone: value.phone,
      address: value.address,
      role: value.role,
      birthday: value.birthday,
    });
  });
  return (
    <ProForm<Staff>
    grid
    omitNil
    formRef={formRef}
    layout={'vertical'}
    onFinish={async (values) => {
        values.id = id;
        const result = await updateStaff(values);
        console.log(result)
        if (result.id) {
          message.success("更新成功")
          return true;
        }
        return false;
      }

    }
  >
    <ProFormText colProps={{ md:4,xl:4}} name="account" label="账号" tooltip="长度范围2-20字符,必填项" placeholder="输入登录账号名称" required={true}/>
    <ProFormText colProps={{ md: 4, xl: 4 }} name="name" label="姓名" tooltip="长度范围2-20字符,选填项" placeholder="输入员工名字" required={true} />
    <ProFormText colProps={{ md: 4, xl: 2 }} name="age" label="年纪" tooltip="年纪" placeholder="20"/>
    <ProFormText.Password colProps={{ md: 12, xl: 4 }} name="password" label="密码" required={true} tooltip="长度范围2-20字符,必填项" placeholder="输入密码"/>
    <ProFormText colProps={{ md: 12, xl: 6 }} name="phone" label="手机号码" tooltip="方便按手机查找" placeholder="输入手机号码"/>
    <ProFormSelect colProps={{ md: 4, xl: 2 }} name="gender" label="性别" valueEnum={{0:'女',1:'男'}} options={[{value:0,label:"女"},{value:1,label:"男"}]} placeholder="设置性别"/>
    <ProFormSelect colProps={{ md: 4, xl: 2 }} name="role" label="角色" valueEnum={{1:'管理层',99:'员工'}} tooltip="管理员可以查看统计数据，员工只看到自己相关的数据" placeholder="员工"/>
    <ProFormText colProps={{ md: 4, xl: 2 }} name="title" label="职务" tooltip="自由文本（如 医师/针灸医师/前台）；排班页医师名册=在职且职务含「医师」" placeholder="医师"/>
    <ProFormTextArea
      colProps={{ span: 24 }}
      name="address"
      label="详细的工作地址或家庭住址"
    />
    <ProFormDatePicker
      colProps={{ xl: 8, md: 12 }}
      label="生日"
      name="birthday"
    />
  </ProForm>);
};

export default Update;
