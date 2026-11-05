import { errorConfig } from '@/requestErrorConfig';

// @umijs/max 全局 mock 由 tests/jest.setup.ts 提供 request；此处补充 history 供 401 跳转断言
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(() => Promise.resolve({})),
}));

jest.mock('antd', () => ({
  message: { error: jest.fn(), warning: jest.fn(), success: jest.fn() },
  notification: { open: jest.fn() },
}));

import { history } from '@umijs/max';
import { message } from 'antd';

const push = history.push as jest.Mock;
const msgError = message.error as jest.Mock;

describe('requestErrorConfig 401 处理', () => {
  beforeEach(() => {
    push.mockClear();
    msgError.mockClear();
    localStorage.clear();
  });

  it('401 清 jwt 并跳登录页（非登录请求）', () => {
    localStorage.setItem('jwt', 'tok');
    errorConfig.errorConfig?.errorHandler?.(
      {
        name: 'AxiosError',
        response: { status: 401 },
        config: { url: '/api/v1/customer/page' },
      } as any,
      {},
    );
    expect(localStorage.getItem('jwt')).toBeNull();
    expect(push).toHaveBeenCalledWith('/user/login');
    expect(msgError).not.toHaveBeenCalled();
  });

  it('401 不跳登录页（登录请求自身失败，避免回环）', () => {
    errorConfig.errorConfig?.errorHandler?.(
      {
        name: 'AxiosError',
        response: { status: 401 },
        config: { url: '/api/v1/user/login' },
      } as any,
      {},
    );
    expect(push).not.toHaveBeenCalled();
  });

  it('非 401 维持原 toast 行为，不跳转', () => {
    errorConfig.errorConfig?.errorHandler?.(
      {
        name: 'AxiosError',
        response: { status: 500, data: { errorMessage: '服务器错误' } },
        config: { url: '/api/v1/customer/page' },
      } as any,
      {},
    );
    expect(msgError).toHaveBeenCalledWith('服务器错误');
    expect(push).not.toHaveBeenCalled();
  });
});
