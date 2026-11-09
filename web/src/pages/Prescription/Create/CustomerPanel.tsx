import { Button } from 'antd';
import React from 'react';
import { LEVEL_TEXT, type CustomerBrief } from './helpers';

/** 1 · 顾客：手机号定位档案（未定位不让写方） */
const CustomerPanel: React.FC<{
  phone: string;
  setPhone: (v: string) => void;
  finding: boolean;
  findCustomer: () => void;
  customer?: CustomerBrief;
}> = ({ phone, setPhone, finding, findCustomer, customer }) => (
  <div className="pr-customer">
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
      <div className="none">未定位顾客，先查找再写方</div>
    )}
  </div>
);

export default CustomerPanel;
