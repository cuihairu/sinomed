package com.sinomed.service;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.service.impl.WxMessageLogService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 网页授权（设计稿 §5.6，D-OA1..D-OA5）：snsapi_base 授权地址拼装（state 无状态 HMAC 防伪）、
 * 回跳处理（state 验签 → code 换 openid → 一次性 ticket）、H5 ticket 兑换。
 * openid 不进前端落地地址，H5 凭 ticket 兑换后进绑定主流程；拒绝路径全部落 reject 审计并明确报错。
 */
@Slf4j
@Component
public class WxOauthService {

    private final WxAccountRepository accountRepository;
    private final WxOauthTicketStore ticketStore;
    private final RestTemplate wechatRestTemplate;
    private final WxAccountTokenResolver tokenResolver;
    private final WxMessageLogService messageLogService;
    private final WxFanBindService fanBindService;

    /** 模块总开关（env WX_OA_ENABLED）；缺省 false=授权拒绝并审计 */
    private final boolean enabled;

    /** OAuth 回跳 H5 基址（env WX_OA_OAUTH_REDIRECT_BASE，如 https://clinic.example.com）；空则网页授权拒启动 */
    private final String oauthRedirectBase;

    /** 微信 API 出网基址（默认官方域；测试指向本地桩，D-OA4） */
    private final String wechatApiBaseUrl;

    /** OAuth state 有效期（秒，D-OA3：无状态 HMAC，10 分钟内完成授权回跳） */
    private static final long OAUTH_STATE_TTL_SECONDS = 600;

    public WxOauthService(WxAccountRepository accountRepository,
                          WxOauthTicketStore ticketStore,
                          RestTemplate wechatRestTemplate,
                          WxAccountTokenResolver tokenResolver,
                          WxMessageLogService messageLogService,
                          WxFanBindService fanBindService,
                          @Value("${sinomed.official-account.enabled:false}") boolean enabled,
                          @Value("${sinomed.official-account.oauth-redirect-base:}") String oauthRedirectBase,
                          @Value("${sinomed.official-account.wechat-api-base-url:https://api.weixin.qq.com}") String wechatApiBaseUrl) {
        this.accountRepository = accountRepository;
        this.ticketStore = ticketStore;
        this.wechatRestTemplate = wechatRestTemplate;
        this.tokenResolver = tokenResolver;
        this.messageLogService = messageLogService;
        this.fanBindService = fanBindService;
        this.enabled = enabled;
        this.oauthRedirectBase = oauthRedirectBase;
        this.wechatApiBaseUrl = wechatApiBaseUrl;
    }

    /**
     * 网页授权发起（设计稿 §5.6，D-OA1..D-OA3）：拼 snsapi_base 授权地址，
     * state=「accountId|nonce|过期秒」+ HMAC-SHA1(token) 无状态防伪（10 分钟）。
     * 拒绝（模块关/账号停用/缺 redirect 基址）不静默：落 reject 审计并抛 400。
     */
    public String oauthAuthorizeUrl(long accountId) {
        if (!enabled) {
            messageLogService.logReject(accountId, "disabled", "oauth", null, null);
            throw new IllegalArgumentException("公众号模块未开启，无法发起网页授权");
        }
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        if (account == null) {
            messageLogService.logReject(accountId, "account_missing", "oauth", null, null);
            throw new IllegalArgumentException("公众号账号不存在或已停用，无法发起网页授权");
        }
        if (isBlank(oauthRedirectBase)) {
            messageLogService.logReject(accountId, "missing_redirect_base", "oauth", null, null);
            throw new IllegalArgumentException("未配置网页授权回跳基址（oauth-redirect-base），无法发起授权");
        }
        long exp = System.currentTimeMillis() / 1000 + OAUTH_STATE_TTL_SECONDS;
        String payload = accountId + "|" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "|" + exp;
        String state = payload + ":" + hmacSha1Hex(tokenResolver.resolveToken(account), payload);
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + urlEncode(account.getAppId())
                + "&redirect_uri=" + urlEncode(oauthRedirectBase + "/api/v1/wx/oauth/" + accountId + "/callback")
                + "&response_type=code&scope=snsapi_base&state=" + urlEncode(state)
                + "#wechat_redirect";
    }

