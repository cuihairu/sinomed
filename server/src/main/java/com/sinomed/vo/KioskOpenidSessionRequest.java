package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小程序登录换 openid 请求（免登录，openid_only 模式专用）：
 * 小程序端 wx.login() 拿 code，服务端经微信 jscode2session 换 openid。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "小程序登录换 openid 请求")
public class KioskOpenidSessionRequest {
    @Schema(title = "wx.login 返回的临时登录凭证 code（一次性，5 分钟有效）", example = "0a3Xf9000Qw9rN1abc200")
    private String code;
}
