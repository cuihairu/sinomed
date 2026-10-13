import { SearchOutlined } from '@ant-design/icons';
import { Input, message } from 'antd';
import type { InputRef } from 'antd';
import { history } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import { fetchCustomerById, fetchCustomerByPhone } from '@/services/ant-design-pro/customer';

/**
 * 扫码枪输入（键盘仿真 HID，desktop.md D3）：扫码枪即打即传、回车结尾，
 * 本输入框落焦点即收码；失焦后仅当焦点落到页面空白处才自动收回，不打断其它表单。
 * 码值约定：11 位手机号（1 开头）按手机号定位顾客，纯数字短码按顾客 id 定位；
 * 定位成功跳顾客详情页（建档 / 接诊入口）。没有扫码枪时手动输入等价。
 * 壳内串口模式（desktop.md D9）：桌面工作站配置了串口扫码枪时由壳读码并广播
 * scanner-code 事件，这里订阅后走同一套定位流程；浏览器无 __TAURI__ 自动退化。
 */
export default function ScannerInput({
  onLocated,
}: { onLocated?: (customer: API.Customer) => void } = {}) {
  const inputRef = useRef<InputRef>(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    inputRef.current?.focus({ preventScroll: true });
  }, []);

  // 失焦后仅当焦点落回页面空白处才收回（扫码枪随时可打），不打断其它表单输入
  const handleBlur = () => {
    window.setTimeout(() => {
      const active = document.activeElement;
      if (!active || active === document.body) {
        inputRef.current?.focus({ preventScroll: true });
      }
    }, 100);
  };

  const locate = async (raw: string) => {
    const value = raw.trim();
    if (!value || busy) return;
    setBusy(true);
    try {
      const customer = /^1\d{10}$/.test(value)
        ? await fetchCustomerByPhone(value)
        : /^\d{1,10}$/.test(value)
        ? await fetchCustomerById(value)
        : null;
      if (!customer || !customer.id) {
        message.warning(`顾客码 ${value} 未定位到顾客，请确认或先建档`);
        return;
      }
      message.success(`已定位顾客：${customer.name || `id ${customer.id}`}`);
      // 传了 onLocated（接诊开单等页内定位场景）则就地回调，不跳顾客页；否则默认进顾客详情
      if (onLocated) {
        onLocated(customer);
        return;
      }
      history.push(`/customer/update?customerId=${customer.id}`);
    } catch (e) {
      message.error('未找到对应顾客，请确认顾客码或先建档');
    } finally {
      setBusy(false);
    }
  };

  // 串口模式订阅只挂一次，locate 闭包随 busy 重建，经 ref 取最新避免陈旧状态
  const locateRef = useRef(locate);
  useEffect(() => {
    locateRef.current = locate;
  });

  useEffect(() => {
    const tauri = (
      window as unknown as {
        __TAURI__?: {
          event?: {
            listen?: (
              name: string,
              handler: (ev: { payload: unknown }) => Promise<() => void>,
            ) => Promise<() => void>;
          };
        };
      }
    ).__TAURI__;
    if (!tauri?.event?.listen) return;
    let cancelled = false;
    let unlisten: (() => void) | undefined;
    tauri.event
      .listen('scanner-code', async ({ payload }: { payload: unknown }) => {
        if (typeof payload === 'string' && payload.trim()) {
          setCode('');
          locateRef.current(payload);
        }
        return () => undefined;
      })
      .then((stop: () => void) => {
        if (cancelled) stop();
        else unlisten = stop;
      });
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, []);

  return (
    <div style={{ marginBottom: 16 }}>
      <Input
        ref={inputRef}
        autoFocus
        onBlur={handleBlur}
        allowClear
        disabled={busy}
        prefix={<SearchOutlined />}
        placeholder="扫码定位顾客：扫顾客码（手机号 / 顾客编号）后自动打开顾客页"
        style={{ maxWidth: 480 }}
        value={code}
        onChange={(e) => setCode(e.target.value)}
        onPressEnter={async () => {
          const value = code;
          setCode('');
          await locate(value);
        }}
      />
    </div>
  );
}
