import { request } from '@umijs/max';
import { pushMenus } from './wx';

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