    /**
     * 网页授权回调（设计稿 §5.6）：state 验签 → code 换 openid（出网可配基址）→
     * 发一次性 ticket → 回跳 H5 绑定页（#ticket=...）。openid 不进前端落地地址，
     * H5 凭 ticket 兑换；失败路径全部落 reject 审计并明确报错。
     */
    public String oauthCallbackRedirectUrl(long accountId, String code, String state) {
        if (!enabled) {
            messageLogService.logReject(accountId, "disabled", "oauth", null, null);
            throw new IllegalArgumentException("公众号模块未开启");
        }
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        if (account == null) {
            messageLogService.logReject(accountId, "account_missing", "oauth", null, null);
            throw new IllegalArgumentException("公众号账号不存在或已停用");
        }
        if (!verifyOauthState(accountId, state)) {
            messageLogService.logReject(accountId, "bad_state", "oauth", null, null);
            throw new IllegalArgumentException("授权 state 校验失败，请重新发起授权");
        }
        String openid = exchangeOpenid(account, code);
        if (openid == null) {
            messageLogService.logReject(accountId, "bad_exchange", "oauth", null, null);
            throw new IllegalArgumentException("授权 code 换取 openid 失败，请重新发起授权");
        }
        return oauthRedirectBase + "/h5/wx/bind?account=" + accountId + "#ticket=" + ticketStore.issue(accountId, openid);
    }

    /**
     * H5 凭 ticket 兑换 openid（一次性，D-OA5）：无效/过期/错账号拒绝并落绑定审计，
     * 兑到的 openid 直接进 bind 主流程（手机号+验证码双因子不变）。
     */
    public String redeemTicket(long accountId, String ticket) {
        return ticketStore.redeem(accountId, ticket).orElseGet(() -> {
            fanBindService.auditBinding(accountId, null, null, null, "bad_ticket");
            throw new IllegalArgumentException("授权 ticket 无效或已过期，请重新发起网页授权");
        });
    }

    /** state 验签：结构 payload:签名 → 常量时间比对 HMAC → 账号一致 + 未过期 */
    private boolean verifyOauthState(long accountId, String state) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        String accountToken = account == null ? null : tokenResolver.resolveToken(account);
        if (isBlank(state) || isBlank(accountToken)) {
            return false;
        }
        int split = state.lastIndexOf(':');
        if (split <= 0 || split == state.length() - 1) {
            return false;
        }
        String payload = state.substring(0, split);
        byte[] expected = hmacSha1Hex(accountToken, payload).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = state.substring(split + 1).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            return false;
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            return false;
        }
        try {
            return Long.parseLong(parts[0]) == accountId
                    && System.currentTimeMillis() / 1000 <= Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** code→openid 换取（D-OA4：可配基址；AppSecret 取自动态解密列；RestTemplate+DTO 同 WechatClient 口径）；失败回 null 不抛 */
    private String exchangeOpenid(WxAccountEntity account, String code) {
        if (isBlank(code) || isBlank(account.getAppSecret())) {
            return null;
        }
        try {
            String url = wechatApiBaseUrl + "/sns/oauth2/access_token?appid=" + urlEncode(account.getAppId())
                    + "&secret=" + urlEncode(account.getAppSecret())
                    + "&code=" + urlEncode(code) + "&grant_type=authorization_code";
            AccessTokenExchange resp = wechatRestTemplate.getForObject(url, AccessTokenExchange.class);
            if (resp == null || (resp.getErrcode() != null && resp.getErrcode() != 0) || isBlank(resp.getOpenid())) {
                return null;
            }
            return resp.getOpenid();
        } catch (Exception e) {
            log.warn("wx oauth exchange failed account={}: {}", account.getId(), e.getMessage());
            return null;
        }
    }

    /** 微信 sns/oauth2/access_token 响应（snsapi_base 只需 openid）；errcode 非 0 时无 openid */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AccessTokenExchange {
        private String openid;
        private Integer errcode;
    }

    private static String hmacSha1Hex(String key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
