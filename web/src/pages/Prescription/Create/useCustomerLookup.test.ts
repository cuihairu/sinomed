// 顾客定位两条硬约束的回归（Codex 深审第 3 条前端半）：
// 1. 新查询发起即清空上一位顾客的档案与接诊单；
// 2. 连续查询时慢的旧响应不得覆盖新顾客（A→B 连查，A 后到不许把顾客翻回 A）。
import { act, renderHook, waitFor } from '@testing-library/react';
import { fetchCustomerByPhone } from '@/services/ant-design-pro/customer';
import { queryTreatByPage } from '@/services/ant-design-pro/treat';
import useCustomerLookup from './useCustomerLookup';

jest.mock('@/services/ant-design-pro/customer', () => ({
  fetchCustomerByPhone: jest.fn(),
}));

jest.mock('@/services/ant-design-pro/treat', () => ({
  queryTreatByPage: jest.fn(),
}));

const mockedFetch = fetchCustomerByPhone as jest.Mock;
const mockedTreats = queryTreatByPage as jest.Mock;

describe('useCustomerLookup 换查即清与过期响应', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('发起新查询时立即清空上一位顾客与接诊单', async () => {
    mockedFetch.mockResolvedValue({ id: 1, name: '顾客A', phone: '13800000001' });
    mockedTreats.mockResolvedValue({ data: [{ id: 11, desc: '首诊' }] });

    const { result } = renderHook(() => useCustomerLookup());

    act(() => {
      result.current.setPhone('13800000001');
    });
    await act(async () => {
      await result.current.findCustomer();
    });
    expect(result.current.customer?.id).toBe(1);
    expect(result.current.treats).toHaveLength(1);
    expect(result.current.treatId).toBe(11);

    // 第二次查询发出瞬间：旧档案/旧接诊单必须已清空（提交在途也不携带）
    let releaseSecond!: () => void;
    mockedFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseSecond = () => resolve({ id: 2, name: '顾客B', phone: '13800000002' });
        }),
    );
    act(() => {
      result.current.setPhone('13800000002');
    });
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.findCustomer();
    });
    // 同步 act 退出即刷新一帧：此刻 B 还在途，档案/接诊单应已是清空态
    expect(result.current.customer).toBeUndefined();
    expect(result.current.treats).toEqual([]);
    expect(result.current.treatId).toBeUndefined();
    await act(async () => {
      releaseSecond();
      await pending;
    });
    expect(result.current.customer?.id).toBe(2);
    expect(result.current.finding).toBe(false);
  });

  it('A→B 连查：A 的慢响应后到，不得把顾客翻回 A', async () => {
    let releaseFirst!: () => void;
    mockedFetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseFirst = () => resolve({ id: 1, name: '顾客A', phone: '13800000001' });
        }),
    );
    mockedTreats.mockResolvedValue({ data: [] });

    const { result } = renderHook(() => useCustomerLookup());

    act(() => {
      result.current.setPhone('13800000001');
    });
    await act(async () => {
      result.current.findCustomer();
    });

    act(() => {
      result.current.setPhone('13800000002');
    });
    mockedFetch.mockResolvedValueOnce({ id: 2, name: '顾客B', phone: '13800000002' });
    await act(async () => {
      await result.current.findCustomer();
    });
    expect(result.current.customer?.id).toBe(2);

    // A 的响应此刻才到：必须被丢弃
    await act(async () => {
      releaseFirst();
    });
    await waitFor(() => {
      expect(result.current.customer?.id).toBe(2);
      expect(result.current.finding).toBe(false);
    });
  });
});
