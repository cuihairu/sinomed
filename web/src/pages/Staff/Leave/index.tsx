import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, DatePicker, Popconfirm, Radio, Select, message } from 'antd';
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import moment from 'moment';
import {
  queryLeavePage,
  queryStaffByPage,
  deleteLeave,
  createLeave,
  type StaffLeave,
} from '@/services/ant-design-pro/staff';
import './index.less';

const LEAVE_TYPE_TEXT: Record<number, string> = { 0: '病假', 1: '事假' };

/** RangePicker 取值类型（跟随 antd 组件推导，项目未直装 dayjs） */
type RangeValue = React.ComponentProps<typeof DatePicker.RangePicker>['value'];

const Leave: React.FC = () => {
  const [staffOptions, setStaffOptions] = useState<{ label: string; value: number }[]>([]);
  const [staffId, setStaffId] = useState<number | undefined>();
  const [leaveType, setLeaveType] = useState<number>(1);
  const [reason, setReason] = useState('');
  const [range, setRange] = useState<RangeValue>(null);
  const [busy, setBusy] = useState(false);
  const [leaves, setLeaves] = useState<StaffLeave[]>([]);

  const loadLeaves = async () => {
    const res = await queryLeavePage({ current: 1, pageSize: 20 });
    setLeaves(res?.data || []);
  };

  useEffect(() => {
    loadLeaves();
  }, []);

  const loadStaff = async () => {
    if (staffOptions.length > 0) return;
    const staffs = await queryStaffByPage({ current: 1, pageSize: 100 });
    setStaffOptions(
      (staffs?.data || []).map((s) => ({ label: s.name || `员工${s.id}`, value: s.id! })),
    );
  };

  const submit = async () => {
    if (!staffId) {
      message.warning('请选择请假员工');
      return;
    }
    if (!reason.trim()) {
      message.warning('请填写事由');
      return;
    }
    const start = range?.[0];
    const end = range?.[1];
    if (!start || !end) {
      message.warning('请选择起止时间');
      return;
    }
    if (end.isBefore(start)) {
      message.warning('结束时间不能早于起始时间');
      return;
    }
    setBusy(true);
    try {
      // 误用 createStaff 会把请假提交成建员工，这里必须走请假接口
      const created = await createLeave({
        staffId,
        leaveType,
        reason: reason.trim(),
        // Jackson(Date) 只认 ISO-8601，空格分隔的 datetime 会被 400（与预约建单同口径）
        startTime: start.format('YYYY-MM-DDTHH:mm:ssZ'),
        endTime: end.format('YYYY-MM-DDTHH:mm:ssZ'),
      });
      if (created?.id) {
        message.success('请假已登记');
        setReason('');
        setRange(null);
        await loadLeaves();
      }
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: number) => {
    await deleteLeave(id);
    message.success('已删除');
    await loadLeaves();
  };

  return (
    <PageContainer
      title="员工请假"
      content="登记员工请假（类型/事由/起止时间）；请假为记录口径，不改员工登录状态、无审批流。"
    >
      <div className="lv-grid">
        <div className="lv-panel">
          <h3>登记请假</h3>
          <div className="lv-form">
            <label>
              员工
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择员工"
                style={{ minWidth: 200 }}
                options={staffOptions}
                value={staffId}
                onDropdownVisibleChange={loadStaff}
                onChange={(v) => setStaffId(v)}
              />
            </label>
            <label>
              类型
              <Radio.Group value={leaveType} onChange={(e) => setLeaveType(e.target.value)}>
                <Radio value={1}>事假</Radio>
                <Radio value={0}>病假</Radio>
              </Radio.Group>
            </label>
            <label>
              起止时间
              <DatePicker.RangePicker
                showTime={{ format: 'HH:mm' }}
                format="YYYY-MM-DD HH:mm"
                value={range}
                onChange={setRange}
              />
            </label>
            <label>
              事由
              <textarea
                className="lv-reason"
                rows={3}
                maxLength={200}
                placeholder="请假事由（必填）"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <Button type="primary" loading={busy} onClick={submit}>
              提交请假
            </Button>
          </div>
        </div>

        <div className="lv-panel">
          <h3>
            近期请假
            <Button
              type="text"
              size="small"
              className="refresh"
              onClick={loadLeaves}
              icon={<ReloadOutlined />}
            />
          </h3>
          {leaves.length === 0 && <div className="lv-empty">暂无请假记录</div>}
          {leaves.map((l) => (
            <div key={l.id} className="lv-row">
              <span className="who">
                <b>{l.staffName || `员工 ${l.staffId}`}</b>
                <i>{l.reason}</i>
              </span>
              <span className={`tp t${l.leaveType ?? 0}`}>
                {LEAVE_TYPE_TEXT[l.leaveType ?? 0] ?? l.leaveType}
              </span>
              <span className="tm">
                {l.startTime ? moment(l.startTime).format('MM-DD HH:mm') : ''}
                {l.endTime ? ` ~ ${moment(l.endTime).format('MM-DD HH:mm')}` : ''}
              </span>
              <Popconfirm title="删除这条请假记录？" onConfirm={() => l.id && remove(l.id)}>
                <Button
                  type="text"
                  size="small"
                  className="del"
                  icon={<DeleteOutlined />}
                  title="删除"
                />
              </Popconfirm>
            </div>
          ))}
        </div>
      </div>
    </PageContainer>
  );
};

export default Leave;
