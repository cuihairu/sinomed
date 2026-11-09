import { Checkbox, Input, InputNumber, Radio, Select } from 'antd';
import React from 'react';
import { fenToYuan } from './helpers';

type StaffOption = { label: string; value: number };

/** 开方单 meta 状态机：类型（汤剂/膏方）、代煎与领取方式、医师三签、用法三格、备注 */
const RxMetaForm: React.FC<{
  ptype: number;
  setPtype: (v: number) => void;
  craft?: string;
  setCraft: (v?: string) => void;
  decoction: boolean;
  setDecoction: (v: boolean) => void;
  delivery: number;
  setDelivery: (v: number) => void;
  staffOptions: StaffOption[];
  loadStaff: () => void;
  staffId?: number;
  setStaffId: (v?: number) => void;
  reviewStaffId?: number;
  setReviewStaffId: (v?: number) => void;
  checkStaffId?: number;
  setCheckStaffId: (v?: number) => void;
  doses: number;
  setDoses: (v: number) => void;
  usage: string;
  setUsage: (v: string) => void;
  takeMethod: string;
  setTakeMethod: (v: string) => void;
  remark: string;
  setRemark: (v: string) => void;
  decoctFeeFen: number;
}> = ({
  ptype,
  setPtype,
  craft,
  setCraft,
  decoction,
  setDecoction,
  delivery,
  setDelivery,
  staffOptions,
  loadStaff,
  staffId,
  setStaffId,
  reviewStaffId,
  setReviewStaffId,
  checkStaffId,
  setCheckStaffId,
  doses,
  setDoses,
  usage,
  setUsage,
  takeMethod,
  setTakeMethod,
  remark,
  setRemark,
  decoctFeeFen,
}) => (
  <>
    <div className="meta">
      <label className="ttype">
        类型
        <Radio.Group
          value={ptype}
          onChange={(e) => {
            const t = e.target.value as number;
            setPtype(t);
            if (t === 1) {
              setDecoction(false);
            } else {
              setCraft(undefined);
            }
          }}
        >
          <Radio value={0}>汤剂</Radio>
          <Radio value={1}>膏方</Radio>
        </Radio.Group>
        {ptype === 1 ? (
          <Select
            allowClear
            placeholder="收膏方式"
            style={{ minWidth: 120 }}
            value={craft}
            options={[
              { label: '炼蜜', value: '炼蜜' },
              { label: '清膏', value: '清膏' },
              { label: '糖膏', value: '糖膏' },
              { label: '阿胶收膏', value: '阿胶收膏' },
            ]}
            onChange={(v) => setCraft(v)}
          />
        ) : null}
      </label>
      {ptype === 0 ? (
        <label>
          <Checkbox checked={decoction} onChange={(e) => setDecoction(e.target.checked)}>
            代煎 {doses} 袋
          </Checkbox>
          <i className="dfee">
            服务费 {fenToYuan(decoctFeeFen)}
            （¥3/袋）计入计价应收；实际收费仍以卡项订单结算为准
            {decoction ? '；今日 17:00 前可取，加急 2 小时' : ''}
          </i>
        </label>
      ) : (
        <label>
          <i className="dfee">膏方按料计，开方后转药房待制作；领取流转见处方查询页</i>
        </label>
      )}
      {ptype === 0 && decoction ? (
        <label>
          领取方式
          <Radio.Group
            value={delivery}
            onChange={(e) => setDelivery(e.target.value as number)}
            options={[
              { label: '自取', value: 0 },
              { label: '同城配送', value: 1 },
            ]}
            optionType="button"
          />
          <i className="dfee">{delivery === 1 ? '运费 ¥6.00 仅提示' : '前台领取'}</i>
        </label>
      ) : null}
      <label>
        医师
        <Select
          allowClear
          placeholder="选填"
          style={{ minWidth: 160 }}
          options={staffOptions}
          value={staffId}
          onDropdownVisibleChange={loadStaff}
          onChange={(v) => setStaffId(v)}
        />
      </label>
      <label>
        药师审核
        <Select
          allowClear
          placeholder="选填"
          style={{ minWidth: 140 }}
          options={staffOptions}
          value={reviewStaffId}
          onDropdownVisibleChange={loadStaff}
          onChange={(v) => setReviewStaffId(v)}
        />
      </label>
      <label>
        调配 / 复核
        <Select
          allowClear
          placeholder="选填"
          style={{ minWidth: 140 }}
          options={staffOptions}
          value={checkStaffId}
          onDropdownVisibleChange={loadStaff}
          onChange={(v) => setCheckStaffId(v)}
        />
      </label>
    </div>
    {/* 用法三格（原型 .usage：剂数/煎法/服法） */}
    <div className="usage-row">
      <div className="u">
        <div className="k">剂数</div>
        <div className="v">
          <InputNumber min={1} precision={0} value={doses} onChange={(v) => setDoses(v ?? 7)} /> 付
        </div>
      </div>
      <div className="u">
        <div className="k">煎法</div>
        <div className="v">
          <Input
            placeholder="如 水煎服，日一剂"
            value={usage}
            onChange={(e) => setUsage(e.target.value)}
          />
        </div>
      </div>
      <div className="u">
        <div className="k">服法</div>
        <div className="v">
          <Input
            placeholder="如 分早晚温服"
            value={takeMethod}
            onChange={(e) => setTakeMethod(e.target.value)}
          />
        </div>
      </div>
    </div>
    <Input
      className="remark"
      placeholder="备注（选填）：复诊提醒、禁忌等"
      value={remark}
      onChange={(e) => setRemark(e.target.value)}
    />
  </>
);

export default RxMetaForm;
