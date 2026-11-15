// 审方两条硬约束的回归（Codex 深审第 4/5 条）：
// 1. 请求异常只置 failed，不回退成「未发现禁忌」类通过结论；
// 2. 清空药味 / 切换顾客后，已发出的在途响应不得回填状态。
// 服务层全部 mock，用可控 Promise 复现「清空后才返回」的时序。
// 注意：不 import 类型（esbuild 剥类型后 babel CJS 变换会报 imported binding
// 同时用于类型标注），hook 入参用 Parameters<typeof usePrescriptionChecks> 推导。
import { act, renderHook, screen, render } from '@testing-library/react';
import React from 'react';
import CheckPanels from './CheckPanels';
import usePrescriptionChecks from './usePrescriptionChecks';
import {
  checkAllergy,
  checkCompatibility,
  checkDose,
  pricePrescription,
} from '@/services/ant-design-pro/prescription';
import { listCustomerHistories } from '@/services/ant-design-pro/customer';

jest.mock('@/services/ant-design-pro/prescription', () => ({
  checkCompatibility: jest.fn(),
  checkAllergy: jest.fn(),
  checkDose: jest.fn(),
  pricePrescription: jest.fn(),
}));

jest.mock('@/services/ant-design-pro/customer', () => ({
  listCustomerHistories: jest.fn(() => Promise.resolve([])),
}));

const mocked = {
  checkCompatibility: checkCompatibility as jest.Mock,
  checkAllergy: checkAllergy as jest.Mock,
  checkDose: checkDose as jest.Mock,
  pricePrescription: pricePrescription as jest.Mock,
  listCustomerHistories: listCustomerHistories as jest.Mock,
};

/** hook 四入参元组：[namedHerbs, herbs, doses, customer]（纯结构化别名，勿引用导入类型） */
type HerbRow = {
  id?: number;
  herb?: string;
  weight?: number;
  special?: string;
  process?: string;
  sort?: number;
};
type CustomerBriefLike = {
  id?: number;
  name?: string;
  phone?: string;
  level?: number;
  gender?: number;
  age?: number;
};
type HookArgs = [string[], HerbRow[], number, CustomerBriefLike | undefined];

const customer = { id: 7, name: '张三' };

const initialProps: HookArgs = [
  ['甘草', '甘遂'],
  [
    { herb: '甘草', weight: 3 },
    { herb: '甘遂', weight: 3 },
  ],
  1,
  undefined,
];

describe('usePrescriptionChecks 在途请求失效', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });
  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('清空药味后，配伍在途响应不回填、失败标志不残留', async () => {
    let resolveCompat!: (r: unknown) => void;
    mocked.checkCompatibility.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveCompat = resolve;
        }),
    );

    const { result, rerender } = renderHook((props: HookArgs) => usePrescriptionChecks(...props), {
      initialProps,
    });

    // 防抖到点，请求发出且挂起
    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    expect(mocked.checkCompatibility).toHaveBeenCalledTimes(1);

    // 清空输入：结果清零、checking 复位
    rerender([[], [{ herb: '', weight: undefined }], 1, undefined]);
    expect(result.current.compat).toBeUndefined();
    expect(result.current.compatFailed).toBe(false);
    expect(result.current.checking).toBe(false);

    // 已在途的响应此刻才返回：不得覆盖清空后的状态
    await act(async () => {
      resolveCompat({ checked: 2, findings: [] });
    });
    expect(result.current.compat).toBeUndefined();
    expect(result.current.compatFailed).toBe(false);
  });

  it('计价同款：清空药味后，在途试算响应不回填', async () => {
    let resolvePrice!: (r: unknown) => void;
    mocked.pricePrescription.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePrice = resolve;
        }),
    );

    const { result, rerender } = renderHook((props: HookArgs) => usePrescriptionChecks(...props), {
      initialProps,
    });

    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    expect(mocked.pricePrescription).toHaveBeenCalledTimes(1);

    rerender([[], [{ herb: '', weight: undefined }], 1, undefined]);
    expect(result.current.pricing).toBeUndefined();

    await act(async () => {
      resolvePrice({ totalFen: 12300, herbCount: 2 });
    });
    expect(result.current.pricing).toBeUndefined();
    expect(result.current.pricingFailed).toBe(false);
  });
});

