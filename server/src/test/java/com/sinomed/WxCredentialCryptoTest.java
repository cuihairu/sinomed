package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * wx_accounts.app_secret/aes_key 加密落库回归（设计稿 §7「凭据不入库明文」：AES 可逆加密，
 * 密钥走 env WX_OA_CREDENTIAL_KEY，BCrypt 单向不可用——凭据需回读调 API）。
 * 断言一律穿透 JPA 读原始列（converter 之外的 sqlite 直查），落库值必须非明文。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WxCredentialCryptoTest {

    private static final String DB_URL = "jdbc:sqlite:target/wx-cred-crypto-test-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void pinPaths(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("sinomed.official-account.credential-key", () -> "test-credential-key-1019");
    }

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private long saveAccount(String appSecret, String aesKey) {
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxCRED" + System.nanoTime());
        account.setAccountType("service");
        account.setAppSecret(appSecret);
        account.setAesKey(aesKey);
        return accountRepository.save(account).getId();
    }

    /** 穿透 JPA/converter 读原始列值 */
    private String rawColumn(long id, String column) {
        return (String) entityManager.createNativeQuery(
                        "SELECT " + column + " FROM wx_accounts WHERE id = ?1")
                .setParameter(1, id).getSingleResult();
    }

    @Test
    void secretFieldsStoredAsCiphertextAndRoundTrip() {
        long id = saveAccount("PLAIN-SECRET-ABC", "PLAIN-AES-KEY-XYZ");

        String rawSecret = rawColumn(id, "app_secret");
        assertTrue(rawSecret.startsWith("enc:v1:"), "落库值应带密文前缀：" + rawSecret);
        assertFalse(rawSecret.contains("PLAIN-SECRET-ABC"), "落库值不得含明文 secret");
        String rawAes = rawColumn(id, "aes_key");
        assertTrue(rawAes.startsWith("enc:v1:"), "aes_key 落库值应带密文前缀：" + rawAes);
        assertFalse(rawAes.contains("PLAIN-AES-KEY-XYZ"), "aes_key 落库值不得含明文");

        WxAccountEntity back = accountRepository.findById(id).orElseThrow();
        assertEquals("PLAIN-SECRET-ABC", back.getAppSecret(), "读回应还原明文（凭据需回读调 API）");
        assertEquals("PLAIN-AES-KEY-XYZ", back.getAesKey());
    }

    @Test
    void sameSecretEncryptsDifferentlyEachWrite() {
        // 注意：读回实体重设同值不会触发 dirty update（字段未脏），故用两次同明文插入比较密文
        long id1 = saveAccount("SAME-SECRET", null);
        long id2 = saveAccount("SAME-SECRET", null);
        assertNotEquals(rawColumn(id1, "app_secret"), rawColumn(id2, "app_secret"),
                "同明文每次写应换随机 IV，密文不同");
        assertEquals("SAME-SECRET", accountRepository.findById(id1).orElseThrow().getAppSecret());
        assertEquals("SAME-SECRET", accountRepository.findById(id2).orElseThrow().getAppSecret());
    }

    @Test
    void tamperedCiphertextRefusesDecrypt() {
        long id = saveAccount("TAMPER-ME", null);
        transactionTemplate.executeWithoutResult(tx -> entityManager
                .createNativeQuery("UPDATE wx_accounts SET app_secret = ?1 WHERE id = ?2")
                .setParameter(1, "enc:v1:AAAAgarbled")
                .setParameter(2, id).executeUpdate());
        // 密文被篡改/密钥不符：读回必须响亮失败（不静默返回空串掩盖）
        assertThrows(Exception.class, () -> accountRepository.findById(id).orElseThrow().getAppSecret(),
                "篡改密文不得静默还原");
    }
}
