package com.sinomed.service;

import com.sinomed.entity.WxAccountEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 消息推送 Token 取用优先级（设计稿 §4.1「env remains the token override」）：
 * env（WX_OA_TOKEN）有值优先，否则取账号行 token。回调验签与 OAuth state 验签共用同一优先级，
 * 拆出独立组件避免两处各持一份 env 配置。
 */
@Component
public class WxAccountTokenResolver {

    /** 消息推送 Token（env WX_OA_TOKEN）；第一期单账号，验签用 */
    @Value("${sinomed.official-account.token:}")
    private String token;

    public String resolveToken(WxAccountEntity account) {
        return !isBlank(token) ? token.trim() : (account == null ? "" : (account.getToken() == null ? "" : account.getToken().trim()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
