package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小程序登录换 openid 回执：只回 openid，供查卡绑定路径使用；
 * session_key 属敏感凭据不出服务端（微信规范）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "小程序登录换 openid 回执")
public class KioskOpenidSessionResult {
    @Schema(title = "本小程序的用户唯一标识（openid_only 查卡模式使用）", example = "oABC123xyz")
    private String openid;
}
