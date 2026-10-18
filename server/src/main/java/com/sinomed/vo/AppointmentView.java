package com.sinomed.vo;


import com.sinomed.entity.AppointmentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 预约视图：字段与 appointments 表对齐；customerName/customerPhone/itemName 是管理端列表联出的展示字段，落库不涉及。
 * 状态：0 待到店、1 已接待、8 过号、9 已取消。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "预约信息")
public class AppointmentView {
    @Schema(title = "预约id", example = "12")
    private Long id;

    @Schema(title = "顾客id", example = "8")
    private Long customerId;

    @Schema(title = "顾客名（列表联出）", example = "王女士")
    private String customerName;

    @Schema(title = "顾客手机号（列表联出）", example = "13700003333")
    private String customerPhone;

    @Schema(title = "卡项id", example = "3")
    private Long itemId;

    @Schema(title = "卡项名（列表联出）", example = "艾灸温阳调理")
    private String itemName;

    @Schema(title = "接待员工id", example = "1")
    private Long staffId;

    @Schema(title = "接待员工名（列表联出）", example = "沈中医师")
    private String staffName;

    @Schema(title = "工位id（美容业态 b，可空：医馆预约不占工位维）", example = "1")
    private Long roomId;

    @Schema(title = "工位名（列表联出）", example = "美容一号工位")
    private String roomName;

    @Schema(title = "预约时段开始时刻", example = "2026-10-10 10:30:00")
    private Date startTime;

    @Schema(title = "时长（分钟）", example = "60")
    private Integer duration;

    @Schema(title = "状态：0 待到店、1 已接待、8 过号、9 已取消", example = "0")
    private Integer status;

    @Schema(title = "备注", example = "肩颈不适，想约王医生")
    private String remark;

    @Schema(title = "创建时间", example = "2026-10-09 09:00:00")
    private Date createTime;

    @Schema(title = "更新时间", example = "2026-10-09 09:30:00")
    private Date updateTime;

    public static AppointmentView FromAppointmentEntity(AppointmentEntity entity) {
        return AppointmentView.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .itemId(entity.getItemId())
                .staffId(entity.getStaffId())
                .roomId(entity.getRoomId())
                .startTime(entity.getStartTime())
                .duration(entity.getDuration())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
