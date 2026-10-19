package com.sinomed.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * wx_accounts.app_secret/aes_key 落库加密（设计稿 docs/design/wechat-official-account.md §7
 * 「凭据不入库明文」）：AES-GCM 可逆加密，密钥走 env WX_OA_CREDENTIAL_KEY——BCrypt 单向不可用，
 * 凭据需回读调微信 API。密钥未配置时拒绝凭据写入（不静默落明文）；不带凭据的账号不受影响。
 * JPA AttributeConverter 由 Hibernate 实例化、不经 Spring，密钥经 @PostConstruct 发布到静态位。
 */
@Slf4j
@Component
public class WxCredentialCipher {

    /** 密文标记前缀（读侧据此区分密文与历史明文行） */
    static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** @Value 注入位（env WX_OA_CREDENTIAL_KEY；缺省空=未启用） */
    @Value("${sinomed.official-account.credential-key:}")
    private String credentialKey;

    private static volatile String staticKey = "";

    @PostConstruct
    void publishKey() {
        staticKey = credentialKey;
        if (staticKey.isBlank()) {
            log.info("WX_OA_CREDENTIAL_KEY 未配置：wx_accounts 凭据列拒绝写入（不带凭据的账号不受影响）");
        }
    }

    static boolean keyConfigured() {
        return !staticKey.isBlank();
    }

    /** 落库前加密；同明文每次换随机 IV（两次密文不同）；传入已加密值则原样返回（实体回读直存的幂等场景） */
    static String encryptOrThrow(String plain) {
        if (plain.startsWith(PREFIX)) {
            return plain;
        }
        String key = requireKey("写入");
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey(key), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("wx credential encrypt failed", e);
        }
    }

    /** 读回解密；无前缀的历史明文行原样返回；密文解不开（密钥不符/被篡改）响亮失败不静默 */
    static String decryptOrThrow(String stored) {
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        String key = requireKey("读取");
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, derivedKey(key), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("wx credential decrypt failed（密钥不符或数据被篡改）", e);
        }
    }

    private static String requireKey(String action) {
        if (staticKey.isBlank()) {
            throw new IllegalStateException(
                    "WX_OA_CREDENTIAL_KEY 未配置（sinomed.official-account.credential-key），微信凭据"
                            + action + "被拒绝——配置后重试，不静默落明文");
        }
        return staticKey;
    }

    private static SecretKey derivedKey(String key) throws GeneralSecurityException {
        return new SecretKeySpec(
                MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
