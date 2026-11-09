import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tag,
  message,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createMenu,
  deleteMenu,
  pushMenus,
  queryCapabilities,
  queryMenus,
  updateMenu,
  type WxCapabilities,
  type WxMenu,
} from '@/services/ant-design-pro/wx';
import { MENU_TYPE_LABELS, TOP_MENU_TYPES } from './constants';
import useAccountOptions from './useAccountOptions';

/** 菜单名权重（微信口径「N 汉字或 2N 字母」）：非 ASCII 记 2 分、ASCII 记 1 分，与服务端同式 */
const menuNameWeight = (s: string) =>
  [...s].reduce((w, c) => w + (c.charCodeAt(0) > 127 ? 2 : 1), 0);

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
    () =>
      menus
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
      message.error(
        e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '推送失败',
      );
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
      message.error(
        e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '保存失败',
      );
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
      message.error(
        e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '操作失败',
      );
    }
  };

  const removeMenuRow = async (entity: WxMenu) => {
    try {
      await deleteMenu(entity.id!);
      message.success('已删除');
      loadMenus();
    } catch (e: any) {
      // 父菜单带子菜单时服务端拒绝（先删子）
      message.error(
        e?.data?.errorMessage || e?.response?.data?.errorMessage || e?.message || '删除失败',
      );
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
            {entity.parentId == null && <a onClick={() => openCreate(entity.id)}>添加二级</a>}
            <a style={{ marginLeft: 12 }} onClick={() => openEdit(entity)}>
              编辑
            </a>
            <Popconfirm
              title="删除该菜单？（带子菜单需先删子）"
              onConfirm={() => removeMenuRow(entity)}
            >
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
          locale={{
            emptyText:
              accountId == null
                ? '暂无公众号账号（先在 wx_accounts 落账号行）'
                : '暂无菜单（草稿态随时可编辑，推送微信需凭据）',
          }}
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
          <Form.Item
            name="parentId"
            label="父菜单"
            extra={isTop ? '留空即一级菜单（每号最多 3 个）' : '二级菜单（每个一级下最多 5 个）'}
          >
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

export default MenusTab;
