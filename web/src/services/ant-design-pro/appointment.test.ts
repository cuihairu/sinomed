import { request } from '@umijs/max';
import {
  createAppointment,
  deleteAppointment,
  queryAppointmentPage,
  updateAppointmentStatus,
  APPOINTMENT_STATUS,
} from './appointment';

// request 由 tests/jest.setup.ts 全局 mock；此处只做请求契约断言
const mocked = request as jest.Mock;

beforeEach(() => {
  mocked.mockClear();
});

describe('appointment service contract', () => {
  it('create posts to appointment root', async () => {
    await createAppointment({
      customerId: 8,
      itemId: 3,
      staffId: 1,
      startTime: '2026-10-12 10:00:00',
      duration: 60,
      remark: '肩颈不适',
    });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/appointment/',
      expect.objectContaining({
        method: 'POST',
        data: {
          customerId: 8,
          itemId: 3,
          staffId: 1,
          startTime: '2026-10-12 10:00:00',
          duration: 60,
          remark: '肩颈不适',
        },
      }),
    );
  });

  it('status flow covers receive, no-show, cancel', async () => {
    expect(APPOINTMENT_STATUS.NO_SHOW).toBe(8);
    await updateAppointmentStatus({ id: 12, status: APPOINTMENT_STATUS.RECEIVED });
    await updateAppointmentStatus({ id: 12, status: APPOINTMENT_STATUS.NO_SHOW });
    await updateAppointmentStatus({ id: 12, status: APPOINTMENT_STATUS.CANCELLED });
    expect(mocked).toHaveBeenNthCalledWith(
      1,
      '/api/v1/appointment/status',
      expect.objectContaining({ method: 'PUT', data: { id: 12, status: 1 } }),
    );
    expect(mocked).toHaveBeenNthCalledWith(
      2,
      '/api/v1/appointment/status',
      expect.objectContaining({ method: 'PUT', data: { id: 12, status: 8 } }),
    );
    expect(mocked).toHaveBeenNthCalledWith(
      3,
      '/api/v1/appointment/status',
      expect.objectContaining({ method: 'PUT', data: { id: 12, status: 9 } }),
    );
  });

  it('page query passes status filter and date', async () => {
    await queryAppointmentPage({ current: 2, pageSize: 20, status: 8, date: '2026-10-11' });
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/appointment/page',
      expect.objectContaining({
        method: 'GET',
        params: { current: 2, pageSize: 20, status: 8, date: '2026-10-11' },
      }),
    );
  });

  it('delete targets appointment id path', async () => {
    await deleteAppointment(12);
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/appointment/12',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });
});
