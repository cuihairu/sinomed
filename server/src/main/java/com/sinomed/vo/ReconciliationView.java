package com.sinomed.vo;

import com.sinomed.entity.RechargeEntity;
import com.sinomed.entity.RefundLogEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 日对账单：按自然日（结算/充值/退款时间）聚合——收款、充值、退款三段 + 净额。
 * 口径见 docs/design/reconciliation.md：充值单列不计入营收（退款补回的储值正数流水
 * 带「退单」note，也不计入充值）；净额 = 实收 - 退款；现金净额收银钱箱核对用。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "日对账单")
public class ReconciliationView {

    @Schema(title = "对账日期 yyyy-MM-dd", example = "2026-10-11")
    private String date;

    @Schema(title = "收款段：单数（含次卡核销）", example = "2")
    private int incomeCount;

    @Schema(title = "收款段：实收合计（元，次卡实收 0）", example = "500")
    private int incomeMoney;

    @Schema(title = "收款段：次卡核销单数（实收 0）", example = "1")
    private int cardCount;

    @Schema(title = "收款构成（只列出现过的，按 payType 升序）")
    private List<PayRow> incomePayTypes;

    @Schema(title = "充值段：充值笔数（退款补回的流水不计）", example = "1")
    private int rechargeCount;

    @Schema(title = "充值段：金额（元，单列不计入营收）", example = "1000")
    private int rechargeMoney;

    @Schema(title = "充值明细（时间倒序）")
    private List<RechargeRow> recharges;

    @Schema(title = "退款段：笔数", example = "1")
    private int refundCount;

    @Schema(title = "退款段：金额合计（元）", example = "88")
    private int refundMoney;

    @Schema(title = "退款构成（按原支付方式，只列出现过的，按 payType 升序）")
    private List<PayRow> refundPayTypes;

    @Schema(title = "退款明细（时间倒序）")
    private List<RefundRow> refunds;

    @Schema(title = "净额 = 实收 - 退款（元）", example = "412")
    private int netMoney;

    @Schema(title = "现金净额 = 现金收款 - 现金退款（元，钱箱核对用）", example = "0")
    private int cashNetMoney;

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "对账·支付方式构成")
    public static class PayRow {
        @Schema(title = "支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣", example = "4")
        private int payType;

        @Schema(title = "支付方式文案", example = "现金")
        private String payTypeText;

        @Schema(title = "该方式笔数", example = "1")
        private int count;

        @Schema(title = "该方式金额（元）", example = "88")
        private int money;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "对账·充值行")
    public static class RechargeRow {
        @Schema(title = "流水id", example = "3")
        private Long id;

        @Schema(title = "顾客id", example = "8")
        private Long customerId;

        @Schema(title = "顾客名（联出）", example = "王女士")
        private String customerName;

        @Schema(title = "金额（元，充值为正）", example = "1000")
        private Integer money;

        @Schema(title = "备注", example = "现场充值")
        private String note;

        @Schema(title = "充值时间", example = "2026-10-11 09:30:00")
        private Date createTime;

        public static RechargeRow from(RechargeEntity entity) {
            return RechargeRow.builder()
                    .id(entity.getId())
                    .customerId(entity.getUserId())
                    .money(entity.getMoney())
                    .note(entity.getNote())
                    .createTime(entity.getCreateTime())
                    .build();
        }
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "对账·退款行")
    public static class RefundRow {
        @Schema(title = "退款流水id", example = "1")
        private Long id;

        @Schema(title = "结算单id", example = "5")
        private Long settlementId;

        @Schema(title = "订单id", example = "12")
        private Long orderId;

        @Schema(title = "顾客id", example = "8")
        private Long customerId;

        @Schema(title = "顾客名（联出）", example = "王女士")
        private String customerName;

        @Schema(title = "退款金额（元）", example = "88")
        private Integer money;

        @Schema(title = "原支付方式", example = "4")
        private Integer payType;

        @Schema(title = "支付方式文案", example = "现金")
        private String payTypeText;

        @Schema(title = "退款理由", example = "收错款")
        private String reason;

        @Schema(title = "操作人 staff id", example = "2")
        private Long staffId;

        @Schema(title = "操作人名（联出）", example = "沈知远")
        private String staffName;

        @Schema(title = "退款时间", example = "2026-10-11 12:05:00")
        private Date createTime;

        public static RefundRow from(RefundLogEntity entity) {
            return RefundRow.builder()
                    .id(entity.getId())
                    .settlementId(entity.getSettlementId())
                    .orderId(entity.getOrderId())
                    .money(entity.getMoney())
                    .payType(entity.getPayType())
                    .reason(entity.getReason())
                    .staffId(entity.getStaffId())
                    .createTime(entity.getCreateTime())
                    .build();
        }
    }
}
