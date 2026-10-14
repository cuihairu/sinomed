package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机自助查预约回执（跨设备）：手机号 + 验证码通过后按手机号回本手机号未过期待到店预约。
 * startTime 与建约入参同格式（yyyy-MM-dd HH:mm），小程序按该格式展示与判断是否临近。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "手机自助查预约回执")
public class KioskMyAppointmentsResult {
    @Schema(title = "待到店预约（未过期、新约在前；无则空数组，不造数据）")
    private List<AppointmentLine> appointments = new ArrayList<>();

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Schema(title = "一条待到店预约")
    public static class AppointmentLine {
        @Schema(title = "预约号", example = "128")
        private Long appointmentId;

        @Schema(title = "预约时段（yyyy-MM-dd HH:mm）", example = "2026-10-20 09:00")
        private String startTime;

        @Schema(title = "项目名（约期未选项目则为 null）")
        private String itemName;

        @Schema(title = "状态：0 待到店", example = "0")
        private Integer status;
    }
}
