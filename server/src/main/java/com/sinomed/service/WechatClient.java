package com.sinomed.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 微信小程序服务端调用：jscode2session 换 openid（openid 查卡路径唯一凭据入口）。
 * AppSecret 只在本类内拼接出网，不下发前端；凭据未配置或微信返回错误时抛 IllegalArgumentException，
 * 由全局异常处理透出 400（不落 500）。回执只含 openid——session_key 属敏感凭据不出服务端。
 */
@Component
public class WechatClient {
    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final RestTemplate restTemplate;

    /** 小程序 appid（env WECHAT_APP_ID）；缺省空串=未配置 */
    @Value("${sinomed.wechat.app-id:}")
    private String appId;

    /** 小程序 AppSecret（env WECHAT_APP_SECRET）；缺省空串=未配置 */
    @Value("${sinomed.wechat.app-secret:}")
    private String appSecret;

    public WechatClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** wx.login 的 code 换 openid；code 一次性 5 分钟有效，重放即被微信拒绝 */
    public String codeToOpenid(String code) {
        if (isBlank(appId) || isBlank(appSecret)) {
            throw new IllegalArgumentException("微信凭据未配置（WECHAT_APP_ID / WECHAT_APP_SECRET），openid 查卡不可用");
        }
        String url = JSCODE2SESSION_URL + "?appid=" + appId + "&secret=" + appSecret
                + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";
        Jscode2Session resp;
        try {
            resp = restTemplate.getForObject(url, Jscode2Session.class);
        } catch (RestClientException e) {
            throw new IllegalArgumentException("微信登录失败：无法连接微信服务，请稍后重试");
        }
        if (resp == null || isBlank(resp.getOpenid())) {
            String detail = resp == null ? "无响应" : "errcode=" + resp.getErrcode() + " " + resp.getErrmsg();
            throw new IllegalArgumentException("微信登录失败：" + detail);
        }
        return resp.getOpenid();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 微信 jscode2session 响应；errcode 非 0 时无 openid（errmsg 含原因，如 invalid code） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Jscode2Session {
        private String openid;
        private Integer errcode;
        private String errmsg;
    }
}
