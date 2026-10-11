import { request } from '@umijs/max';
import {
  PAY_TYPE,
  PAY_TYPE_TEXT,
  createRecharge,
  fetchRechargeBalance,
  fetchReconciliation,
  receiptPrintUrl,
  refundSettlement,
  settleOrder,
} from './billing';

// request 由 tests/jest.setup.ts 全局 mock；此处只做请求契约断言
const mocked = request as jest.Mock;

beforeEach(() => {
  mocked.mockClear();
});

describe('billing service contract', () => {
  it('pay type map covers all five pay types', () => {
    expect(PAY_TYPE).toEqual({
      STORED_VALUE: 1,
      WECHAT: 2,
      ALIPAY: 3,
      CASH: 4,
      CARD: 5,
    });
    expect(Object.keys(PAY_TYPE_TEXT).map(Number).sort()).toEqual([1, 2, 3, 4, 5]);
    for (const text of Object.values(PAY_TYPE_TEXT)) {
      expect(text.length).toBeGreaterThan(0);
    }
  });

  it('refund posts reason to settlement refund endpoint', async () => {
    await refundSettlement(7, '顾客取消订单');
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/settlement/7/refund',
      expect.objectContaining({
        method: 'POST',
        data: { reason: '顾客取消订单' },
      }),
    );
  });

  it('reconciliation reads by date', async () => {
    await fetchReconciliation('2026-10-11');
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/settlement/reconciliation',
      expect.objectContaining({
        method: 'GET',
        params: { date: '2026-10-11' },
      }),
    );
  });

  it('settle posts orderId and payType', async () => {
    await settleOrder({ orderId: 3, payType: PAY_TYPE.CASH });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/settlement/',
      expect.objectContaining({
        method: 'POST',
        data: { orderId: 3, payType: 4 },
      }),
    );
  });

  it('recharge posts flow and balance reads by customer', async () => {
    await createRecharge({ customerId: 8, money: 500 });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/recharge/',
      expect.objectContaining({
        method: 'POST',
        data: { customerId: 8, money: 500 },
      }),
    );
    await fetchRechargeBalance(8);
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/recharge/balance',
      expect.objectContaining({
        method: 'GET',
        params: { customerId: 8 },
      }),
    );
  });

  it('receipt url points to print endpoint', () => {
    expect(receiptPrintUrl(5)).toBe('/api/v1/print/receipt/5');
  });
});