describe('usePrescriptionChecks 失败态区分', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });
  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('配伍请求异常：置 compatFailed，不产生通过结论', async () => {
    mocked.checkCompatibility.mockRejectedValue(new Error('down'));

    const { result } = renderHook((props: HookArgs) => usePrescriptionChecks(...props), {
      initialProps,
    });

    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    expect(result.current.compat).toBeUndefined();
    expect(result.current.compatFailed).toBe(true);
    expect(result.current.checking).toBe(false);
  });

  it('过敏审方请求异常：置 allergyFailed', async () => {
    mocked.checkAllergy.mockRejectedValue(new Error('down'));

    const { result } = renderHook((props: HookArgs) => usePrescriptionChecks(...props), {
      initialProps: [['甘草', '甘遂'], initialProps[1], 1, customer] as HookArgs,
    });

    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    expect(result.current.allergy).toBeUndefined();
    expect(result.current.allergyFailed).toBe(true);
  });

  it('过敏史条数读取失败：置 allergyRecordFailed，不冒充「无过敏记录」', async () => {
    mocked.listCustomerHistories.mockRejectedValue(new Error('down'));

    const { result } = renderHook((props: HookArgs) => usePrescriptionChecks(...props), {
      initialProps: [['甘草', '甘遂'], initialProps[1], 1, customer] as HookArgs,
    });

    await act(async () => {});
    expect(result.current.allergyRecordCount).toBe(0);
    expect(result.current.allergyRecordFailed).toBe(true);
  });

  it('剂量请求异常：置 doseFailed', async () => {
    mocked.checkDose.mockRejectedValue(new Error('down'));

    const { result } = renderHook((props: HookArgs) => usePrescriptionChecks(...props), {
      initialProps,
    });

    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    expect(result.current.dose).toBeUndefined();
    expect(result.current.doseFailed).toBe(true);
  });
});

describe('CheckPanels 失败不渲染通过结论', () => {
  const baseProps = {
    namedCount: 2,
    checking: false,
    compatFailed: false,
    specialFxCount: 0,
    allergyFailed: false,
    allergyRecordCount: 0,
    allergyRecordFailed: false,
    hasCustomer: true,
    doseFailed: false,
  };

  it('检查成功且无命中：显示通过结论', () => {
    render(
      React.createElement(CheckPanels, {
        ...baseProps,
        compat: { checked: 2, findings: [] },
        allergy: { customerId: 7, checked: 2, findings: [] },
        dose: { checked: 2, limited: 2, findings: [] },
      }),
    );
    expect(screen.getByText('未发现配伍禁忌（十八反 / 十九畏）')).toBeTruthy();
    expect(screen.getByText(/档案无药物过敏记录/)).toBeTruthy();
  });

  it('配伍失败：不显示「未发现配伍禁忌」，改提示未完成', () => {
    render(React.createElement(CheckPanels, { ...baseProps, compatFailed: true }));
    expect(screen.queryByText('未发现配伍禁忌（十八反 / 十九畏）')).toBeNull();
    expect(screen.getByText('审方服务暂不可用，本次未完成比对（请重试或稍后再开方）')).toBeTruthy();
  });

  it('过敏史读取失败：不显示「档案无药物过敏记录」', () => {
    render(React.createElement(CheckPanels, { ...baseProps, allergyRecordFailed: true }));
    expect(screen.queryByText(/档案无药物过敏记录/)).toBeNull();
    expect(screen.getByText(/过敏史读取失败，本次无法确认过敏记录/)).toBeTruthy();
  });

  it('剂量审方失败：不显示「均未超线」类结论', () => {
    render(React.createElement(CheckPanels, { ...baseProps, doseFailed: true }));
    expect(screen.queryByText(/均未超线/)).toBeNull();
    expect(screen.getByText('剂量审方未完成（服务异常），超限情况未知')).toBeTruthy();
  });
});
