package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * openid 查卡路径的登录换取：wx.login 的 code 经服务端 jscode2session 换 openid。
 * 微信出网用 MockRestServiceServer 绑定 wechatRestTemplate 替身（不打真实微信）；
 * 微信返回错误映射 400；凭据缺失路径见 KioskOpenidSessionNoCredentialTest，off 模式见 CardLookupOffTest。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/openid-session-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=openid_only",
        "sinomed.wechat.app-id=wxTEST123",
        "sinomed.wechat.app-secret=secTEST456"
})
class KioskOpenidSessionTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private RestTemplate wechatRestTemplate;

    private MockRestServiceServer wechat;

    @BeforeEach
    void resetWechatStub() {
        wechat = MockRestServiceServer.bindTo(wechatRestTemplate).build();
    }

    /** 免登录区无需 token；4xx 转成响应返回，供断言状态码与 errorMessage */
    private ResponseEntity<String> post(String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/openid-session"))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void exchangesCodeForOpenid() {
        wechat.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wxTEST123&secret=secTEST456&js_code=theCode&grant_type=authorization_code"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"openid\":\"oTEST123\",\"session_key\":\"sk\",\"errcode\":0}",
                        MediaType.APPLICATION_JSON));
        var resp = post("{\"code\":\"theCode\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("oTEST123", JsonPath.read(resp.getBody(), "$.openid"));
        wechat.verify();
    }

    @Test
    void mapsWechatErrorTo400() {
        wechat.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wxTEST123&secret=secTEST456&js_code=badCode&grant_type=authorization_code"))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.APPLICATION_JSON));
        var resp = post("{\"code\":\"badCode\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        String message = JsonPath.read(resp.getBody(), "$.errorMessage");
        assertTrue(message.contains("微信登录失败"), message);
        assertTrue(message.contains("invalid code"), message);
        wechat.verify();
    }

    @Test
    void missingCodeRejected() {
        var resp = post("{}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要小程序登录 code", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }
}
