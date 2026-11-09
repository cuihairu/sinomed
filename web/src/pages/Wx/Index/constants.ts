import type { ProColumns } from '@ant-design/pro-components';

/** 规则类型标签（服务端校验同口径：subscribe/keyword/fallback） */
export const RULE_TYPE_LABELS: Record<string, string> = {
  subscribe: '关注欢迎语',
  keyword: '关键词',
  fallback: '兜底回复',
};
export const RULE_TYPES = Object.keys(RULE_TYPE_LABELS);

/** subscribe/fallback 同账号同时最多一条启用（服务层约束；新建时若已有启用规则需先停用旧的） */
export const SINGLE_ENABLED_TYPES = ['subscribe', 'fallback'];

export const MATCH_TYPE_LABELS: Record<string, string> = {
  exact: '全等',
  contains: '包含',
};

/** 方向 / 类型 / 结果 / 素材类型标签（与 docs/server/api.md 台账口径一致；ENUM 供筛选下拉） */
export const DIRECTION_LABELS: Record<string, string> = {
  in: '入站',
  out: '出站',
  reject: '拒绝',
};
export const DIRECTION_ENUM = {
  in: { text: '入站' },
  out: { text: '出站' },
  reject: { text: '拒绝' },
};
export const MSG_TYPE_LABELS: Record<string, string> = {
  text: '文本',
  event: '事件',
  image: '图片',
  oauth: '授权',
  none: '无',
};
export const MSG_TYPE_ENUM = {
  text: { text: '文本' },
  event: { text: '事件' },
  image: { text: '图片' },
  oauth: { text: '授权' },
  none: { text: '无' },
};
export const RESULT_LABELS: Record<string, string> = {
  ok: '正常',
  disabled: '模块关闭',
  account_missing: '账号缺失',
  bad_signature: '验签失败',
  bad_msg_signature: '信封验签失败',
  bad_encrypt: '解密失败',
  missing_encrypt: '明文推送被拒',
  duplicate: '重复推送',
};
export const RESULT_ENUM = {
  ok: { text: '正常' },
  disabled: { text: '模块关闭' },
  account_missing: { text: '账号缺失' },
  bad_signature: { text: '验签失败' },
  bad_msg_signature: { text: '信封验签失败' },
  bad_encrypt: { text: '解密失败' },
  missing_encrypt: { text: '明文推送被拒' },
  duplicate: { text: '重复推送' },
};
export const MATERIAL_TYPE_LABELS: Record<string, string> = {
  temp: '临时素材',
  image: '图片',
  voice: '语音',
  video: '视频',
  news: '图文',
};
export const MATERIAL_TYPE_ENUM = {
  temp: { text: '临时素材' },
  image: { text: '图片' },
  voice: { text: '语音' },
  video: { text: '视频' },
  news: { text: '图文' },
};

/** 菜单类型标签（微信协议枚举；一级仅 view/click，服务端同口径校验） */
export const MENU_TYPE_LABELS: Record<string, string> = {
  view: '跳转网页',
  click: '点击推事件',
  scancode_push: '扫码推事件',
  scancode_waitmsg: '扫码等待',
  pic_sysphoto: '系统拍照',
  pic_photo_or_album: '拍照或相册',
  pic_weixin: '微信相册',
  location_select: '发送位置',
  view_miniprogram: '小程序',
};
export const TOP_MENU_TYPES = ['view', 'click'];

/** 时间范围列（只进搜索表单，不出现在表格里）→ 服务端 startTime/endTime */
export const timeRangeColumn = (key: string): ProColumns<any> => ({
  title: '时间范围',
  dataIndex: key,
  valueType: 'dateTimeRange',
  hideInTable: true,
  search: { transform: (value: any) => ({ startTime: value?.[0], endTime: value?.[1] }) },
});
