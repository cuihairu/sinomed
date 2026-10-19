package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密钥未配置（env WX_OA_CREDENTIAL_KEY 缺省空）时的行为回归：
 * 带凭据的写入必须拒绝（不静默落明文，设计稿 §7 凭据不入库明文）；
 * 不带凭据的账号写入照常（模块未激活时无副作用）。
 */
@SpringBootTest
class WxCredentialCryptoNoKeyTest {

    private static final String DB_URL = "jdbc:sqlite:target/wx-cred-nokey-test-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void pinPaths(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
    }

    @Autowired
    private WxAccountRepository accountRepository;

    private WxAccountEntity build(String appSecret) {
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxCREDNOKEY" + System.nanoTime());
        account.setAccountType("service");
        account.setAppSecret(appSecret);
        return account;
    }

    @Test
    void secretWriteWithoutKeyRefused() {
        var ex = assertThrows(RuntimeException.class, () -> accountRepository.save(build("SHOULD-NOT-PERSIST")),
                "密钥未配置时凭据写入必须拒绝");
        assertTrue(String.valueOf(ex.getMessage()).contains("WX_OA_CREDENTIAL_KEY"),
                "拒绝信息应指明配置项：" + ex.getMessage());
    }

    @Test
    void accountWithoutSecretStillPersists() {
        assertDoesNotThrow(() -> accountRepository.save(build(null)), "无凭据账号写入不受密钥影响");
        assertEquals(1, accountRepository.count());
    }
}
