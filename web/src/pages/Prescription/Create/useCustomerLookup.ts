import { useRef, useState } from 'react';
import { message } from 'antd';
import { fetchCustomerByPhone } from '@/services/ant-design-pro/customer';
import { queryTreatByPage } from '@/services/ant-design-pro/treat';
import type { CustomerBrief } from './helpers';

/**
 * 顾客定位：手机号查档案 + 联动该顾客的接诊单（时间倒序，默认选最近一张）。
 * 两条硬约束：新查询发起时立即清空上一位顾客的档案与接诊单（不许残留）；
 * 只有最后一次查询的响应可落状态——慢的旧响应到达时直接丢弃，防止 A→B 连查后
 * 顾客被翻回 A 却带着 B 显示的手机号/接诊单。
 */
const useCustomerLookup = () => {
  const [customer, setCustomer] = useState<CustomerBrief | undefined>();
  const [phone, setPhone] = useState('');
  const [finding, setFinding] = useState(false);
  /** 接诊单联动：该顾客的诊疗记录（时间倒序），默认带最近一张的辨证与治法/方案 */
  const [treats, setTreats] = useState<API.Treat[]>([]);
  const [treatId, setTreatId] = useState<number | undefined>();
  const lookupSeq = useRef(0);

  const findCustomer = async () => {
    const p = phone.trim();
    if (!/^1\d{10}$/.test(p)) {
      message.warning('请输入 1 开头的 11 位手机号');
      return;
    }
    const seq = ++lookupSeq.current;
    // 发起即清：提交在途时也不会再携带上一位顾客的接诊单
    setCustomer(undefined);
    setTreats([]);
    setTreatId(undefined);
    setFinding(true);
    try {
      const found = await fetchCustomerByPhone(p);
      if (seq !== lookupSeq.current) return;
      if (found?.id) {
        setCustomer(found);
        const page = await queryTreatByPage({ current: 1, pageSize: 20, customerId: found.id });
        if (seq !== lookupSeq.current) return;
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
      if (seq === lookupSeq.current) {
        setFinding(false);
      }
    }
  };

  return { customer, phone, setPhone, finding, findCustomer, treats, treatId, setTreatId };
};

export default useCustomerLookup;
