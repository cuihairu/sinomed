package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 剂量审方结果：findings 为空即未发现超单剂上限。提示不拦截，是否照用由医师判断。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "剂量审方结果")
public class DoseResultView {

    @Schema(title = "参与比对的药材数（去空白后）", example = "8")
    private int checked;

    @Schema(title = "已设单剂上限的条目数（与 checked 同口径，同名多味重复计，有参照才比）", example = "5")
    private int limited;

    @Schema(title = "超上限清单，空 = 未发现超单剂上限")
    private List<Finding> findings;

    @Schema(title = "未收录药材名（药材字典无此药，无剂量参照）")
    private List<String> unmatched;

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "剂量提示")
    public static class Finding {
        @Schema(title = "药名", example = "附子")
        private String herb;

        @Schema(title = "当前单剂克数", example = "30")
        private double weight;

        @Schema(title = "药材字典单剂上限（克）", example = "10")
        private double limit;
    }
}
