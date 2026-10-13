import { PageContainer } from '@ant-design/pro-components';
import { Button, Form, message, Modal, Popconfirm, Select, Table, TimePicker } from 'antd';
import { useEffect, useState } from 'react';
import { queryStaffByPage } from '@/services/ant-design-pro/staff';
import {
  createShift,
  deleteShift,
  queryShiftPage,
  updateShift,
  type Shift,
} from '@/services/ant-design-pro/shift';
import './index.less';

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/** 周期班表：员工 × 星期（周一~周日）矩阵，每格一条 HH:mm 班次；考勤打卡仍以签到记录为准 */
export default () => {
  const [staffs, setStaffs] = useState<API.Staff[]>([]);
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<Shift | undefined>();
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const staffMsg = await queryStaffByPage({ current: 1, pageSize: 50 });
      setStaffs((staffMsg?.data as API.Staff[]) || []);
      const shiftMsg = await queryShiftPage({ current: 1, pageSize: 200 });
      setShifts(shiftMsg?.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (shift: Shift) => {
    setEditing(shift);
    form.setFieldsValue({
      staffId: shift.staffId,
      weekday: shift.weekday,
      start: shift.start,
      end: shift.end,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload = {
      staffId: values.staffId,
      weekday: values.weekday,
      start: values.start.format('HH:mm'),
      end: values.end.format('HH:mm'),
    };
    setSaving(true);
    try {
      const saved = editing?.id
        ? await updateShift({ id: editing.id, ...payload })
        : await createShift(payload);
      if (saved?.id) {
        message.success(editing?.id ? '班次已更新' : '已加排班');
        setModalOpen(false);
        await load();
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: '员工',
      dataIndex: 'name',
      width: 120,
      fixed: 'left' as const,
      render: (name: string, record: API.Staff) => (
        <span className="sf-nm">
          {name || '-'}
          {record.account ? <i className="sf-acct">{record.account}</i> : null}
        </span>
      ),
    },
    ...WEEKDAYS.map((label, idx) => ({
      title: label,
      key: `day-${idx + 1}`,
      width: 140,
      render: (_: any, staff: API.Staff) => {
        const shift = shifts.find((s) => s.staffId === staff.id && s.weekday === idx + 1);
        if (!shift) {
          return <span className="sf-empty">—</span>;
        }
        return (
          <span className="sf-cell">
            <a className="sf-time" onClick={() => openEdit(shift)}>
              {shift.start}–{shift.end}
            </a>
            <Popconfirm
              key="del"
              title="删除该班次不影响考勤记录，确认删除？"
              onConfirm={async () => {
                if (shift.id) {
                  await deleteShift(shift.id);
                  message.success('已删除');
                  await load();
                }
              }}
            >
              <a className="sf-del">✕</a>
            </Popconfirm>
          </span>
        );
      },
    })),
  ];

  return (
    <PageContainer
      title="员工班表"
      content={
        <span className="sf-tip">
          周期班表按周循环：同一员工同一星期仅一条班次，点击时段可调整，
          <code>✕</code> 删除。班表只做排班参考，考勤打卡仍以签到记录为准。
        </span>
      }
    >
      <div className="sf-board">
        <div className="sf-toolbar">
          <Button type="primary" onClick={openCreate}>
            加排班
          </Button>
          <span className="sf-count">
            共 {shifts.length} 条班次 · {staffs.length} 名员工
          </span>
        </div>
        <Table
          rowKey="id"
          size="middle"
          bordered
          loading={loading}
          columns={columns}
          dataSource={staffs}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
      </div>
      <Modal
        title={editing?.id ? `调整班次 · ${editing.staffName} ${editing.weekdayText}` : '加排班'}
        open={modalOpen}
        onOk={submit}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText="保存"
        cancelText="取消"
        width={480}
      >
        <Form form={form} layout="vertical" className="sf-form">
          <Form.Item
            name="staffId"
            label="员工"
            rules={[{ required: true, message: '员工不能为空' }]}
          >
            <Select
              placeholder="选择员工"
              showSearch
              optionFilterProp="label"
              options={staffs.map((s) => ({ label: s.name || String(s.id), value: s.id }))}
            />
          </Form.Item>
          <Form.Item
            name="weekday"
            label="星期"
            rules={[{ required: true, message: '星期不能为空' }]}
          >
            <Select
              placeholder="周一 ~ 周日"
              options={WEEKDAYS.map((label, idx) => ({ label, value: idx + 1 }))}
            />
          </Form.Item>
          <div className="sf-times">
            <Form.Item
              name="start"
              label="上班时间"
              rules={[{ required: true, message: '上班时间不能为空' }]}
            >
              <TimePicker format="HH:mm" minuteStep={30} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="end"
              label="下班时间"
              rules={[
                { required: true, message: '下班时间不能为空' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const start = getFieldValue('start');
                    if (!value || !start) {
                      return Promise.resolve();
                    }
                    if (value.format('HH:mm') > start.format('HH:mm')) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('下班时间须晚于上班时间'));
                  },
                }),
              ]}
            >
              <TimePicker format="HH:mm" minuteStep={30} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <p className="sf-note">
            时段按 30 分钟步进选择；同一员工同一星期仅一条班次，重复保存会提示冲突。
          </p>
        </Form>
      </Modal>
    </PageContainer>
  );
};
