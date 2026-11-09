import React from 'react';
import type { Pricing } from '@/services/ant-design-pro/prescription';
import { fenToYuan } from './helpers';

/** 4 · 计价：字典实时试算（无快照），三行账=饮片+代煎服务费；运费不进合计 */
const PricingPanel: React.FC<{
  pricing?: Pricing;
  recvFen: number;
  decoctFeeFen: number;
  doses: number;
  showDecoctionLine: boolean;
}> = ({ pricing, recvFen, decoctFeeFen, doses, showDecoctionLine }) => (
  <div className="pricing">
    <h3>4 · 计价</h3>
    {!pricing || (pricing.herbCount ?? 0) === 0 ? (
      <div className="tip">写药名后按药材字典自动试算</div>
    ) : (
      <div className="sum">
        <span className="total">
          <em>应收</em>
          {fenToYuan(recvFen)}
        </span>
        <span className="detail">
          单剂 {fenToYuan(pricing.perDoseFen)} × {pricing.doses} 付
          {pricing.pricedHerbCount === pricing.herbCount
            ? ''
            : ` · 已比价 ${pricing.pricedHerbCount}/${pricing.herbCount} 味`}
        </span>
        {/* 三行账（原型 admin-herbprescription .fee 口径）：饮片行 + 代煎服务行，合计=两者之和 */}
        <div className="lines">
          <span>
            饮片 {pricing.herbCount} 味 × {pricing.doses} 付
          </span>
          <span>{fenToYuan(pricing.totalFen)}</span>
        </div>
        {showDecoctionLine ? (
          <div className="lines">
            <span>代煎服务（{doses} 袋）</span>
            <span>{fenToYuan(decoctFeeFen)}</span>
          </div>
        ) : null}
        {(pricing.unknownHerbs?.length ?? 0) > 0 ? (
          <div className="unknown">未收录（不计费）：{pricing.unknownHerbs!.join('、')}</div>
        ) : null}
        <div className="note">
          按药材字典实时试算、无快照；应收=饮片+代煎，实际收费仍以卡项订单结算为准
        </div>
      </div>
    )}
  </div>
);

export default PricingPanel;
