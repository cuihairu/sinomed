import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Spin, Table, Tabs, Tag, message } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createAutoReply,
  createMenu,
  deleteAutoReply,
  deleteMenu,
  fetchMaterialBlob,
  pushMenus,
  queryAutoRepliesByPage,
  queryCapabilities,
  queryMaterialsByPage,
  queryMenus,
  queryMessageLogs,
  queryWxAccounts,
  updateAutoReply,
  updateMenu,
  type WxAccount,
  type WxAutoReply,
  type WxCapabilities,
  type WxMaterial,
  type WxMenu,
  type WxMessageLog,
} from '@/services/ant-design-pro/wx';
import moment from 'moment';

/** 规则类型标签（服务端校验同口径：subscribe/keyword/fallback） */
const RULE_TYPE_LABELS: Record<string, string> = {
  subscribe: '关注欢迎语',
  keyword: '关键词',
  fallback: '兜底回复',
};
const RULE_TYPES = Object.keys(RULE_TYPE_LABELS);

/** subscribe/fallback 同账号同时最多一条启用（服务层约束；新建时若已有启用规则需先停用旧的） */
const SINGLE_ENABLED_TYPES = ['subscribe', 'fallback'];

const MATCH_TYPE_LABELS: Record<string, string> = {
  exact: '全等',
  contains: '包含',
};

/** 方向 / 类型 / 结果 / 素材类型标签（与 docs/server/api.md 台账口径一致；ENUM 供筛选下拉） */
const DIRECTION_LABELS: Record<string, string> = { in: '入站', out: '出站', reject: '拒绝' };
const DIRECTION_ENUM = { in: { text: '入站' }, out: { text: '出站' }, reject: { text: '拒绝' } };
const MSG_TYPE_LABELS: Record<string, string> = {
  text: '文本',
  event: '事件',
  image: '图片',
  oauth: '授权',
  none: '无',
};
const MSG_TYPE_ENUM = {
  text: { text: '文本' },
  event: { text: '事件' },
  image: { text: '图片' },
  oauth: { text: '授权' },
  none: { text: '无' },
};
const RESULT_LABELS: Record<string, string> = {
  ok: '正常',
  disabled: '模块关闭',
  account_missing: '账号缺失',
  bad_signature: '验签失败',
  bad_msg_signature: '信封验签失败',
  bad_encrypt: '解密失败',
  missing_encrypt: '明文推送被拒',
  duplicate: '重复推送',
};
const RESULT_ENUM = {
  ok: { text: '正常' },
  disabled: { text: '模块关闭' },
  account_missing: { text: '账号缺失' },
  bad_signature: { text: '验签失败' },
  bad_msg_signature: { text: '信封验签失败' },
  bad_encrypt: { text: '解密失败' },
  missing_encrypt: { text: '明文推送被拒' },
  duplicate: { text: '重复推送' },
};
const MATERIAL_TYPE_LABELS: Record<string, string> = {
  temp: '临时素材',
  image: '图片',
  voice: '语音',
  video: '视频',
  news: '图文',
};
const MATERIAL_TYPE_ENUM = {
  temp: { text: '临时素材' },
  image: { text: '图片' },
  voice: { text: '语音' },
  video: { text: '视频' },
  news: { text: '图文' },
};

