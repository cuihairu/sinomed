import React, { useCallback, useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Input, message, Popconfirm, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  createRoom,
  deleteRoom,
  queryRoomList,
  updateRoom,
  type Room,
} from '@/services/ant-design-pro/room';

/**
 * 工位/房间字典（美容业态 b，docs/design/salon-extension.md §2-b）：
 * 美容项目预约占工位，冲突检测技师+工位双维（服务端 AppointmentServiceImpl）。
 * 医馆预约不选工位，旧口径不变。
 */
export default function RoomManage() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(false);
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRooms((await queryRoomList()) || []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const add = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      message.warning('请输入工位名');
      return;
    }
    setCreating(true);
    try {
      const created = await createRoom({ name: trimmed });
      if (created?.id) {
        message.success('工位已建');
        setName('');
        load();
      }
    } finally {
      setCreating(false);
    }
  };

  const toggle = async (room: Room) => {
    const updated = await updateRoom({ id: room.id, enabled: room.enabled === 1 ? 0 : 1 });
    if (updated?.id) {
      message.success(updated.enabled === 1 ? '已启用' : '已停用');
      load();
    }
  };

  const columns: ColumnsType<Room> = [
    { title: 'id', dataIndex: 'id', width: 70 },
    { title: '工位名', dataIndex: 'name' },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 100,
      render: (_, room) =>
        room.enabled === 1 ? <Tag color="green">在用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作',
      key: 'option',
      width: 160,
      render: (_, room) => (
        <>
          <a onClick={() => toggle(room)}>{room.enabled === 1 ? '停用' : '启用'}</a>
          <Popconfirm
            title="删除该工位？历史预约的工位名将显示为空"
            onConfirm={async () => {
              await deleteRoom(room.id!);
              message.success('删除成功');
              load();
            }}
          >
            <a style={{ marginLeft: 16 }}>删除</a>
          </Popconfirm>
        </>
      ),
    },
  ];

  return (
    <PageContainer
      title="工位管理"
      content="美容项目预约占工位：同工位同小时只排一位（技师+工位双维限约，服务端校验）；停用工位不再出现在新建预约的下拉里。"
    >
      <div style={{ display: 'flex', gap: 12, marginBottom: 16, maxWidth: 420 }}>
        <Input
          placeholder="新工位名，如：美容一号工位"
          value={name}
          maxLength={30}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && add()}
        />
        <Button type="primary" loading={creating} onClick={add}>
          新建
        </Button>
      </div>
      <Table<Room>
        rowKey="id"
        columns={columns}
        dataSource={rooms}
        loading={loading}
        pagination={false}
        size="middle"
      />
    </PageContainer>
  );
}
