package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 手机查卡回执：命中顾客才返回其持卡列表（含余次/有效期），未命中/被拒不返回任何卡项。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "手机查卡回执")
public class KioskCardLookupResult {
    @Schema(title = "顾客id", example = "8")
    private Long customerId;

    @Schema(title = "顾客称呼", example = "张女士")
    private String customerName;

    @Schema(title = "持卡列表（新卡在前）")
    private List<CardLine> cards;

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "持卡行")
    public static class CardLine {
        @Schema(title = "持卡id", example = "21")
        private Long cardId;

        @Schema(title = "卡项id", example = "3")
        private Long itemId;

        @Schema(title = "卡项名", example = "艾灸次卡")
        private String itemName;

        @Schema(title = "总次数", example = "10")
        private Integer totalTimes;

        @Schema(title = "剩余次数", example = "7")
        private Integer remainingTimes;

        @Schema(title = "状态（1 有效 / 0 停用）", example = "1")
        private Integer status;

        @Schema(title = "有效期至（可空=长期有效）", example = "2027-10-14")
        private Date validUntil;
    }
}
