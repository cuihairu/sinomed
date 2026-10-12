package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 经营趋势报表：近 N 个月（含当月）按结算时间聚合的营收/单数/次卡核销，联动接诊量。
 * 无数据的月份零填充，月份升序——供月度报表页趋势图。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "经营趋势报表")
public class SettlementTrendView {

    @Schema(title = "统计月数（1-24）", example = "6")
    private int months;

    @Schema(title = "逐月数据（月份升序，含无数据月零填充）")
    private List<MonthRow> rows;

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "趋势·单月")
    public static class MonthRow {

        @Schema(title = "月份 yyyy-MM", example = "2026-09")
        private String month;

        @Schema(title = "当月实收合计（元；次卡核销实收 0）", example = "3516")
        private int money;

        @Schema(title = "当月结算单数（含次卡核销）", example = "7")
        private int count;

        @Schema(title = "次卡核销单数（不产生实收）", example = "1")
        private int cardCount;

        @Schema(title = "当月接诊单数", example = "5")
        private int treatCount;
    }
}
