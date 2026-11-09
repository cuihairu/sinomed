import { useEffect, useMemo, useState } from 'react';
import { queryWxAccounts, type WxAccount } from '@/services/ant-design-pro/wx';

/** 三个 tab 都需要「账号下拉 + 行内显示账号名」，口径统一 */
const useAccountOptions = () => {
  const [accounts, setAccounts] = useState<WxAccount[]>([]);
  useEffect(() => {
    queryWxAccounts().then((list) => setAccounts(list || []));
  }, []);
  const options = useMemo(
    () => accounts.map((a) => ({ value: a.id!, label: a.accountName || a.appId || `#${a.id}` })),
    [accounts],
  );
  const nameOf = (id?: number) =>
    accounts.find((a) => a.id === id)?.accountName || (id != null ? `#${id}` : '-');
  return { accounts, options, nameOf };
};

export default useAccountOptions;