/** 菜单类型标签（微信协议枚举；一级仅 view/click，服务端同口径校验） */
const MENU_TYPE_LABELS: Record<string, string> = {
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
const TOP_MENU_TYPES = ['view', 'click'];

/** 三个 tab 都需要「账号下拉 + 行内显示账号名」，口径统一 */
const useAccountOptions = () => {
  const [accounts, setAccounts] = useState<WxAccount[]>([]);
  useEffect(() => {
    queryWxAccounts().then((list) => setAccounts(list || []));
  }, []);
  const options = useMemo(() => accounts.map((a) => ({ value: a.id!, label: a.accountName || a.appId || `#${a.id}` })), [accounts]);
  const nameOf = (id?: number) =>
    accounts.find((a) => a.id === id)?.accountName || (id != null ? `#${id}` : '-');
  return { accounts, options, nameOf };
};

/** 时间范围列（只进搜索表单，不出现在表格里）→ 服务端 startTime/endTime */
const timeRangeColumn = (key: string): ProColumns<any> => ({
  title: '时间范围',
  dataIndex: key,
  valueType: 'dateTimeRange',
  hideInTable: true,
  search: { transform: (value: any) => ({ startTime: value?.[0], endTime: value?.[1] }) },
});

const MessageLogsTab: React.FC = () => {
  const { options, nameOf } = useAccountOptions();

  const columns: ProColumns<WxMessageLog>[] = [
    { title: '时间', dataIndex: 'createTime', width: 150, hideInSearch: true,
      render: (_, entity) => (entity.createTime ? moment(entity.createTime).format('YYYY-MM-DD HH:mm:ss') : '-') },
    { title: '账号', dataIndex: 'accountId', width: 130, valueType: 'select', fieldProps: { options, showSearch: true },
      render: (_, entity) => nameOf(entity.accountId) },
    { title: '方向', dataIndex: 'direction', width: 90, valueType: 'select',
      valueEnum: DIRECTION_ENUM,
      render: (_, entity) => <Tag>{DIRECTION_LABELS[entity.direction || ''] || entity.direction}</Tag> },
    { title: '类型', dataIndex: 'msgType', width: 90, valueType: 'select',
      valueEnum: MSG_TYPE_ENUM,
      render: (_, entity) => MSG_TYPE_LABELS[entity.msgType || ''] || entity.msgType || '-' },
    { title: '粉丝', dataIndex: 'fromUser', ellipsis: true },
    { title: '顾客', dataIndex: 'customerName', width: 110, hideInSearch: true,
      render: (_, entity) => entity.customerName || <span style={{ color: '#aaa' }}>未绑定</span> },
    { title: '内容', dataIndex: 'content', ellipsis: true, hideInSearch: true },
    { title: '结果', dataIndex: 'result', width: 130, valueType: 'select',
      valueEnum: RESULT_ENUM,
      render: (_, entity) => <Tag color={entity.result === 'ok' ? 'green' : entity.direction === 'reject' ? 'red' : 'default'}>
        {RESULT_LABELS[entity.result || ''] || entity.result}
      </Tag> },
    timeRangeColumn('createTime'),
  ];

  return (
    <ProTable<WxMessageLog>
      rowKey="id"
      columns={columns}
      headerTitle="消息流水（含拒绝审计，最新在前）"
      dateFormatter="string"
      search={{ labelWidth: 'auto' }}
      request={async (params) => {
        // 时间范围列经 search.transform 已折成 startTime/endTime，其余键与服务端参数同名直传
        const res = await queryMessageLogs(params as any);
        return { data: res?.data || [], success: true, total: res?.total || 0 };
      }}
      pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
      locale={{ emptyText: '暂无消息流水（公众号模块未启用或尚未收到消息）' }}
    />
  );
};

const MaterialsTab: React.FC = () => {
  const { options, nameOf } = useAccountOptions();
  const [preview, setPreview] = useState<{ url: string; name: string; mime: string }>();

  const openPreview = async (material: WxMaterial) => {
    try {
      const blob = await fetchMaterialBlob(material.id!);
      setPreview({
        url: URL.createObjectURL(blob),
        name: material.name || material.mediaId || `素材 #${material.id}`,
        mime: material.mimeType || 'application/octet-stream',
      });
    } catch {
      message.error('素材读取失败（本地副本可能已丢失）');
    }
  };

  const closePreview = () => {
    if (preview) URL.revokeObjectURL(preview.url);
    setPreview(undefined);
  };

  const columns: ProColumns<WxMaterial>[] = [
    { title: '时间', dataIndex: 'createTime', width: 150, hideInSearch: true,
      render: (_, entity) => (entity.createTime ? moment(entity.createTime).format('YYYY-MM-DD HH:mm:ss') : '-') },
    { title: '账号', dataIndex: 'accountId', width: 130, valueType: 'select', fieldProps: { options, showSearch: true },
      render: (_, entity) => nameOf(entity.accountId) },
    { title: '类型', dataIndex: 'materialType', width: 110, valueType: 'select',
      valueEnum: MATERIAL_TYPE_ENUM,
      render: (_, entity) => MATERIAL_TYPE_LABELS[entity.materialType || ''] || entity.materialType || '-' },
    { title: '素材', dataIndex: 'name', ellipsis: true,
      render: (_, entity) => entity.name || entity.mediaId || '-' },
    { title: '媒体ID', dataIndex: 'mediaId', width: 180, ellipsis: true, hideInSearch: true,
      render: (_, entity) => entity.mediaId || '-' },
    { title: '大小', dataIndex: 'size', width: 100, hideInSearch: true,
      render: (_, entity) => (entity.size != null ? `${(entity.size / 1024).toFixed(1)} KB` : '-') },
    { title: '本地副本', dataIndex: 'localPath', width: 110, hideInSearch: true,
      render: (_, entity) => (entity.localPath ? <Tag color="green">有</Tag> : <Tag>无</Tag>) },
    { title: '查看', key: 'view', width: 90, hideInSearch: true,
      render: (_, entity) =>
        entity.localPath ? (
          <a onClick={() => openPreview(entity)}>查看</a>
        ) : (
          <span style={{ color: '#aaa' }}>—</span>
        ) },
    timeRangeColumn('createTime'),
  ];

  return (
    <>
      <ProTable<WxMaterial>
        rowKey="id"
        columns={columns}
        headerTitle="素材台账（粉丝发图落盘副本，最新在前）"
        dateFormatter="string"
        search={{ labelWidth: 'auto' }}
        request={async (params) => {
          // 同消息流水：时间范围列由 search.transform 折成 startTime/endTime
          const res = await queryMaterialsByPage(params as any);
          return { data: res?.data || [], success: true, total: res?.total || 0 };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
        locale={{ emptyText: '暂无素材（粉丝发来图片消息后会落到这里）' }}
      />
      <Modal title={preview?.name} open={preview != null} onCancel={closePreview} footer={null} width={720} destroyOnClose>
        {preview &&
          (preview.mime.startsWith('image/') ? (
            <img src={preview.url} alt={preview.name} style={{ maxWidth: '100%' }} />
          ) : (
            <p>
              非图片素材，
              <a href={preview.url} target="_blank" rel="noreferrer">点此打开</a>
            </p>
          ))}
      </Modal>
    </>
  );
};

/** 菜单名权重（微信口径「N 汉字或 2N 字母」）：非 ASCII 记 2 分、ASCII 记 1 分，与服务端同式 */
const menuNameWeight = (s: string) => [...s].reduce((w, c) => w + (c.charCodeAt(0) > 127 ? 2 : 1), 0);

const MenusTab: React.FC = () => {
  const { options, accounts } = useAccountOptions();
  const [accountId, setAccountId] = useState<number>();
  const [menus, setMenus] = useState<WxMenu[]>([]);
  const [loading, setLoading] = useState(false);
  const [menuModal, setMenuModal] = useState<{ open: boolean; editing?: WxMenu }>({ open: false });
  const [caps, setCaps] = useState<WxCapabilities | 'unknown'>('unknown');
  const [pushing, setPushing] = useState(false);

  const [menuForm] = Form.useForm();
  const watchMenuType = Form.useWatch('menuType', menuForm) as WxMenu['menuType'] | undefined;
  const watchParentId = Form.useWatch('parentId', menuForm);
  const isTop = watchParentId == null;

  const loadMenus = useCallback(async () => {
    if (accountId == null) return;
    setLoading(true);
    try {
      setMenus((await queryMenus({ accountId })) || []);
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    // 单店一期通常单账号：默认选中第一个，手工切换
    if (accounts.length === 0) return;
    if (accountId == null) setAccountId(accounts[0].id!);
  }, [accounts, accountId]);

  useEffect(() => {
    loadMenus();
  }, [loadMenus]);

  // 能力矩阵（§6.2）：未认证订阅号 menuManagement=false → 隐藏工作区 + 提示；拉取失败按「不允许」处理不闪编辑器
  useEffect(() => {
    if (accountId == null) {
      setCaps('unknown');
      return;
    }
    let alive = true;
    setCaps('unknown');
    queryCapabilities(accountId)
      .then((c) => {
        if (alive) setCaps(c || {});
      })
      .catch(() => {
        if (alive) setCaps({});
      });
    return () => {
      alive = false;
    };
  }, [accountId]);

  const capsLoading = caps === 'unknown' && accountId != null;
  const menuAllowed = caps !== 'unknown' && caps.menuManagement === true;

  /** 平铺转树：顶级行挂 children（antd Table 树形），无子菜单的顶级保持普通行 */
  const treeData = useMemo(() => {
    const tops = menus.filter((m) => m.parentId == null);
    return tops.map((t) => {
      const kids = menus.filter((m) => m.parentId === t.id);
      return kids.length ? { ...t, children: kids } : t;
    });
  }, [menus]);

  const topOptions = useMemo(
    () => menus
      .filter((m) => m.parentId == null)
      .map((t) => ({ value: t.id!, label: t.name || `#${t.id}` })),
    [menus],
  );

  const openCreate = (parentId?: number) => {
    setMenuModal({ open: true });
    menuForm.resetFields();
    menuForm.setFieldsValue({ menuType: 'view', sort: 0, enabled: 1, parentId });
  };

  const openEdit = (entity: WxMenu) => {
    setMenuModal({ open: true, editing: entity });
    menuForm.setFieldsValue(entity);
  };

  /** 推送启用菜单到微信（设计稿 §4.3「推送」半边）：服务端只推 enabled=1 并透传微信 errcode，这里原样提示 */
  const pushToWechat = async () => {
    if (accountId == null) {
      message.error('请先选择公众号账号');
      return;
    }
    setPushing(true);
    try {
      const res = await pushMenus(accountId);
      if (res?.message) message.success(res.message);
    } catch (e: any) {
      message.error(e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '推送失败');
    } finally {
      setPushing(false);
    }
  };

  const saveMenuRow = async () => {
    if (accountId == null) {
      message.error('请先选择公众号账号');
      return;
    }
    const values = await menuForm.validateFields();
    const payload: WxMenu = { ...values, accountId, parentId: values.parentId ?? null };
    try {
      const res = menuModal.editing
        ? await updateMenu({ ...menuModal.editing, ...payload })
        : await createMenu(payload);
      if (res?.id) {
        message.success(menuModal.editing ? '已更新' : '已创建');
        setMenuModal({ open: false });
        loadMenus();
      }
    } catch (e: any) {
      // 服务端校验拒绝（结构/容量/必填字段）原样透出，不静默
      message.error(e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '保存失败');
    }
  };

  const toggleMenu = async (entity: WxMenu) => {
    const next = entity.enabled === 1 ? 0 : 1;
    try {
      const res = await updateMenu({ ...entity, enabled: next });
      if (res?.id) {
        message.success(next === 1 ? '已启用' : '已转草稿');
        loadMenus();
      }
    } catch (e: any) {
      message.error(e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '操作失败');
    }
  };

  const removeMenuRow = async (entity: WxMenu) => {
    try {
      await deleteMenu(entity.id!);
      message.success('已删除');
      loadMenus();
    } catch (e: any) {
      // 父菜单带子菜单时服务端拒绝（先删子）
      message.error(e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '删除失败');
    }
  };

  const columns = useMemo(
    () => [
      {
        title: '菜单名',
        dataIndex: 'name',
        width: 200,
      },
      {
        title: '类型',
        dataIndex: 'menuType',
        width: 110,
        render: (v: string) => <Tag>{MENU_TYPE_LABELS[v] || v}</Tag>,
      },
      {
        title: '跳转 / 事件',
        key: 'target',
        ellipsis: true,
        render: (_: unknown, entity: WxMenu) =>
          entity.menuType === 'view' ? (
            <span>{entity.url}</span>
          ) : entity.menuType === 'click' ? (
            <span>{entity.eventKey}</span>
          ) : entity.menuType === 'view_miniprogram' ? (
            <span>
              {entity.miniprogramAppid} / {entity.miniprogramPagepath}
            </span>
          ) : (
            <span style={{ color: '#aaa' }}>—</span>
          ),
      },
      {
        title: '顺序',
        dataIndex: 'sort',
        width: 70,
        render: (v: number) => v ?? 0,
      },
      {
        title: '状态',
        dataIndex: 'enabled',
        width: 90,
        render: (_: unknown, entity: WxMenu) => (
          <Popconfirm
            title={entity.enabled === 1 ? '转为草稿？' : '启用该菜单？'}
            onConfirm={() => toggleMenu(entity)}
          >
            <Tag color={entity.enabled === 1 ? 'green' : 'default'}>
              {entity.enabled === 1 ? '启用' : '草稿'}
            </Tag>
          </Popconfirm>
        ),
      },
      {
        title: '操作',
        key: 'actions',
        width: 190,
        render: (_: unknown, entity: WxMenu) => (
          <>
            {entity.parentId == null && (
              <a onClick={() => openCreate(entity.id)}>添加二级</a>
            )}
            <a style={{ marginLeft: 12 }} onClick={() => openEdit(entity)}>
              编辑
            </a>
            <Popconfirm title="删除该菜单？（带子菜单需先删子）" onConfirm={() => removeMenuRow(entity)}>
              <a style={{ marginLeft: 12, color: '#cf1322' }}>删除</a>
            </Popconfirm>
          </>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [menus],
  );

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Select
          style={{ width: 260 }}
          placeholder="选择公众号账号"
          value={accountId}
          onChange={(v) => {
            setAccountId(v);
            setMenus([]);
          }}
          options={options}
        />
        <Button
          type="primary"
          disabled={accountId == null || !menuAllowed}
          onClick={() => openCreate()}
          style={{ marginLeft: 'auto' }}
        >
          新建一级菜单
        </Button>
        <Popconfirm
          title="推送启用菜单到微信？"
          description="只推送启用的一级菜单（草稿不推送）；未配 AppSecret 或微信侧报错会在提示里原样透出"
          onConfirm={pushToWechat}
          okText="推送"
          cancelText="取消"
        >
          <Button type="primary" disabled={accountId == null || !menuAllowed} loading={pushing}>
            推送微信
          </Button>
        </Popconfirm>
      </div>

      {capsLoading ? (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin tip="正在拉取账号能力矩阵…" />
        </div>
      ) : menuAllowed ? (
        <Table<WxMenu>
          rowKey="id"
          loading={loading}
          dataSource={treeData}
          columns={columns}
          pagination={false}
          size="middle"
          locale={{ emptyText: accountId == null ? '暂无公众号账号（先在 wx_accounts 落账号行）' : '暂无菜单（草稿态随时可编辑，推送微信需凭据）' }}
        />
      ) : (
        <Alert
          type="warning"
          showIcon
          message="该账号不支持自定义菜单"
          description="未认证订阅号不支持自定义菜单（能力矩阵由 account_type 与 verified 驱动，见设计稿 §6.1/§6.2）——需服务号或已认证账号；能力缺失是账号属性，可在账号设置里调整后回来。"
        />
      )}

      <Modal
        title={menuModal.editing ? '编辑菜单' : '新建菜单'}
        open={menuModal.open}
        onOk={saveMenuRow}
        onCancel={() => setMenuModal({ open: false })}
        destroyOnClose
        width={520}
      >
        <Form form={menuForm} layout="vertical">
          <Form.Item name="parentId" label="父菜单" extra={isTop ? '留空即一级菜单（每号最多 3 个）' : '二级菜单（每个一级下最多 5 个）'}>
            <Select allowClear placeholder="不选=一级菜单" options={topOptions} />
          </Form.Item>
          <Form.Item
            name="name"
            label="菜单名"
            rules={[
              { required: true, whitespace: true, message: '菜单名不能为空' },
              {
                validator: async (_: unknown, value?: string) => {
                  if (!value || !value.trim()) return;
                  const limit = watchParentId != null ? 16 : 8;
                  if (menuNameWeight(value.trim()) > limit) {
                    throw new Error(
                      watchParentId != null
                        ? '二级菜单名最长 8 个汉字（16 个字母等效）'
                        : '一级菜单名最长 4 个汉字（8 个字母等效）',
                    );
                  }
                },
              },
            ]}
          >
            <Input placeholder={isTop ? '如：预约挂号' : '如：皮肤科'} />
          </Form.Item>
          <Form.Item
            name="menuType"
            label="菜单类型"
            rules={[{ required: true, message: '请选择菜单类型' }]}
            extra={isTop ? '一级菜单仅支持 跳转网页 / 点击推事件（微信协议）' : undefined}
          >
            <Select
              options={(isTop ? TOP_MENU_TYPES : Object.keys(MENU_TYPE_LABELS)).map((t) => ({
                value: t,
                label: MENU_TYPE_LABELS[t],
              }))}
            />
          </Form.Item>
          {watchMenuType === 'view' && (
            <Form.Item
              name="url"
              label="跳转地址"
              rules={[
                { required: true, whitespace: true, message: 'view 菜单必须填写跳转地址' },
                {
                  validator: async (_: unknown, value?: string) => {
                    if (value && !(value.startsWith('http://') || value.startsWith('https://'))) {
                      throw new Error('地址必须以 http:// 或 https:// 开头');
                    }
                  },
                },
              ]}
            >
              <Input placeholder="https://clinic.example.com/..." />
            </Form.Item>
          )}
          {watchMenuType === 'click' && (
            <Form.Item
              name="eventKey"
              label="事件 Key"
              rules={[{ required: true, whitespace: true, message: 'click 菜单必须填写事件 key' }]}
              extra="粉丝点击后回调携带该 key（目前按兜底规则回复）"
            >
              <Input placeholder="如：OPEN_HOURS" maxLength={64} />
            </Form.Item>
          )}
          {watchMenuType === 'view_miniprogram' && (
            <>
              <Form.Item
                name="miniprogramAppid"
                label="小程序 AppID"
                rules={[{ required: true, whitespace: true, message: '必须填写小程序 appid' }]}
                extra="可指向本仓小程序（app/project.config.json 里的 appid）"
              >
                <Input placeholder="wx..." />
              </Form.Item>
              <Form.Item
                name="miniprogramPagepath"
                label="小程序页面路径"
                rules={[{ required: true, whitespace: true, message: '必须填写页面路径' }]}
              >
                <Input placeholder="pages/index/index" />
              </Form.Item>
            </>
          )}
          <Form.Item name="sort" label="展示顺序" initialValue={0} extra="数字小的排前面">
            <InputNumber min={0} max={99} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item
            name="enabled"
            label="状态"
            initialValue={1}
            extra="草稿不推送微信；菜单推送微信半边仍依附公众号凭据（第二期）"
          >
            <Select
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '草稿' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const RulesTab: React.FC = () => {
  const { options, accounts } = useAccountOptions();
  const [accountId, setAccountId] = useState<number>();
  const [rules, setRules] = useState<WxAutoReply[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterRuleType, setFilterRuleType] = useState<string>();
  const [filterEnabled, setFilterEnabled] = useState<number>();

  const [ruleForm] = Form.useForm();
  const watchRuleType = Form.useWatch('ruleType', ruleForm) as WxAutoReply['ruleType'] | undefined;
  const [ruleModal, setRuleModal] = useState<{ open: boolean; editing?: WxAutoReply }>({ open: false });

  const loadRules = useCallback(async () => {
    if (accountId == null) return;
    setLoading(true);
    try {
      const res = await queryAutoRepliesByPage({
        current: 1,
        pageSize: 100,
        accountId,
        ruleType: filterRuleType,
        enabled: filterEnabled,
      });
      setRules(res?.data || []);
    } finally {
      setLoading(false);
    }
  }, [accountId, filterRuleType, filterEnabled]);

  useEffect(() => {
    // 单店一期通常单账号：默认选中第一个，手工切换
    if (accounts.length === 0) return;
    if (accountId == null) setAccountId(accounts[0].id!);
  }, [accounts, accountId]);

  useEffect(() => {
    loadRules();
  }, [loadRules]);

  const openCreate = () => {
    setRuleModal({ open: true });
    ruleForm.resetFields();
    ruleForm.setFieldsValue({ ruleType: 'keyword', matchType: 'exact', priority: 0 });
  };

  const openEdit = (entity: WxAutoReply) => {
    setRuleModal({ open: true, editing: entity });
    ruleForm.setFieldsValue(entity);
  };

  const saveRule = async () => {
    if (accountId == null) {
      message.error('请先选择公众号账号');
      return;
    }
    const values = await ruleForm.validateFields();
    const payload: WxAutoReply = { ...values, accountId };
    const res = ruleModal.editing
      ? await updateAutoReply({ ...ruleModal.editing, ...payload })
      : await createAutoReply(payload);
    if (res?.id) {
      message.success(ruleModal.editing ? '已更新' : '已创建');
      setRuleModal({ open: false });
      loadRules();
    }
  };

  const toggleRule = async (entity: WxAutoReply) => {
    const next = entity.enabled === 1 ? 0 : 1;
    const res = await updateAutoReply({ ...entity, enabled: next });
    if (res?.id) {
      message.success(next === 1 ? '已启用' : '已停用');
      loadRules();
    }
  };

  const removeRule = async (entity: WxAutoReply) => {
    await deleteAutoReply(entity.id!);
    message.success('已删除');
    loadRules();
  };

  const columns = useMemo(
    () => [
      { title: '优先级', dataIndex: 'priority', width: 80, render: (v: number) => v ?? 0 },
      {
        title: '类型',
        dataIndex: 'ruleType',
        width: 110,
        render: (v: string) => RULE_TYPE_LABELS[v] || v,
      },
      {
        title: '关键词 / 匹配',
        dataIndex: 'keyword',
        width: 200,
        render: (_: unknown, entity: WxAutoReply) =>
          entity.ruleType === 'keyword' ? (
            <span>
              {entity.keyword}{' '}
              <Tag>{MATCH_TYPE_LABELS[entity.matchType || 'exact'] || entity.matchType}</Tag>
            </span>
          ) : (
            <span style={{ color: '#aaa' }}>—</span>
          ),
      },
      {
        title: '回复内容',
        dataIndex: 'content',
        ellipsis: true,
      },
      {
        title: '状态',
        dataIndex: 'enabled',
        width: 90,
        render: (_: unknown, entity: WxAutoReply) => (
          <Popconfirm
            title={entity.enabled === 1 ? '停用该规则？' : '启用该规则？'}
            onConfirm={() => toggleRule(entity)}
            disabled={
              entity.enabled !== 1 &&
              SINGLE_ENABLED_TYPES.includes(entity.ruleType || '') &&
              rules.some(
                (r) =>
                  r.enabled === 1 &&
                  r.ruleType === entity.ruleType &&
                  r.id !== entity.id,
              )
            }
          >
            <Tag color={entity.enabled === 1 ? 'green' : 'default'}>
              {entity.enabled === 1 ? '启用' : '停用'}
            </Tag>
          </Popconfirm>
        ),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 150,
        render: (v: string) => (v ? moment(v).format('YYYY-MM-DD HH:mm') : '-'),
      },
      {
        title: '操作',
        key: 'actions',
        width: 130,
        render: (_: unknown, entity: WxAutoReply) => (
          <>
            <a onClick={() => openEdit(entity)}>编辑</a>
            <Popconfirm title="删除该规则？" onConfirm={() => removeRule(entity)}>
              <a style={{ marginLeft: 12, color: '#cf1322' }}>删除</a>
            </Popconfirm>
          </>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rules],
  );

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Select
          style={{ width: 260 }}
          placeholder="选择公众号账号"
          value={accountId}
          onChange={setAccountId}
          options={options}
        />
        <Select
          style={{ width: 140 }}
          allowClear
          placeholder="全部类型"
          value={filterRuleType}
          onChange={setFilterRuleType}
          options={RULE_TYPES.map((t) => ({ value: t, label: RULE_TYPE_LABELS[t] }))}
        />
        <Select
          style={{ width: 120 }}
          allowClear
          placeholder="全部状态"
          value={filterEnabled}
          onChange={setFilterEnabled}
          options={[
            { value: 1, label: '启用' },
            { value: 0, label: '停用' },
          ]}
        />
        <Button type="primary" disabled={accountId == null} onClick={openCreate} style={{ marginLeft: 'auto' }}>
          新建规则
        </Button>
      </div>

      <Table<WxAutoReply>
        rowKey="id"
        loading={loading}
        dataSource={rules}
        columns={columns}
        pagination={false}
        size="middle"
        locale={{ emptyText: accountId == null ? '暂无公众号账号（先在 wx_accounts 落账号行）' : '暂无规则' }}
      />

      <Modal
        title={ruleModal.editing ? '编辑规则' : '新建规则'}
        open={ruleModal.open}
        onOk={saveRule}
        onCancel={() => setRuleModal({ open: false })}
        destroyOnClose
        width={520}
      >
        <Form form={ruleForm} layout="vertical">
          <Form.Item
            name="ruleType"
            label="规则类型"
            rules={[{ required: true, message: '请选择规则类型' }]}
            extra={
              watchRuleType && SINGLE_ENABLED_TYPES.includes(watchRuleType)
                ? '该类型同账号同时只有一条启用（服务端校验：先停用旧规则再启用新的）'
                : undefined
            }
          >
            <Select options={RULE_TYPES.map((t) => ({ value: t, label: RULE_TYPE_LABELS[t] }))} />
          </Form.Item>
          {watchRuleType === 'keyword' && (
            <>
              <Form.Item
                name="keyword"
                label="关键词"
                rules={[{ required: true, whitespace: true, message: '关键词规则必须填写关键词' }]}
              >
                <Input maxLength={64} placeholder="如：营业时间" />
              </Form.Item>
              <Form.Item name="matchType" label="匹配方式" initialValue="exact">
                <Select
                  options={[
                    { value: 'exact', label: '全等（消息与关键词完全相同才命中）' },
                    { value: 'contains', label: '包含（消息里出现关键词即命中）' },
                  ]}
                />
              </Form.Item>
            </>
          )}
          <Form.Item
            name="content"
            label="回复内容"
            rules={[{ required: true, whitespace: true, message: '回复内容不能为空' }]}
          >
            <Input.TextArea rows={3} maxLength={600} showCount placeholder="粉丝命中规则后收到的文本回复" />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue={0} extra="数字小的先匹配；同账号关键词按此排序">
            <InputNumber min={0} max={9999} style={{ width: 120 }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const WxPage: React.FC = () => (
  <PageContainer
    title="公众号管理"
    content={<span>规则与菜单不再手工改库；消息流水与素材台账为只读（推送到微信仍依附公众号凭据）</span>}
  >
    <Tabs
      items={[
        { key: 'rules', label: '自动回复规则', children: <RulesTab /> },
        { key: 'menus', label: '自定义菜单', children: <MenusTab /> },
        { key: 'logs', label: '消息流水', children: <MessageLogsTab /> },
        { key: 'materials', label: '素材台账', children: <MaterialsTab /> },
      ]}
    />
  </PageContainer>
);

export default WxPage;
