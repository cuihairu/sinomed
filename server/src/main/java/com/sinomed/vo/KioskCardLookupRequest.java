package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机查卡请求（免登录，身份绑定后才行）：
 * openid_only 模式带 openid（登录态），sms_verified 模式带 phone+smsCode（双因子）。
 * 裸手机号查卡（无身份绑定）一律拒绝——2026-10-14 裁定隐私红线。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "手机查卡请求")
public class KioskCardLookupRequest {
    @Schema(title = "手机号（sms_verified 模式必填，须与验证码同号）", example = "13800001111")
    private String phone;

    @Schema(title = "openid（openid_only 模式必填，小程序登录态天然携带）", example = "oABC123xyz")
    private String openid;

    @Schema(title = "短信验证码（sms_verified 模式必填，先取码再查）", example = "123456")
    private String smsCode;
}
