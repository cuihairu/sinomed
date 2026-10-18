package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公众号能力探测 HTTP 回归（设计稿 §6.1/§6.2）：能力集统一由 account_type + verified 两字段
 * 驱动返回（前端据此隐藏入口），不写死枚举分支；未认证订阅号按矩阵降级。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-oa-cap-test-${random.uuid}.db"
})
class WxCapabilitiesHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxAccountRepository accountRepository;

    private String token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        return (String) resp.getBody().get("token");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
    }

    private long seedAccount(String type, int verified) {
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxCAP" + type + verified + System.nanoTime());
        account.setAccountType(type);
        account.setVerified(verified);
        return accountRepository.save(account).getId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capabilities(String token, long accountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(url("/api/v1/wx/capabilities/" + accountId), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
    }

    @Test
    void unverifiedSubscriptionLacksMenuServiceAndMass() {
        String token = token();
        Map<String, Object> caps = capabilities(token, seedAccount("subscription", 0));
        assertEquals(Boolean.TRUE, caps.get("callback"), "消息回调三档账号都可用");
        assertEquals(Boolean.TRUE, caps.get("materials"), "素材上传三档账号都可用");
        assertEquals(Boolean.FALSE, caps.get("menuManagement"), "未认证订阅号无自定义菜单");
        assertEquals(Boolean.FALSE, caps.get("customerService"), "未认证订阅号无客服消息");
        assertEquals(Boolean.FALSE, caps.get("massMessaging"), "未认证订阅号无群发");
        assertEquals(Boolean.FALSE, caps.get("templateMessage"), "订阅号无模板消息");
    }

    @Test
    void verifiedServiceAccountHasFullCapabilities() {
        String token = token();
        Map<String, Object> caps = capabilities(token, seedAccount("service", 1));
        assertEquals(Boolean.TRUE, caps.get("menuManagement"), "服务号有自定义菜单");
        assertEquals(Boolean.TRUE, caps.get("customerService"), "服务号有客服消息");
        assertEquals(Boolean.TRUE, caps.get("massMessaging"), "服务号可群发");
        assertEquals(Boolean.TRUE, caps.get("templateMessage"), "服务号有模板消息");
    }

    @Test
    void verifiedSubscriptionHasMassButNoTemplate() {
        String token = token();
        Map<String, Object> caps = capabilities(token, seedAccount("subscription", 1));
        assertEquals(Boolean.TRUE, caps.get("menuManagement"), "认证订阅号有自定义菜单");
        assertEquals(Boolean.TRUE, caps.get("massMessaging"), "认证订阅号有订阅通知群发（一天一条）");
        assertEquals(Boolean.FALSE, caps.get("templateMessage"), "订阅号无模板消息");
    }

    @Test
    void missingAccountRejected400() {
        String token = token();
        try {
            capabilities(token, 999999L);
            assertTrue(false, "不存在的账号应 400");
        } catch (HttpClientErrorException e) {
            assertEquals(400, e.getStatusCode().value());
        }
    }

    @Test
    void requiresAuth() {
        try {
            rest.exchange(url("/api/v1/wx/capabilities/1"), HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);
            assertTrue(false, "能力探测属管理端接口，应要求登录");
        } catch (HttpClientErrorException e) {
            assertEquals(401, e.getStatusCode().value());
        }
    }
}
