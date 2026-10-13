import React, { useState } from 'react';
import {
  PageContainer,
  ProForm,
  ProFormTextArea,
  ProFormSelect,
  ProFormDateTimePicker,
} from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Button, message } from 'antd';
import moment from 'moment';
import { createAppointment } from '@/services/ant-design-pro/appointment';
import { fetchCustomerByPhone } from '@/services/ant-design-pro/customer';
import { queryStaffByPage } from '@/services/ant-design-pro/staff';
import { queryItemByPage } from '@/services/ant-design-pro/item';
import './index.less';

/** 会员等级展示口径（同顾客档案）：1 普通 / 2 银卡 / 3 金卡 */
const LEVEL_TEXT: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员' };

type CustomerBrief = { id?: number; name?: string; phone?: string; level?: number };

const Create: React.FC = () => {
  const [customer, setCustomer] = useState<CustomerBrief | undefined>();
  const [phone, setPhone] = useState('');
  const [finding, setFinding] = useState(false);
  const [staffOptions, setStaffOptions] = useState<{ label: string; value: number }[]>([]);
  const [itemOptions, setItemOptions] = useState<{ label: string; value: number }[]>([]);

  const loadOptions = async () => {
    if (staffOptions.length > 0) return;
    const [staffs, items] = await Promise.all([
      queryStaffByPage({ current: 1, pageSize: 100 }),
      queryItemByPage({ current: 1, pageSize: 100 }),
    ]);
    setStaffOptions(
      (staffs?.data || []).map((s) => ({ label: s.name || `员工${s.id}`, value: s.id! })),
    );
    setItemOptions(
      (items?.data || []).map((i) => ({ label: i.name || `卡项${i.id}`, value: i.id! })),
    );
  };

  const findCustomer = async () => {
    const p = phone.trim();
    if (!/^1\d{10}$/.test(p)) {
      message.warning('请输入 1 开头的 11 位手机号');
      return;
    }
    setFinding(true);
    try {
      const found = await fetchCustomerByPhone(p);
      if (found?.id) {
        setCustomer(found);
      } else {
        setCustomer(undefined);
        message.warning('未找到该手机号顾客，请先在「顾客 → 新建顾客」建档');
      }
    } finally {
      setFinding(false);
    }
  };

  return (
    <PageContainer
      title="新建预约"
      content="按手机号定位顾客 → 选时段与项目；顾客到店后在「预约排班」点「到店接待」，再发起接诊。"
    >
      <div className="ap-create">
        <div className="ap-customer">
          <h3>1 · 顾客</h3>
          <div className="find">
            <input
              className="phone"
              placeholder="顾客手机号"
              value={phone}
              maxLength={11}
              onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
              onKeyDown={(e) => e.key === 'Enter' && findCustomer()}
            />
            <Button type="primary" loading={finding} onClick={findCustomer}>
              查找
            </Button>
          </div>
          {customer ? (
            <div className="found">
              <span className="nm">{customer.name}</span>
              <span className="tier">{LEVEL_TEXT[customer.level ?? 0] ?? '普通会员'}</span>
              <span className="tel">{customer.phone}</span>
            </div>
          ) : (
            <div className="none">未定位顾客，先查找再选时段</div>
          )}
        </div>

        <ProForm
          className="ap-form"
          layout="vertical"
          onFocus={loadOptions}
          submitter={{
            searchConfig: { submitText: '建约' },
            render: (_, dom) => dom,
          }}
          onFinish={async (values: any) => {
            if (!customer?.id) {
              message.warning('请先按手机号定位顾客');
              return false;
            }
            // 发带时区偏移的 ISO 串：服务端 Jackson 默认按 UTC 解析无时区串，会平移 8 小时
            const body = {
              customerId: customer.id,
              itemId: values.itemId,
              staffId: values.staffId,
              startTime: values.startTime
                ? moment(values.startTime).format('YYYY-MM-DDTHH:mm:ssZ')
                : undefined,
              duration: values.duration,
              remark: values.remark,
            };
            if (!body.startTime) {
              message.warning('请选择预约时段');
              return false;
            }
            const created = await createAppointment(body);
            if (created?.id) {
              message.success('预约已建');
              history.push('/appointment/query');
              return true;
            }
            return false;
          }}
        >
          <h3>2 · 时段与项目</h3>
          <ProFormDateTimePicker
            name="startTime"
            label="预约时段"
            placeholder="选择日期与时间"
            width="md"
            rules={[{ required: true, message: '请选择预约时段' }]}
            fieldProps={{ showTime: { format: 'HH:mm' }, format: 'YYYY-MM-DD HH:mm' }}
          />
          <ProFormSelect
            name="duration"
            label="时长"
            width="xs"
            initialValue={60}
            options={[
              { value: 30, label: '30 分钟' },
              { value: 60, label: '60 分钟' },
              { value: 90, label: '90 分钟' },
            ]}
          />
          <ProFormSelect
            name="staffId"
            label="接待员工"
            width="md"
            placeholder="可选，默认到店分配"
            options={staffOptions}
            fieldProps={{ onFocus: loadOptions }}
          />
          <ProFormSelect
            name="itemId"
            label="预约项目"
            width="md"
            placeholder="可选，到店再定"
            options={itemOptions}
            fieldProps={{ onFocus: loadOptions }}
          />
          <ProFormTextArea
            name="remark"
            label="备注"
            placeholder="症状 / 需求，选填"
            fieldProps={{ rows: 3 }}
          />
        </ProForm>
      </div>
    </PageContainer>
  );
};

export default Create;
