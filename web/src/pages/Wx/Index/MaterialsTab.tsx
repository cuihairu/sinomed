import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Modal, Tag, message } from 'antd';
import React, { useState } from 'react';
import moment from 'moment';
import {
  fetchMaterialBlob,
  queryMaterialsByPage,
  type WxMaterial,
} from '@/services/ant-design-pro/wx';
import { MATERIAL_TYPE_ENUM, MATERIAL_TYPE_LABELS, timeRangeColumn } from './constants';
import useAccountOptions from './useAccountOptions';

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
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 150,
      hideInSearch: true,
      render: (_, entity) =>
        entity.createTime ? moment(entity.createTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '账号',
      dataIndex: 'accountId',
      width: 130,
      valueType: 'select',
      fieldProps: { options, showSearch: true },
      render: (_, entity) => nameOf(entity.accountId),
    },
    {
      title: '类型',
      dataIndex: 'materialType',
      width: 110,
      valueType: 'select',
      valueEnum: MATERIAL_TYPE_ENUM,
      render: (_, entity) =>
        MATERIAL_TYPE_LABELS[entity.materialType || ''] || entity.materialType || '-',
    },
    {
      title: '素材',
      dataIndex: 'name',
      ellipsis: true,
      render: (_, entity) => entity.name || entity.mediaId || '-',
    },
    {
      title: '媒体ID',
      dataIndex: 'mediaId',
      width: 180,
      ellipsis: true,
      hideInSearch: true,
      render: (_, entity) => entity.mediaId || '-',
    },
    {
      title: '大小',
      dataIndex: 'size',
      width: 100,
      hideInSearch: true,
      render: (_, entity) => (entity.size != null ? `${(entity.size / 1024).toFixed(1)} KB` : '-'),
    },
    {
      title: '本地副本',
      dataIndex: 'localPath',
      width: 110,
      hideInSearch: true,
      render: (_, entity) => (entity.localPath ? <Tag color="green">有</Tag> : <Tag>无</Tag>),
    },
    {
      title: '查看',
      key: 'view',
      width: 90,
      hideInSearch: true,
      render: (_, entity) =>
        entity.localPath ? (
          <a onClick={() => openPreview(entity)}>查看</a>
        ) : (
          <span style={{ color: '#aaa' }}>—</span>
        ),
    },
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
      <Modal
        title={preview?.name}
        open={preview != null}
        onCancel={closePreview}
        footer={null}
        width={720}
        destroyOnClose
      >
        {preview &&
          (preview.mime.startsWith('image/') ? (
            <img src={preview.url} alt={preview.name} style={{ maxWidth: '100%' }} />
          ) : (
            <p>
              非图片素材，
              <a href={preview.url} target="_blank" rel="noreferrer">
                点此打开
              </a>
            </p>
          ))}
      </Modal>
    </>
  );
};

export default MaterialsTab;
