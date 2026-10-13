import { PageContainer } from '@ant-design/pro-components';
import { history, useLocation } from '@umijs/max';
import { Button, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import {
  createCall,
  createMaterial,
  createSchedule,
  createScreen,
  deleteMaterial,
  deleteSchedule,
  deleteScreen,
  MATERIAL_UPLOAD_ACTION,
  queryMaterialsByPage,
  queryRecentCalls,
  querySchedulesByPage,
  queryScreensByPage,
  updateMaterial,
  updateSchedule,
  updateScreen,
  uploadAuthHeaders,
  type AdMaterial,
  type AdSchedule,
  type AdScreen,
  type QueueCall,
} from '@/services/ant-design-pro/ads';
import moment from 'moment';
import { Upload } from 'antd';
import './index.less';

const TYPE_IMAGE = 1;
const TYPE_VIDEO = 2;
const TYPE_TEXT: Record<number, string> = { 1: '图片', 2: '视频' };
const WEEKDAY_LABELS = '一二三四五六日';
/** 心跳超过 2 分钟视为离线（平板轮询 30s） */
const OFFLINE_AFTER_MS = 2 * 60 * 1000;
/** 封面渐变轮换（同卡项页：g1 石墨/g2 金棕/g3 黛蓝/g4 藤紫；素材首格为深色底） */
const THUMB_GRADS = ['g1', 'g2', 'g3', 'g4'];

type TabKey = 'materials' | 'schedules' | 'screens' | 'calls';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'materials', label: '素材管理' },
  { key: 'schedules', label: '排期管理' },
  { key: 'screens', label: '屏幕管理' },
  { key: 'calls', label: '叫号' },
];

const weekdaysText = (weekdays?: string) => {
  if (!weekdays || !weekdays.trim()) return '每天';
  return weekdays
    .split(',')
    .map((d) => `周${WEEKDAY_LABELS[Number(d.trim()) - 1] ?? d.trim()}`)
    .join(' ');
};

const timeRange = (start?: string, end?: string) =>
  !start && !end ? '全天' : `${start || '00:00'} ~ ${end || '24:00'}`;

const isOnline = (lastSeenAt?: string) =>
  !!lastSeenAt && Date.now() - new Date(lastSeenAt).getTime() < OFFLINE_AFTER_MS;

