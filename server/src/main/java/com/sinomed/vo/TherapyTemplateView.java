package com.sinomed.vo;

import com.sinomed.entity.TherapyTemplateEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 外治处方模板视图：字段与 therapy_templates 表对齐。
 * 模板只存建议值，接诊页套用后回填针灸处方区随单自由改（不写回模板）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "外治处方模板")
public class TherapyTemplateView {
    @Schema(title = "模板id", example = "1")
    private Long id;

    @Schema(title = "模板名（唯一，如「失眠·耳穴压豆」）", example = "失眠·耳穴压豆")
    private String name;

    @Schema(title = "外治方法（如 耳穴压豆、穴位贴敷、艾灸）", example = "耳穴压豆")
    private String method;

    @Schema(title = "建议取穴/部位", example = "神门、交感、皮质下")
    private String target;

    @Schema(title = "建议疗程/频次", example = "每周 2 次 × 4 周")
    private String course;

    @Schema(title = "注意事项", example = "每日自行揉按 3 次，夏季 3 天一换")
    private String note;

    @Schema(title = "上架：0 停用 / 1 启用", example = "1")
    private Integer enabled;

    @Schema(title = "创建时间", example = "2026-10-11 10:00:00")
    private Date createTime;

    @Schema(title = "更新时间", example = "2026-10-11 10:00:00")
    private Date updateTime;

    public static TherapyTemplateView FromEntity(TherapyTemplateEntity entity) {
        return TherapyTemplateView.builder()
                .id(entity.getId())
                .name(entity.getName())
                .method(entity.getMethod())
                .target(entity.getTarget())
                .course(entity.getCourse())
                .note(entity.getNote())
                .enabled(entity.getEnabled())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
