import { request } from '@umijs/max';
import {
  createOrder,
  fetchOrder,
  orderBusinessNo,
  updateOrderRemark,
  updateOrderStatus,
} from './order';

// request 由 tests/jest.setup.ts 全局 mock；此处只做请求契约断言
const mocked = request as jest.Mock;

beforeEach(() => {
  mocked.mockClear();
});

describe('order service contract', () => {
  it('multi-line create posts items with qty', async () => {
    await createOrder({
      customerId: 8,
      items: [
        { itemId: 3, qty: 1 },
        { itemId: 4, qty: 2 },
      ],
    });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/order/',
      expect.objectContaining({
        method: 'POST',
        data: {
          customerId: 8,
          items: [
            { itemId: 3, qty: 1 },
            { itemId: 4, qty: 2 },
          ],
        },
      }),
    );
  });

  it('remark put targets order remark endpoint', async () => {
    await updateOrderRemark({ id: 6, remark: '代煎 7 袋，前两袋先试饮' });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/order/remark',
      expect.objectContaining({
        method: 'PUT',
        data: { id: 6, remark: '代煎 7 袋，前两袋先试饮' },
      }),
    );
  });

  it('status flow and detail read by id', async () => {
    await updateOrderStatus({ id: 2, status: 1 });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/order/status',
      expect.objectContaining({ method: 'PUT', data: { id: 2, status: 1 } }),
    );
    await fetchOrder(2);
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/order/2',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('business no is create date plus padded id', () => {
    expect(orderBusinessNo({ id: 6, createTime: '2026-10-08 15:00:00' })).toBe('20261008-006');
    expect(orderBusinessNo({ id: 12, createTime: '2026-01-02T09:30:00' })).toBe('20260102-012');
    expect(orderBusinessNo({ id: 5 })).toBe('005');
    expect(orderBusinessNo()).toBe('-');
  });
});
