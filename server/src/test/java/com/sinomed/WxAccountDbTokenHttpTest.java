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
 * 账号行 token 兜底（env 未配）回归：设计稿 §4.1「env remains the token override」——env 留空时
 * 控制台写入 wx_accounts.token 的 Token 必须参与 URL 验签（修复前 DB 列完全不参与，静默失效）。
 * 本上下文不设 sinomed.official-account.token（默认空串），账号行 token 应生效。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-db-token-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true"
})
class WxAccountDbTokenHttpTest {

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
        account.setAppId("wxDBTOKEN" + System.nanoTime());
        account.setAccountType("service");
        account.setToken(DB_TOKEN);
        accountId = accountRepository.save(account).getId();
    }

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
                + "?signature=" + sign(token, "1700000000", "nonceD")
                + "&timestamp=1700000000&nonce=nonceD&echostr=DB-OK", String.class);
    }

    @Test
    void dbTokenParticipatesWhenEnvEmpty() throws Exception {
        assertEquals("DB-OK", echoWithToken(DB_TOKEN), "env 未配时应以账号行 token 验签（不静默失效）");
        assertFalse("DB-OK".equals(echoWithToken("WRONG")), "错误 token 应拒绝回显");
    }
}
