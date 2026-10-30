package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 消息推送 Token 取用优先级（设计稿 §4.1「env remains the token override」）回归：
 * env 有值 = env 优先（账号行 token 不参与验签）；env 未配 = 账号行 token 兜底参与 URL 验签
 * （修复前 DB 列完全不参与，控制台设置的 Token 对回调验签无效）。URL 验签走 GET
 * /api/v1/wx/callback/{accountId}（echostr 回显/拒绝）最窄暴露口。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-token-precedence-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.token=ENVTOKEN"
})
class WxAccountTokenPrecedenceHttpTest {

    private static final String ENV_TOKEN = "ENVTOKEN";
    private static final String DB_TOKEN = "DBTOKEN";

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxAccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void seed() {
        accountRepository.deleteAll();
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxTOKENPREC" + System.nanoTime());
        account.setAccountType("service");
        account.setToken(DB_TOKEN);
        accountId = accountRepository.save(account).getId();
    }

    /** 测试侧独立实现微信验签算法（SHA1 字典序排序拼接），同 WxOfficialAccountHttpTest 先例 */
    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sign(String token, String timestamp, String nonce) throws Exception {
        return sha1Hex(Arrays.stream(new String[]{token, timestamp, nonce}).sorted()
                .collect(Collectors.joining()));
    }

    private String echoWithToken(String token) throws Exception {
        return rest.getForObject("http://localhost:" + port + "/api/v1/wx/callback/" + accountId
                + "?signature=" + sign(token, "1700000000", "nonceP")
                + "&timestamp=1700000000&nonce=nonceP&echostr=PREC-OK", String.class);
    }

    @Test
    void envTokenOverridesDbToken() throws Exception {
        assertEquals("PREC-OK", echoWithToken(ENV_TOKEN), "env 有值应优先于账号行 token");
        assertFalse("PREC-OK".equals(echoWithToken(DB_TOKEN)), "env 有值时账号行 token 不得参与验签");
    }
}
