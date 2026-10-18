package com.sinomed.vo;


import com.sinomed.entity.CommissionRuleEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "提成规则")
public class CommissionRuleView {
    @Schema(title = "规则id", example = "1")
    private Long id;

    @NotNull
    @Schema(title = "卡项id", description = "items.id", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @NotNull
    @Schema(title = "岗位", description = "staffs.title 精确匹配，如 技师/美容师/医师", example = "技师", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotNull
    @Schema(title = "提成比例", description = "整数百分比 0–100；金额 = 行小计 × ratio / 100", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer ratio;

    @Schema(title = "创建时间", example = "2026-10-18 21:00:00")
    private Date createTime;

    @Schema(title = "更新时间", example = "2026-10-18 21:00:00")
    private Date updateTime;

    public CommissionRuleEntity ToCommissionRuleEntity(){
        CommissionRuleEntity rule = new CommissionRuleEntity();
        rule.setId(id);
        rule.setItemId(itemId);
        rule.setTitle(title != null ? title.trim() : null);
        rule.setRatio(ratio);
        return rule;
    }

    public static CommissionRuleView FromCommissionRuleEntity(CommissionRuleEntity rule){
        return CommissionRuleView.builder()
                .id(rule.getId())
                .itemId(rule.getItemId())
                .title(rule.getTitle())
                .ratio(rule.getRatio())
                .createTime(rule.getCreateTime())
                .updateTime(rule.getUpdateTime())
                .build();
    }
}
