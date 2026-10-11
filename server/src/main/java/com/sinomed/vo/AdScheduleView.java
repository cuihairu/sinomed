package com.sinomed.vo;


import com.sinomed.entity.AdScheduleEntity;
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
@Schema(title = "广告排期")
public class AdScheduleView {
    @Schema(title = "排期id", example = "1")
    private Long id;

    @NotNull
    @Schema(title = "屏id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long screenId;

    @NotNull
    @Schema(title = "素材id", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long materialId;

    @Schema(title = "排期名", description = "屏幕状态展示用，如「10 月常规排期」；可空", example = "10 月常规排期")
    private String name;

    @Schema(title = "生效星期", description = "逗号分隔 1=周一 … 7=周日；空 = 每天", example = "1,2,3,4,5")
    private String weekdays;

    @Schema(title = "时段起点", description = "HH:mm；空 = 全天", example = "09:00")
    private String startTime;

    @Schema(title = "时段终点", description = "HH:mm；空 = 全天", example = "18:00")
    private String endTime;

    @NotNull
    @Schema(title = "是否启用", description = "1 启用、0 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer enabled;

    @Schema(title = "创建时间", example = "2026-10-01 12:00:00")
    private Date createTime;

    @Schema(title = "更新时间", example = "2026-10-07 09:00:00")
    private Date updateTime;

    public AdScheduleEntity ToAdScheduleEntity() {
        AdScheduleEntity entity = new AdScheduleEntity();
        entity.setId(id);
        entity.setScreenId(screenId);
        entity.setMaterialId(materialId);
        entity.setName(name);
        entity.setWeekdays(weekdays);
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setEnabled(enabled);
        return entity;
    }

    public static AdScheduleView FromAdScheduleEntity(AdScheduleEntity entity) {
        return AdScheduleView.builder()
                .id(entity.getId())
                .screenId(entity.getScreenId())
                .materialId(entity.getMaterialId())
                .name(entity.getName())
                .weekdays(entity.getWeekdays())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .enabled(entity.getEnabled())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
