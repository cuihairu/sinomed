import React, { useRef } from 'react';
import { ProForm, ProFormInstance, ProFormTextArea, ProCard } from '@ant-design/pro-components';
import { message } from 'antd';
import { createReview, fetchReviewByDay } from '@/services/ant-design-pro/review';
import moment from 'moment';

const Plan: React.FC = () => {
  const today = new Date();
  const formRef = useRef<ProFormInstance>();
  fetchReviewByDay(today).then((value) => {
    console.log(value);
    formRef.current?.setFieldsValue({
      id: value?.id,
      good: value?.good,
      improvement: value?.improvement,
      day: today,
    });
  });
  return (
    <ProForm<{
      id?: number;
      day: Date;
      good?: string;
      improvement?: string;
    }>
      initialValues={{
        day: today,
      }}
      grid
      formRef={formRef}
      onFinish={async (values) => {
        values.day = today;
        createReview(values)
          .then(() => {
            message.success('每日总结保存成功');
          })
          .catch(() => {
            message.error('每日总结保存失败');
          });
        return true;
      }}
    >
      <ProCard title={`今日总结 ${moment(today).format('YYYY-MM-DD')}`} headerBordered>
        <ProFormTextArea colProps={{ md: 12, xl: 24 }} name={'good'} label={'做的好的事情'} />
        <ProFormTextArea
          colProps={{ md: 12, xl: 24 }}
          name={'improvement'}
          label={'待改进的事情'}
        />
      </ProCard>
    </ProForm>
  );
};

export default Plan;
