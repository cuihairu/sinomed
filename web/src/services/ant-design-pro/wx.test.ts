import { request } from '@umijs/max';
import { unbindFan, pushMenus } from './wx';

// request 由 tests/jest.setup.ts 全局 mock；此处只做请求契约断言
const mocked = request as jest.Mock;

beforeEach(() => {
  mocked.mockClear();
});

describe('wx menu service contract', () => {
  it('pushMenus posts enabled menus of an account to the push endpoint', async () => {
    mocked.mockResolvedValueOnce({ message: '已推送 3 个一级菜单到微信' });
    const resp = await pushMenus(7);
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/wx/menus/push/7',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(resp.message).toContain('3');
  });
});

describe('wx fan service contract', () => {
  it('unbindFan posts the fan openid to the admin unbind endpoint', async () => {
    mocked.mockResolvedValueOnce({ message: '已解除粉丝与顾客档案的绑定' });
    const resp = await unbindFan(3, 'oFan001');
    expect(mocked).toHaveBeenCalledWith(
      '/api/v1/wx/fans/3/unbind',
      expect.objectContaining({ method: 'POST', data: { openid: 'oFan001' } }),
    );
    expect(resp.message).toContain('解除');
  });
});
