import { request } from '@umijs/max';
import { createPrescription, createTemplate, setDecoctionStatus } from './prescription';

// request 由 tests/jest.setup.ts 全局 mock；此处只做请求契约断言
const mocked = request as jest.Mock;

beforeEach(() => {
  mocked.mockClear();
});

describe('prescription service contract', () => {
  it('create posts sign fields and delivery type', async () => {
    await createPrescription({
      customerId: 5,
      staffId: 2,
      reviewStaffId: 3,
      checkStaffId: 4,
      treatId: 8,
      doses: 7,
      decoction: true,
      deliveryType: 1,
      herbs: [{ herb: '党参', weight: 15, special: '先煎' }],
    });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/prescription/',
      expect.objectContaining({
        method: 'POST',
        data: {
          customerId: 5,
          staffId: 2,
          reviewStaffId: 3,
          checkStaffId: 4,
          treatId: 8,
          doses: 7,
          decoction: true,
          deliveryType: 1,
          herbs: [{ herb: '党参', weight: 15, special: '先煎' }],
        },
      }),
    );
  });

  it('save to formula library posts enabled template', async () => {
    await createTemplate({
      name: '脾胃虚寒',
      doses: 7,
      decoction: 1,
      enabled: 1,
      herbs: [{ herb: '白术', weight: 10 }],
    });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/prescription/template/',
      expect.objectContaining({
        method: 'POST',
        data: expect.objectContaining({ name: '脾胃虚寒', enabled: 1, decoction: 1 }),
      }),
    );
  });

  it('decoction flow advances by status param', async () => {
    await setDecoctionStatus(12, 2);
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/prescription/12/decoction?status=2',
      expect.objectContaining({ method: 'PUT' }),
    );
  });
});
