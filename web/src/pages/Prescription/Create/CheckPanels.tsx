import { CheckCircleFilled, WarningFilled } from '@ant-design/icons';
import React from 'react';
import type {
  AllergyResult,
  CompatibilityResult,
  DoseResult,
} from '@/services/ant-design-pro/prescription';

/** 安全审方展示区：配伍（十八反/十九畏）、特殊煎法、过敏史、剂量上限（全部提示不拦截） */
const CheckPanels: React.FC<{
  namedCount: number;
  checking: boolean;
  compat?: CompatibilityResult;
  specialFxCount: number;
  allergy?: AllergyResult;
  allergyRecordCount: number;
  hasCustomer: boolean;
  dose?: DoseResult;
}> = ({
  namedCount,
  checking,
  compat,
  specialFxCount,
  allergy,
  allergyRecordCount,
  hasCustomer,
  dose,
}) => (
  <>
    <div className={`compat ${compat && (compat.findings?.length ?? 0) > 0 ? 'bad' : 'ok'}`}>
      <h3>3 · 配伍审方</h3>
      {namedCount < 2 ? (
        <div className="tip">写够两味药后自动比对十八反 / 十九畏</div>
      ) : checking ? (
        <div className="tip">审方中…</div>
      ) : compat && (compat.findings?.length ?? 0) > 0 ? (
        <div className="hits">
          <div className="head">
            <WarningFilled /> 发现 {compat!.findings!.length} 组配伍提示
          </div>
          {compat!.findings!.map((f, i) => (
            <div className="hit" key={i}>
              <span className={`lv ${f.level === '禁忌' ? 'fan' : 'wei'}`}>{f.level}</span>
              <span className="pair">
                {f.a} × {f.b}
              </span>
              <span className="rule">
                {f.rule} · {f.note}
              </span>
            </div>
          ))}
          <div className="note">提示不拦截，是否照用由医师判断</div>
        </div>
      ) : (
        <div className="pass">
          <CheckCircleFilled /> 未发现配伍禁忌（十八反 / 十九畏）
        </div>
      )}
    </div>

    {/* 特殊煎法提醒（原型审方四查之一）：扫描药味煎法备注，命中关键词即提示；剂量上限需药典区间数据，暂缓 */}
    <div className={`fx-check${specialFxCount > 0 ? ' warn' : ''}`}>
      {specialFxCount > 0 ? (
        <>
          <WarningFilled />
          <span>
            <b>特殊煎法</b>：{specialFxCount} 味需标注（先煎 / 后下等已录入，随笺打印）
          </span>
        </>
      ) : (
        <>
          <CheckCircleFilled />
          <span>
            <b>特殊煎法</b>：无需标注（先煎 / 后下 / 包煎 / 烊化 / 冲服 / 另煎）
          </span>
        </>
      )}
    </div>

    {allergy && (allergy.findings?.length ?? 0) > 0 ? (
      <div className="allergy">
        <div className="ahead">
          <WarningFilled /> 过敏提示 · 顾客过敏史提到 {allergy!.findings!.length} 味当前药方
        </div>
        {allergy!.findings!.map((f, i) => (
          <div className="hit" key={i}>
            <span className="pair">{f.herb}</span>
            <span className="rule">{f.content}</span>
          </div>
        ))}
        <div className="note">按顾客过敏史记录原文匹配（提示不拦截，是否照用由医师判断）</div>
      </div>
    ) : hasCustomer ? (
      // ✓ 常显态：定位顾客且未命中即显示（原型 admin-herbprescription 审方卡口径）
      <div className="allergy ok">
        <div className="ahead">
          <CheckCircleFilled /> 过敏史 ·{' '}
          {allergyRecordCount > 0
            ? `档案 ${allergyRecordCount} 条过敏记录，未命中当前药方`
            : '档案无药物过敏记录'}
        </div>
      </div>
    ) : null}

    {dose && (dose.findings?.length ?? 0) > 0 ? (
      <div className="dose">
        <div className="ahead">
          <WarningFilled /> 剂量提示 · {dose!.findings!.length} 味超单剂上限
        </div>
        {dose!.findings!.map((f, i) => (
          <div className="hit" key={i}>
            <span className="pair">{f.herb}</span>
            <span className="rule">
              {f.weight} 克 ＞ 上限 {f.limit} 克
            </span>
          </div>
        ))}
        <div className="note">按药材字典单剂上限比对（提示不拦截，是否照用由医师判断）</div>
      </div>
    ) : dose && (dose.checked ?? 0) > 0 ? (
      <div className="dose ok">
        <div className="ahead">
          <CheckCircleFilled /> 单剂剂量 ·{' '}
          {(dose.limited ?? 0) > 0
            ? `${dose!.checked} 味中 ${dose!.limited} 味有单剂上限参照，均未超线`
            : '药材字典未设单剂上限，本次无比对'}
        </div>
        {dose.unmatched && dose.unmatched.length > 0 ? (
          <div className="note">
            未收录 {dose.unmatched.length} 味：{dose.unmatched.join('、')}（无剂量参照）
          </div>
        ) : null}
      </div>
    ) : null}
  </>
);

export default CheckPanels;
