import { useState } from 'react';
import { message } from 'antd';
import { fetchCustomerByPhone } from '@/services/ant-design-pro/customer';
import { queryTreatByPage } from '@/services/ant-design-pro/treat';
import type { CustomerBrief } from './helpers';

/** 顾客定位：手机号查档案 + 联动该顾客的接诊单（时间倒序，默认选最近一张） */
const useCustomerLookup = () => {
  const [customer, setCustomer] = useState<CustomerBrief | undefined>();
  const [phone, setPhone] = useState('');
  const [finding, setFinding] = useState(false);
  /** 接诊单联动：该顾客的诊疗记录（时间倒序），默认带最近一张的辨证与治法/方案 */
  const [treats, setTreats] = useState<API.Treat[]>([]);
  const [treatId, setTreatId] = useState<number | undefined>();

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
        const page = await queryTreatByPage({ current: 1, pageSize: 20, customerId: found.id });
        const list = page?.data || [];
        setTreats(list);
        setTreatId(list[0]?.id);
      } else {
        setCustomer(undefined);
        setTreats([]);
        setTreatId(undefined);
        message.warning('未找到该手机号顾客，请先在「顾客 → 新建顾客」建档');
      }
    } finally {
      setFinding(false);
    }
  };

  return { customer, phone, setPhone, finding, findCustomer, treats, treatId, setTreatId };
};

export default useCustomerLookup;
