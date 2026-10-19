package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 粉丝绑定请求（设计稿第一期交付物 5）：H5 授权拿到的 openid + 手机号 + 短信验证码双因子。
 * sms-code 取码端点只用 phone；绑定端点用 openid+phone+smsCode（§5.6：openid 也可由
 * 网页授权一次性 ticket 兑换代替）。无字段全空校验——逐条明确拒绝并落审计。
 */
@Data
@Schema(title = "公众号粉丝绑定请求")
public class WxBindRequest {
    @Schema(title = "粉丝 openid（H5 网页授权获得；与 ticket 二选一）")
    private String openid;

    @Schema(title = "网页授权一次性 ticket（callback 落地 #ticket=...；兑换即废，5 分钟有效）")
    private String ticket;

    @Schema(title = "顾客手机号")
    private String phone;

    @Schema(title = "短信验证码")
    private String smsCode;
}
