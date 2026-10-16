package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机自助取消预约请求（免登录，身份核验后才行）：sms_verified 模式带 phone+smsCode（双因子，
 * 与 my-appointments 查预约同一套一次性验证码通道）+ 要取消的预约 id（取自查预约回执）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "手机自助取消预约请求")
public class KioskAppointmentCancelRequest {
    @Schema(title = "手机号（须与验证码同号，且为预约归属顾客的手机号）", example = "13800001111")
    private String phone;

    @Schema(title = "短信验证码（一次性，先取码再取消；查预约已用过的码须重新获取）", example = "123456")
    private String smsCode;

    @Schema(title = "要取消的预约 id", example = "12")
    private Long appointmentId;
}