const AdsPage: React.FC = () => {
  const location = useLocation();
  const initialTab = (new URLSearchParams(location.search).get('tab') || 'materials') as TabKey;
  const [tab, setTab] = useState<TabKey>(
    TABS.some((t) => t.key === initialTab) ? initialTab : 'materials',
  );

  const [materials, setMaterials] = useState<AdMaterial[]>([]);
  const [screens, setScreens] = useState<AdScreen[]>([]);
  const [schedules, setSchedules] = useState<AdSchedule[]>([]);
  const [calls, setCalls] = useState<QueueCall[]>([]);
  const [loading, setLoading] = useState(false);

  const [matModal, setMatModal] = useState<{ open: boolean; editing?: AdMaterial }>({
    open: false,
  });
  const [mediaUrl, setMediaUrl] = useState('');
  const [mediaType, setMediaType] = useState<1 | 2>(TYPE_IMAGE);
  const [uploading, setUploading] = useState(false);
  const [matForm] = Form.useForm();

  const [schModal, setSchModal] = useState<{ open: boolean; editing?: AdSchedule }>({
    open: false,
  });
  const [schForm] = Form.useForm();

  const [scrModal, setScrModal] = useState<{ open: boolean; editing?: AdScreen }>({ open: false });
  const [scrForm] = Form.useForm();

  const [callForm] = Form.useForm();
  const [calling, setCalling] = useState(false);

  const screenName = (id?: number | null) =>
    id == null ? '全部屏' : screens.find((s) => s.id === id)?.name || `#${id}`;

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [m, s, sch, c] = await Promise.all([
        queryMaterialsByPage({ current: 1, pageSize: 100 }),
        queryScreensByPage({ current: 1, pageSize: 100 }),
        querySchedulesByPage({ current: 1, pageSize: 100 }),
        queryRecentCalls(),
      ]);
      setMaterials(m?.data || []);
      setScreens(s?.data || []);
      setSchedules(sch?.data || []);
      setCalls(c || []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
    // 屏幕心跳与最近叫号定时刷新（平板轮询 30s，2 分钟无心跳即离线）
    const timer = setInterval(loadAll, 20_000);
    return () => clearInterval(timer);
  }, [loadAll]);

  const switchTab = (key: TabKey) => {
    setTab(key);
    history.replace(`/ads?tab=${key}`);
  };

  /** 该屏当前生效素材数与排期名（客户端镜像 playlist 口径：命中排期取素材并集，无命中回退全部启用素材） */
  const playingInfo = (screenId?: number) => {
    const weekday = String(moment().isoWeekday());
    const hhmm = moment().format('HH:mm');
    const enabled = materials.filter((m) => m.enabled === 1);
    const hits = schedules.filter(
      (s) =>
        s.screenId === screenId &&
        s.enabled === 1 &&
        (!s.weekdays?.trim() ||
          s.weekdays
            .split(',')
            .map((t) => t.trim())
            .includes(weekday)) &&
        (!s.startTime || hhmm >= s.startTime) &&
        (!s.endTime || hhmm <= s.endTime),
    );
    if (hits.length === 0) return { count: enabled.length, name: '默认轮播' };
    const ids = new Set(hits.map((h) => h.materialId));
    const mats = enabled.filter((m) => ids.has(m.id!));
    const schedName = hits.find((h) => h.name?.trim())?.name?.trim();
    return { count: mats.length, name: schedName || mats[0]?.name || '默认轮播' };
  };

  // ---------- 素材 ----------

  const openMatCreate = () => {
    setMatModal({ open: true });
    setMediaUrl('');
    setMediaType(TYPE_IMAGE);
    matForm.resetFields();
    matForm.setFieldsValue({ type: TYPE_IMAGE, enabled: 1 });
  };

  const openMatEdit = (entity: AdMaterial) => {
    setMatModal({ open: true, editing: entity });
    setMediaUrl(entity.url || '');
    setMediaType((entity.type as 1 | 2) || TYPE_IMAGE);
    matForm.setFieldsValue(entity);
  };

  const saveMaterial = async () => {
    const values = await matForm.validateFields();
    if (!mediaUrl) {
      message.error('请先上传媒体文件');
      return;
    }
    const payload: AdMaterial = { ...values, url: mediaUrl };
    const res = matModal.editing
      ? await updateMaterial({ ...matModal.editing, ...payload })
      : await createMaterial(payload);
    if (res?.id) {
      message.success(matModal.editing ? '已更新' : '已创建');
      setMatModal({ open: false });
      loadAll();
    }
  };

  const toggleMaterial = async (entity: AdMaterial) => {
    const next = entity.enabled === 1 ? 0 : 1;
    const res = await updateMaterial({ ...entity, enabled: next });
    if (res?.id) {
      message.success(next === 1 ? '已启用' : '已停用');
      loadAll();
    }
  };

  // ---------- 排期 / 屏 ----------

  const saveSchedule = async () => {
    const values = await schForm.validateFields();
    const res = schModal.editing
      ? await updateSchedule({ ...schModal.editing, ...values })
      : await createSchedule(values);
    if (res?.id) {
      message.success(schModal.editing ? '已更新' : '已创建');
      setSchModal({ open: false });
      loadAll();
    }
  };

  const saveScreen = async () => {
    const values = await scrForm.validateFields();
    const res = scrModal.editing
      ? await updateScreen({ ...scrModal.editing, ...values })
      : await createScreen(values);
    if (res?.id) {
      message.success(scrModal.editing ? '已更新' : '已注册');
      setScrModal({ open: false });
      loadAll();
    }
  };

  // ---------- 快捷叫号 ----------

  const onCall = async (values: QueueCall) => {
    setCalling(true);
    try {
      const res = await createCall({ ...values, screenId: values.screenId ?? null });
      if (res?.id) {
        message.success(`已叫号：${res.room} ${res.number} 号`);
        callForm.resetFields(['number', 'patientMasked']);
        loadAll();
      }
    } finally {
      setCalling(false);
    }
  };

  // ---------- 表格列 ----------

  const materialColumns = [
    {
      title: '素材',
      dataIndex: 'name',
      render: (_: any, entity: AdMaterial, index: number) => (
        <div className="mat">
          {entity.url && entity.type === TYPE_IMAGE ? (
            <img className="thumb img" src={entity.url} alt="" />
          ) : (
            <div className={`thumb ${THUMB_GRADS[index % 4]}`}>
              {(entity.name || '素').slice(0, 1)}
            </div>
          )}
          <div>
            <div className="nm">{entity.name || '-'}</div>
            <div className="tp">
              {[TYPE_TEXT[entity.type || TYPE_IMAGE], entity.category].filter(Boolean).join(' · ')}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (_: any, entity: AdMaterial) => (
        <span className="num">{Math.round((entity.durationMs || 0) / 100) / 10} 秒</span>
      ),
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 76,
      render: (_: any, entity: AdMaterial) => <span className="sort">{entity.sort ?? 0}</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 96,
      render: (_: any, entity: AdMaterial) => (
        <span className={`st ${entity.enabled === 1 ? 'on' : 'off'}`}>
          {entity.enabled === 1 ? '启用' : '停用'}
        </span>
      ),
    },
    {
      title: '操作',
      width: 150,
      render: (_: any, entity: AdMaterial) => (
        <span className="op">
          <a onClick={() => openMatEdit(entity)}>编辑</a>
          <a onClick={() => toggleMaterial(entity)}>{entity.enabled === 1 ? '停用' : '启用'}</a>
          <Popconfirm
            title="确认删除该素材？"
            onConfirm={async () => {
              await deleteMaterial(entity.id!);
              message.success('已删除');
              loadAll();
            }}
          >
            <a className="del">删除</a>
          </Popconfirm>
        </span>
      ),
    },
  ];

  const scheduleColumns = [
    {
      title: '排期名',
      dataIndex: 'name',
      width: 150,
      render: (_: any, entity: AdSchedule) => entity.name || '—',
    },
    {
      title: '屏',
      dataIndex: 'screenId',
      render: (_: any, entity: AdSchedule) => screenName(entity.screenId),
    },
    {
      title: '素材',
      dataIndex: 'materialId',
      render: (_: any, entity: AdSchedule) =>
        materials.find((m) => m.id === entity.materialId)?.name || `#${entity.materialId}`,
    },
    {
      title: '生效星期',
      dataIndex: 'weekdays',
      width: 170,
      render: (_: any, entity: AdSchedule) => weekdaysText(entity.weekdays),
    },
    {
      title: '时段',
      dataIndex: 'startTime',
      width: 140,
      render: (_: any, entity: AdSchedule) => timeRange(entity.startTime, entity.endTime),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 96,
      render: (_: any, entity: AdSchedule) => (
        <span className={`st ${entity.enabled === 1 ? 'on' : 'off'}`}>
          {entity.enabled === 1 ? '启用' : '停用'}
        </span>
      ),
    },
    {
      title: '操作',
      width: 130,
      render: (_: any, entity: AdSchedule) => (
        <span className="op">
          <a
            onClick={() => {
              setSchModal({ open: true, editing: entity });
              schForm.setFieldsValue(entity);
            }}
          >
            编辑
          </a>
          <Popconfirm
            title="确认删除该排期？"
            onConfirm={async () => {
              await deleteSchedule(entity.id!);
              message.success('已删除');
              loadAll();
            }}
          >
            <a className="del">删除</a>
          </Popconfirm>
        </span>
      ),
    },
  ];

  const screenColumns = [
    {
      title: '屏',
      dataIndex: 'name',
      render: (_: any, entity: AdScreen) => (
        <div>
          <div className="nm">{entity.name || '-'}</div>
          <div className="tp">{entity.code}</div>
        </div>
      ),
    },
    {
      title: '位置',
      dataIndex: 'location',
      render: (_: any, entity: AdScreen) => entity.location || '—',
    },
    {
      title: '心跳',
      dataIndex: 'lastSeenAt',
      width: 190,
      render: (_: any, entity: AdScreen) => (
        <span className="hb">
          <span className={`dot ${isOnline(entity.lastSeenAt) ? '' : 'off'}`} />
          {isOnline(entity.lastSeenAt)
            ? '在线'
            : entity.lastSeenAt
            ? `离线 · ${moment(entity.lastSeenAt).format('M-DD HH:mm')}`
            : '从未上线'}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 96,
      render: (_: any, entity: AdScreen) => (
        <span className={`st ${entity.enabled === 1 ? 'on' : 'off'}`}>
          {entity.enabled === 1 ? '启用' : '停用'}
        </span>
      ),
    },
    {
      title: '操作',
      width: 130,
      render: (_: any, entity: AdScreen) => (
        <span className="op">
          <a
            onClick={() => {
              setScrModal({ open: true, editing: entity });
              scrForm.setFieldsValue(entity);
            }}
          >
            编辑
          </a>
          <Popconfirm
            title="确认删除该屏？该屏的排期不会自动删除"
            onConfirm={async () => {
              await deleteScreen(entity.id!);
              message.success('已删除');
              loadAll();
            }}
          >
            <a className="del">删除</a>
          </Popconfirm>
        </span>
      ),
    },
  ];

  const callColumns = [
    {
      title: '号码',
      dataIndex: 'number',
      width: 110,
      render: (_: any, entity: QueueCall) => <span className="sort">{entity.number}</span>,
    },
    { title: '就诊科室', dataIndex: 'room', width: 180 },
    {
      title: '姓名（脱敏）',
      dataIndex: 'patientMasked',
      width: 140,
      render: (_: any, entity: QueueCall) => entity.patientMasked || '—',
    },
    {
      title: '目标屏',
      dataIndex: 'screenId',
      width: 160,
      render: (_: any, entity: QueueCall) =>
        entity.screenId == null ? (
          <span className="st on">全部屏</span>
        ) : (
          screenName(entity.screenId)
        ),
    },
    {
      title: '叫号时间',
      dataIndex: 'calledAt',
      width: 170,
      render: (_: any, entity: QueueCall) =>
        entity.calledAt ? moment(entity.calledAt).format('M-DD HH:mm:ss') : '-',
    },
  ];

  const primaryAction =
    tab === 'materials' ? (
      <Button key="mat" type="primary" onClick={openMatCreate}>
        上传素材
      </Button>
    ) : tab === 'schedules' ? (
      <Button
        key="sch"
        type="primary"
        onClick={() => {
          setSchModal({ open: true });
          schForm.resetFields();
          schForm.setFieldsValue({ enabled: 1 });
        }}
      >
        新建排期
      </Button>
    ) : tab === 'screens' ? (
      <Button
        key="scr"
        type="primary"
        onClick={() => {
          setScrModal({ open: true });
          scrForm.resetFields();
          scrForm.setFieldsValue({ enabled: 1 });
        }}
      >
        注册屏
      </Button>
    ) : null;

  const lastCall = calls[0];
  // 快捷叫号草稿：表单即草稿（draftNumber 等随输入即时反映，hooks 须在组件顶层调用）
  const draftNumber = Form.useWatch('number', callForm) as string | undefined;
  const draftMasked = Form.useWatch('patientMasked', callForm) as string | undefined;
  const draftRoom = Form.useWatch('room', callForm) as string | undefined;

  return (
    <PageContainer
      title="广告屏管理"
      className="ad-page"
      content={
        <span className="ad-tip">
          素材按 <b>排序</b> 在各屏轮播；屏以 <b>?screen=屏标识</b> 打开展示页，心跳 2
          分钟内为在线； 叫号脱敏上屏，约 1 秒全屏提示、15 秒后自动回轮播。
        </span>
      }
      extra={[primaryAction]}
    >
      <div className="ad-tabs">
        {TABS.map((t) => (
          <span
            key={t.key}
            className={`tab${tab === t.key ? ' on' : ''}`}
            onClick={() => switchTab(t.key)}
          >
            {t.label}
          </span>
        ))}
      </div>

      <div className="ad-cols">
        <section className="ad-main">
          {tab === 'materials' ? (
            <Table
              rowKey="id"
              loading={loading}
              dataSource={materials}
              columns={materialColumns as any}
              pagination={false}
              className="ad-table"
              locale={{ emptyText: '暂无素材，点右上角「上传素材」新建' }}
            />
          ) : null}
          {tab === 'schedules' ? (
            <Table
              rowKey="id"
              loading={loading}
              dataSource={schedules}
              columns={scheduleColumns as any}
              pagination={false}
              className="ad-table"
              locale={{ emptyText: '暂无排期，点右上角「新建排期」绑定屏与素材' }}
            />
          ) : null}
          {tab === 'screens' ? (
            <Table
              rowKey="id"
              loading={loading}
              dataSource={screens}
              columns={screenColumns as any}
              pagination={false}
              className="ad-table"
              locale={{ emptyText: '暂无屏幕，点右上角「注册屏」登记平板' }}
            />
          ) : null}
          {tab === 'calls' ? (
            <>
              <div className="ad-note">
                在右侧「快捷叫号」发起叫号，定向屏（或全部屏）约 1 秒内全屏提示号码与诊室，15
                秒后自动回轮播。
              </div>
              <Table
                rowKey="id"
                loading={loading}
                dataSource={calls}
                columns={callColumns as any}
                pagination={false}
                className="ad-table"
                locale={{ emptyText: '今日尚无叫号记录' }}
              />
            </>
          ) : null}
        </section>

        <div className="ad-stack">
          <section className="ad-panel">
            <h3>
              屏幕状态<span className="hint">断网续播</span>
            </h3>
            {screens.length === 0 ? (
              <div className="ad-empty">尚未注册屏幕</div>
            ) : (
              screens.map((s) => (
                <div className="scr" key={s.id}>
                  <div className="sn">
                    <span className={`dot ${isOnline(s.lastSeenAt) ? '' : 'off'}`} />
                    {s.name}
                  </div>
                  <div className="sm">
                    {isOnline(s.lastSeenAt) ? (
                      <>
                        在线 · 正在播 <b>{playingInfo(s.id).name || '默认轮播'}</b>（
                        {playingInfo(s.id).count} 条素材轮播）
                      </>
                    ) : s.lastSeenAt ? (
                      <>
                        离线 · 上次在线 {moment(s.lastSeenAt).format('M-DD HH:mm')}，本地缓存续播中
                      </>
                    ) : (
                      <>离线 · 从未上线，等待平板以 {s.code} 接入</>
                    )}
                  </div>
                </div>
              ))
            )}
          </section>

          <section className="ad-panel">
            <h3>快捷叫号</h3>
            {/* 已就绪号码：表单即草稿，填好即显示，一键上屏（原型 admin-ads 口径）；无草稿回显最近一次 */}
            {draftNumber && draftNumber.trim() ? (
              <div className="call-num">
                <span className="n">{draftNumber.trim()}</span>
                <span className="w">{draftMasked || ''}</span>
              </div>
            ) : lastCall ? (
              <div className="call-num">
                <span className="n">{lastCall.number}</span>
                <span className="w">{lastCall.patientMasked || ''}</span>
              </div>
            ) : null}
            <div className="call-row">
              {draftNumber && draftNumber.trim() ? (
                <>就诊科室：{draftRoom?.trim() || '—'}</>
              ) : lastCall ? (
                <>
                  就诊科室：{lastCall.room} ·{' '}
                  {lastCall.screenId == null ? '全部屏' : screenName(lastCall.screenId)}
                </>
              ) : (
                '今日尚无叫号，填下方信息发起'
              )}
            </div>
            <div className="call-row">上屏约 1 秒，停留 15 秒后自动回轮播</div>
            <Form<QueueCall>
              form={callForm}
              layout="vertical"
              className="call-form"
              onFinish={onCall}
            >
              <div className="call-grid">
                <Form.Item
                  name="number"
                  label="号码"
                  rules={[{ required: true, message: '如 08' }]}
                >
                  <Input placeholder="08" />
                </Form.Item>
                <Form.Item name="patientMasked" label="姓名（脱敏）">
                  <Input placeholder="王*" maxLength={12} />
                </Form.Item>
              </div>
              <Form.Item
                name="room"
                label="就诊科室"
                rules={[{ required: true, message: '请填写诊室' }]}
              >
                <Input placeholder="中医一诊室" />
              </Form.Item>
              <Form.Item name="screenId" label="目标屏">
                <Select
                  allowClear
                  placeholder="不选 = 全部屏"
                  options={screens
                    .filter((s) => s.enabled === 1)
                    .map((s) => ({ label: `${s.name}（${s.code}）`, value: s.id }))}
                />
              </Form.Item>
              <Button
                className="call-btn"
                type="primary"
                loading={calling}
                onClick={() => callForm.submit()}
              >
                叫号上屏
              </Button>
            </Form>
            <div className="call-tip">
              姓名默认脱敏显示（王*），候诊区可见但不泄隐私；提示音与语音播报按屏能力自动开关。
            </div>
          </section>
        </div>
      </div>

      {/* 素材新建/编辑（上传走真实 /ads/materials/upload，落盘 data/ads 经 /media 托管） */}
      <Modal
        title={matModal.editing ? '编辑素材' : '上传素材'}
        open={matModal.open}
        onCancel={() => setMatModal({ open: false })}
        onOk={saveMaterial}
        okText="保存"
        width={520}
        destroyOnHidden
      >
        <Form form={matForm} layout="vertical">
          <Form.Item
            name="name"
            label="素材名"
            rules={[{ required: true, message: '请填写素材名' }]}
          >
            <Input placeholder="如 三伏灸疗程卡" />
          </Form.Item>
          <Form.Item name="category" label="分类" tooltip="素材子标题用，如「门店活动」，选填">
            <Input placeholder="门店活动" maxLength={20} />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select
              onChange={(v) => setMediaType(v as 1 | 2)}
              options={[
                { label: '图片', value: TYPE_IMAGE },
                { label: '视频', value: TYPE_VIDEO },
              ]}
            />
          </Form.Item>
          <Form.Item label="媒体文件" required>
            <Upload
              name="file"
              action={MATERIAL_UPLOAD_ACTION}
              headers={uploadAuthHeaders()}
              maxCount={1}
              accept={mediaType === TYPE_VIDEO ? 'video/*' : 'image/*'}
              showUploadList={false}
              disabled={uploading}
              onChange={(info) => {
                if (info.file.status === 'uploading') {
                  setUploading(true);
                  return;
                }
                setUploading(false);
                if (info.file.status === 'done') {
                  const url = info.file.response?.url;
                  if (url) {
                    setMediaUrl(url);
                    message.success('上传成功');
                  } else {
                    message.error(info.file.response?.errorMessage || '上传失败');
                  }
                } else if (info.file.status === 'error') {
                  message.error('上传失败');
                }
              }}
            >
              <Button loading={uploading}>
                {mediaType === TYPE_VIDEO
                  ? '上传视频 (mp4/webm/mov)'
                  : '上传图片 (png/jpg/webp/gif)'}
              </Button>
            </Upload>
            <div className={`media-hint${mediaUrl ? ' ok' : ''}`}>
              {mediaUrl ? `已选媒体：${mediaUrl}` : '尚未上传媒体'}
            </div>
          </Form.Item>
          <div className="form-grid">
            <Form.Item
              name="durationMs"
              label="停留时长（毫秒）"
              rules={[{ required: true, message: '请填写停留时长' }]}
              tooltip="图片轮播停留时长；视频按实际播完为准，此值作兜底"
            >
              <InputNumber min={1500} step={500} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="sort"
              label="轮播顺序"
              rules={[{ required: true, message: '请填写顺序' }]}
              tooltip="数字小的先播"
            >
              <InputNumber min={0} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item name="enabled" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '启用', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
          {/* 叠加层文案（平板整版排版层，对齐原型 tablet-screen.html：眉题/大标题/文案/价格；全空即纯素材播放） */}
          <Form.Item
            name="kicker"
            label="眉题"
            tooltip="叠加层顶部小字，如「冬病夏治」；四项文案全空即纯素材播放"
          >
            <Input placeholder="如 冬病夏治" maxLength={12} />
          </Form.Item>
          <Form.Item name="title" label="大标题" tooltip="叠加层主文案，建议一句话">
            <Input placeholder="如 三伏艾灸，借阳祛寒正当时" maxLength={30} />
          </Form.Item>
          <Form.Item name="copy" label="文案" tooltip="叠加层说明行">
            <Input.TextArea
              rows={2}
              placeholder="如 督脉灸配伍中脘、足三里，适合手脚怕冷的顾客。"
              maxLength={60}
            />
          </Form.Item>
          <Form.Item name="price" label="价格行" tooltip="自由文本，如「¥880 / 10 次」">
            <Input placeholder="如 ¥880 / 10 次" maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 排期新建/编辑 */}
      <Modal
        title={schModal.editing ? '编辑排期' : '新建排期'}
        open={schModal.open}
        onCancel={() => setSchModal({ open: false })}
        onOk={saveSchedule}
        okText="保存"
        width={520}
        destroyOnHidden
      >
        <Form form={schForm} layout="vertical">
          <Form.Item name="screenId" label="屏" rules={[{ required: true, message: '请选择屏' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={screens.map((s) => ({ label: `${s.name}（${s.code}）`, value: s.id }))}
            />
          </Form.Item>
          <Form.Item
            name="materialId"
            label="素材"
            rules={[{ required: true, message: '请选择素材' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={materials.map((m) => ({
                label: `${m.name}（${TYPE_TEXT[m.type || TYPE_IMAGE]}）`,
                value: m.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="排期名"
            tooltip="屏幕状态「正在播」展示用，如「10 月常规排期」，选填"
          >
            <Input placeholder="10 月常规排期" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="weekdays"
            label="生效星期"
            tooltip="1=周一 … 7=周日，逗号分隔；留空 = 每天"
          >
            <Input placeholder="如 1,2,3,4,5；留空 = 每天" />
          </Form.Item>
          <div className="form-grid">
            <Form.Item name="startTime" label="时段起" tooltip="留空 = 00:00">
              <Input placeholder="09:00" />
            </Form.Item>
            <Form.Item name="endTime" label="时段止" tooltip="留空 = 24:00">
              <Input placeholder="18:00" />
            </Form.Item>
          </div>
          <Form.Item name="enabled" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '启用', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 屏注册/编辑 */}
      <Modal
        title={scrModal.editing ? '编辑屏' : '注册屏'}
        open={scrModal.open}
        onCancel={() => setScrModal({ open: false })}
        onOk={saveScreen}
        okText="保存"
        width={480}
        destroyOnHidden
      >
        <Form form={scrForm} layout="vertical">
          <Form.Item
            name="code"
            label="屏标识"
            tooltip="唯一；平板以 /tablet/?screen=屏标识 打开"
            rules={[{ required: true, message: '请填写屏标识' }]}
          >
            <Input placeholder="PAD-01" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请填写名称' }]}>
            <Input placeholder="候诊区 1 号屏" />
          </Form.Item>
          <Form.Item name="location" label="位置">
            <Input placeholder="如 一楼候诊区" />
          </Form.Item>
          <Form.Item name="enabled" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '启用', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default AdsPage;
