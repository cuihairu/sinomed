import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { Button, Drawer, Popconfirm, message } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import moment from 'moment';
import {
  DECOCTION_TEXT,
  PASTE_TEXT,
  deletePrescription,
  dispensePrescription,
  fetchPrescription,
  queryPrescriptionPage,
  setDecoctionStatus,
  setPasteStatus,
  type Prescription,
} from '@/services/ant-design-pro/prescription';
import './index.less';

const maskPhone = (p?: string) => (p && p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p || '');

export default function PrescriptionQuery() {
  const actionRef = useRef<ActionType>();
  /** 详情抽屉：打开时按 id 拉全量处方（含药味） */
  const [detailId, setDetailId] = useState<number | undefined>();
  const [detail, setDetail] = useState<Prescription | undefined>();
  const [detailLoading, setDetailLoading] = useState(false);
  /** 代煎流转按钮忙态 */
  const [flowing, setFlowing] = useState(false);

  const loadDetail = useCallback(async (id: number) => {
    setDetailLoading(true);
    try {
      setDetail(await fetchPrescription(id));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  /** 代煎流转：待煎→可取→已取，成功后刷新抽屉与列表 */
  const flow = async (status: 2 | 3) => {
    if (detailId == null) return;
    setFlowing(true);
    try {
      await setDecoctionStatus(detailId, status);
      message.success(status === 2 ? '已转可取，请顾客凭单领取' : '已登记领取');
      await loadDetail(detailId);
      actionRef.current?.reload();
    } finally {
      setFlowing(false);
    }
  };

  /** 膏方领取流转：待制作→可取→已取，成功后刷新抽屉与列表 */
  const flowPaste = async (status: 2 | 3) => {
    if (detailId == null) return;
    setFlowing(true);
    try {
      await setPasteStatus(detailId, status);
      message.success(status === 2 ? '已转可取，请顾客凭单领取' : '已登记领取');
      await loadDetail(detailId);
      actionRef.current?.reload();
    } finally {
      setFlowing(false);
    }
  };

  /** 药房发药：待发药→已发药单向（备注口径在药房工作台，此处快捷确认） */
  const dispenseHere = async () => {
    if (detailId == null) return;
    setFlowing(true);
    try {
      await dispensePrescription(detailId);
      message.success('已发药');
      await loadDetail(detailId);
      actionRef.current?.reload();
    } finally {
      setFlowing(false);
    }
  };

  useEffect(() => {
    if (detailId != null) loadDetail(detailId);
  }, [detailId, loadDetail]);

  const closeDetail = () => {
    setDetailId(undefined);
    setDetail(undefined);
  };

  const columns: ProColumns<Prescription>[] = [
    { title: 'id', dataIndex: 'id', width: 60, hideInTable: true },
    {
      title: '处方',
      dataIndex: 'id',
      width: 90,
      hideInSearch: true,
      render: (_, entity) => <span className="pid">#{entity.id}</span>,
    },
    {
      title: '顾客',
      dataIndex: 'customerName',
      width: 120,
      render: (_, entity) => entity.customerName || '-',
    },
    {
      title: '医师',
      dataIndex: 'staffName',
      width: 110,
      render: (_, entity) => entity.staffName || <span className="faint">未记录</span>,
    },
    {
      title: '药味',
      dataIndex: 'herbs',
      width: 260,
      hideInSearch: true,
      render: (_, entity) => {
        const herbs = entity.herbs || [];
        if (herbs.length === 0) return '-';
        const names = herbs.map((h) => h.herb || '').join('、');
        return (
          <span className="herbs" title={names}>
            {herbs.length} 味 · {names}
          </span>
        );
      },
    },
    {
      title: '剂数',
      dataIndex: 'doses',
      width: 80,
      hideInSearch: true,
      render: (_, entity) => (entity.doses ? `${entity.doses} 付` : '-'),
    },
    {
      title: '类型',
      dataIndex: 'prescriptionType',
      width: 80,
      hideInSearch: true,
      render: (_, entity) => (entity.prescriptionType === 1 ? <span>膏方</span> : '汤剂'),
    },
    {
      title: '代煎',
      dataIndex: 'decoctionStatus',
      width: 90,
      hideInSearch: true,
      render: (_, entity) =>
        entity.decoctionStatus == null || entity.decoctionStatus === 0 ? (
          <span className="faint">—</span>
        ) : (
          <span className={`dc s${entity.decoctionStatus}`}>{DECOCTION_TEXT[entity.decoctionStatus]}</span>
        ),
    },
    {
      title: '膏方',
      dataIndex: 'pasteStatus',
      width: 90,
      hideInSearch: true,
      render: (_, entity) =>
        entity.prescriptionType === 1 && entity.pasteStatus != null ? (
          <span className={`pc s${entity.pasteStatus}`}>{PASTE_TEXT[entity.pasteStatus]}</span>
        ) : (
          <span className="faint">—</span>
        ),
    },
    {
      title: '用法',
      dataIndex: 'usage',
      ellipsis: true,
      hideInSearch: true,
      render: (_, entity) => entity.usage || '-',
    },
    {
      title: '开方时间',
      dataIndex: 'createTime',
      width: 150,
      hideInSearch: true,
      render: (_, entity) => (entity.createTime ? moment(entity.createTime).format('MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 130,
      fixed: 'right',
      render: (_, entity) =>
        entity.id
          ? [
              <a key="view" className="op-go" onClick={() => setDetailId(entity.id!)}>
                详情
              </a>,
              <Popconfirm
                key="delete"
                title="删除这张处方（连同药味）？"
                onConfirm={async () => {
                  await deletePrescription(entity.id!);
                  message.success('已删除');
                  actionRef.current?.reload();
                }}
              >
                <a className="op-stop">删除</a>
              </Popconfirm>,
            ]
          : [],
    },
  ];

  return (
    <PageContainer
      title="处方查询"
      content="已开处方笺一览；点「详情」看整方药味、实时计价，代煎处方在此流转领取（待煎→可取→已取）。"
    >
      <ProTable<Prescription>
        rowKey="id"
        columns={columns}
        actionRef={actionRef}
        search={false}
        options={false}
        request={async (params) => {
          const res = await queryPrescriptionPage({ current: params.current, pageSize: params.pageSize });
          return { data: res?.data || [], success: true, total: res?.total || 0 };
        }}
        pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条 · 每页 20 条` }}
        dateFormatter="string"
      />

      <Drawer
        title={`处方笺 #${detailId ?? ''}`}
        open={detailId != null}
        onClose={closeDetail}
        width={520}
        loading={detailLoading}
      >
        {detail ? (
          <div className="pr-detail">
            <div className="head">
              <div className="who">
                <div className="nm">{detail.customerName || `顾客 ${detail.customerId}`}</div>
                <div className="sub">
                  {detail.staffName ? `${detail.staffName} 开方` : '未记录医师'} ·{' '}
                  {detail.createTime ? moment(detail.createTime).format('YYYY-MM-DD HH:mm') : ''}
                </div>
              </div>
              <div className="doses">
                <b>{detail.doses ?? '-'}</b>
                <span>付</span>
              </div>
            </div>
            <table className="herb-table">
              <thead>
                <tr>
                  <th className="no">#</th>
                  <th className="nm">药名</th>
                  <th className="wt">克/付</th>
                  <th className="sp">特殊煎法</th>
                </tr>
              </thead>
              <tbody>
                {(detail.herbs || []).map((h, i) => (
                  <tr key={h.id ?? i}>
                    <td className="no">{i + 1}</td>
                    <td className="nm">{h.herb}</td>
                    <td className="wt">{h.weight}</td>
                    <td className="sp">{h.special || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {detail.usage ? (
              <div className="field">
                <span className="k">用法</span>
                <span className="v">{detail.usage}</span>
              </div>
            ) : null}
            {detail.remark ? (
              <div className="field">
                <span className="k">备注</span>
                <span className="v">{detail.remark}</span>
              </div>
            ) : null}
            {detail.pricing ? (
              <div className="field">
                <span className="k">计价</span>
                <span className="v">
                  ¥{((detail.pricing.totalFen ?? 0) / 100).toFixed(2)}
                  （已比价 {detail.pricing.pricedHerbCount ?? 0}/{detail.pricing.herbCount ?? 0} 味
                  {detail.pricing.unknownHerbs?.length
                    ? `，未收录：${detail.pricing.unknownHerbs.join('、')}`
                    : ''}
                  ）
                </span>
              </div>
            ) : null}
            {detail.decoctionStatus != null && detail.decoctionStatus > 0 && (
              <div className="field dc-line">
                <span className="k">代煎</span>
                <span className="v">
                  <span className={`dc s${detail.decoctionStatus}`}>{DECOCTION_TEXT[detail.decoctionStatus ?? 0]}</span>
                  {detail.decoctionBags ? ` ${detail.decoctionBags} 袋` : ''}
                  {detail.decoctionFeeFen ? ` · 服务费 ¥${(detail.decoctionFeeFen / 100).toFixed(2)}（提示口径）` : ''}
                </span>
                {detail.decoctionStatus === 1 && (
                  <Button size="small" type="primary" loading={flowing} onClick={() => flow(2)}>
                    制作完成 · 转可取
                  </Button>
                )}
                {detail.decoctionStatus === 2 && (
                  <Button size="small" type="primary" loading={flowing} onClick={() => flow(3)}>
                    顾客已领取
                  </Button>
                )}
              </div>
            )}
            {detail.prescriptionType === 1 && detail.pasteStatus !== null && (
              <div className="field dc-line">
                <span className="k">膏方</span>
                <span className="v">
                  <span className={`pc s${detail.pasteStatus}`}>{PASTE_TEXT[detail.pasteStatus ?? 0]}</span>
                  {detail.craft ? ` · ${detail.craft}` : ''}
                </span>
                {detail.pasteStatus === 1 && (
                  <Button size="small" type="primary" loading={flowing} onClick={() => flowPaste(2)}>
                    制成 · 转可取
                  </Button>
                )}
                {detail.pasteStatus === 2 && (
                  <Button size="small" type="primary" loading={flowing} onClick={() => flowPaste(3)}>
                    顾客已领取
                  </Button>
                )}
              </div>
            )}
            {detail.dispenseStatus != null && (
              <div className="field dc-line">
                <span className="k">发药</span>
                <span className="v">
                  {detail.dispenseStatus === 1 ? (
                    <span className="pc s3">
                      已发药
                      {detail.dispenseTime ? ` · ${moment(detail.dispenseTime).format('MM-DD HH:mm')}` : ''}
                    </span>
                  ) : (
                    <span className="dc s1">待发药</span>
                  )}
                </span>
                {detail.dispenseStatus === 0 && (
                  <Popconfirm title="确认药已交出？（单向，不可撤销）" onConfirm={dispenseHere}>
                    <Button size="small" type="primary" loading={flowing}>
                      确认发药
                    </Button>
                  </Popconfirm>
                )}
              </div>
            )}
            <div className="hint">
              演示口径：计价按药材字典实时试算、无快照（随改价同步），代煎费仅提示不收费，库存为规划功能；带备注的发药走药房工作台
            </div>
          </div>
        ) : null}
      </Drawer>
    </PageContainer>
  );
}
