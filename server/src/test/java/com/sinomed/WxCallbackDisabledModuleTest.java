package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxBindingLogEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxBindingLogRepository;
import com.sinomed.repository.WxMessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 公众号模块开关（设计稿 §7）：enabled 缺省 false=模块整体关闭——GET 验证与 POST 推送
 * 一律 200 拒处理并落审计，不静默吞掉。与 phone-lookup.mode=off 同款「不静默」口径。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-oa-disabled-test-${random.uuid}.db"
})
class WxCallbackDisabledModuleTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxMessageLogRepository messageLogRepository;
    @Autowired
    private WxBindingLogRepository bindingLogRepository;

    private Long accountId;

    @BeforeEach
    void seed() {
        messageLogRepository.deleteAll();
        bindingLogRepository.deleteAll();
        accountRepository.deleteAll();
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOA-DISABLED-1");
        account.setAccountType("service");
        account.setEnabled(1);
        accountId = accountRepository.save(account).getId();
    }

    @Test
    void disabledModuleRefusesGetVerificationAndPost() {
        String get = rest.getForObject("http://localhost:" + port + "/api/v1/wx/callback/" + accountId
                + "?signature=any&timestamp=1&nonce=2&echostr=NO-ECHO", String.class);
        assertFalse("NO-ECHO".equals(get), "模块关闭时 GET 验证不得回显 echostr：" + get);
        assertEquals("success", get, "模块关闭应拒处理回 success（不静默）：" + get);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        String post = rest.exchange("http://localhost:" + port + "/api/v1/wx/callback/" + accountId
                        + "?signature=any&timestamp=1&nonce=3", HttpMethod.POST,
                new HttpEntity<>("<xml><MsgType><![CDATA[text]]></MsgType></xml>", headers), String.class).getBody();
        assertEquals("success", post, "模块关闭 POST 应拒处理回 success：" + post);

        assertEquals(2, messageLogRepository.findAll().size(), "两次拒绝都应落审计（result=disabled）");
        assertEquals(2, messageLogRepository.findAll().stream()
                .filter(l -> "reject".equals(l.getDirection()) && "disabled".equals(l.getResult())).count(),
                "审计行应标 result=disabled");
    }

    @Test
    void disabledModuleRefusesBindFlowWithAudit() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 预期 400：postForEntity 对 4xx 抛异常，捕获后断言状态码
        ResponseEntity<String> codeResp;
        try {
            codeResp = rest.postForEntity("http://localhost:" + port + "/api/v1/wx/bind/" + accountId + "/code",
                    new HttpEntity<>("{\"phone\":\"13900000001\"}", headers), String.class);
        } catch (HttpClientErrorException e) {
            codeResp = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
        assertEquals(400, codeResp.getStatusCode().value(), "模块关闭取码应明确拒绝：" + codeResp.getBody());
        ResponseEntity<String> bindResp;
        try {
            bindResp = rest.postForEntity("http://localhost:" + port + "/api/v1/wx/bind/" + accountId,
                    new HttpEntity<>("{\"openid\":\"oFANX\",\"phone\":\"13900000001\",\"smsCode\":\"123456\"}",
                            headers), String.class);
        } catch (HttpClientErrorException e) {
            bindResp = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
        assertEquals(400, bindResp.getStatusCode().value(), "模块关闭绑定应明确拒绝：" + bindResp.getBody());
        assertEquals(2, bindingLogRepository.count(), "两次拒绝都应落绑定审计");
        assertEquals(2, bindingLogRepository.findAll().stream()
                .filter(a -> "disabled".equals(a.getResult())).count(), "审计行应标 result=disabled");
        assertEquals(0, messageLogRepository.count(), "模块关闭绑定不应落消息流水（只落绑定审计）");
    }
}
