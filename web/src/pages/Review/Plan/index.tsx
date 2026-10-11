
import React, {useRef} from 'react';
import {ProForm, ProFormInstance,ProFormText,ProFormDatePicker,ProFormTextArea} from '@ant-design/pro-components'
import {message} from "antd";
import { history } from '@umijs/max';
import {useSearchParams} from "@@/exports";
import {fetchCustomerById} from "@/services/ant-design-pro/customer";
import {createReviewCustomer} from "@/services/ant-design-pro/review";

const Plan: React.FC = () => {
  let [searchParams, ] = useSearchParams();
  const formRef = useRef<ProFormInstance>();
  const customerId = searchParams.get("customerId");
  if (customerId === null || customerId === undefined || customerId === "") {
    message.error("没有传入顾客id,请选择顾客,再选择诊断创建诊断表")
    history.push("/customer/query")
    return null;
  }
  const id = parseInt(customerId);
  if (isNaN(id)){
    message.error("没有传入顾客id,请选择顾客,再选择诊断创建诊断表")
    history.push("/customer/query")
    return null;
  }
  fetchCustomerById(customerId).then((value) => {
    console.log(value)
    formRef.current?.setFieldsValue({
      customerId: customerId,
      name: value.name,
    });
  });

  return (<ProForm<{
    customerId: number; // 顾客id
    name: string;
    last?: Date;
    plan: Date;
    advice?: string;
  }>
    grid
    formRef={formRef}
    onFinish={async (values) => {
      createReviewCustomer({
        customerId: values?.customerId,
        name: values?.name,
        last: values?.last,
        day: values?.plan,
        advice: values?.advice,
      }).then(()=>{
        message.success("计划创建成功");
      }).catch(()=>{
        message.error("计划创建失败");
      })
        return true;
      }
    }
  >
    <ProFormText colProps={{ md: 12, xl: 4 }} name={"name"} label={"姓名"}/>
    <ProFormDatePicker colProps={{ md: 12, xl: 4 }} name={"last"} label={"最近访问"}/>
    <ProFormDatePicker colProps={{ md: 12, xl: 10 }} name={"plan"} label={"计划回访"}/>
    <ProFormTextArea
      colProps={{ span: 24 }}
      name={"advice"}
      label={"回访原因"}
      placeholder={"如：艾灸第 4 疗程结束后 7 天，回访睡眠与饮食情况，酌情续卡"}
      fieldProps={{ maxLength: 100, showCount: true }}
    />
  </ProForm>);
};

export default Plan;
