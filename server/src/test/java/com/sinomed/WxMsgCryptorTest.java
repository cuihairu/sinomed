package com.sinomed;

import com.sinomed.service.WxMsgCryptor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全模式消息加解密单元回归（设计稿 §5.3，AES-256-CBC + PKCS7 + receiveid 绑定）。
 * 已知向量（KAT）由 OpenSSL 独立实现按微信官方布局预计算（非同源循环，同 WxSignatureUtil
 * 已知向量先例）：布局 = 16 字节随机串 + 4 字节大端 msg_len + 明文 XML + receiveid，
 * 密钥 = Base64Decode(EncodingAESKey + "=")，IV = 密钥前 16 字节。
 */
class WxMsgCryptorTest {

    private static final String ENC_KEY = "30KYCOUPMps+YQjLeVHL+2EpiyizimzctwXj9lYTYL4";
    private static final String APP_ID = "wxKATAPPID1";

    /** OpenSSL 加密产物（随机前缀固定为 01..10，明文 XML 261 字节，PKCS7 补 8） */
    private static final String CIPHER_1 =
            "FQYQTP4MGa9ihUr7rjp8i2m8XlGsFPkr2IUcDs0o+DPGILJxFmbqqARF3ztiSkBqbBD4LoA6fpkBHp+a3EH+1szMPL86+0jTI5NDWIT9duRcTm2rPe1gJGBhULMfxpdslwF6O5EwJFnnquDzmEfnvtkKjO4g4jouEayLvvg4eyMTI3ALy3qs1QfuHY6bxQgL3cJ/9Gc8/1gsTEbmR26Dd8JEAqjcnpIdjkVR0rE/lrFV0CoMFcHZKsZrPsOPBood85HH0HccKqAbfz8IkJlSAJb0HJeiXVWBhVIeta4xe64T8StpzEAGB9AJQ7R0suJYQSKUkkin4cpRv+3m49XQX7Qr2YSgLsPsNMhjT1UcntcopNivLx+QXIWyVAbZb9cGz+sj8OwkCvtM3Di3EANu/g==";
    private static final String PLAIN_1 =
            "<xml><ToUserName><![CDATA[wxKATAPPID1]]></ToUserName>"
                    + "<FromUserName><![CDATA[fromUser]]></FromUserName>"
                    + "<CreateTime>1348831860</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                    + "<Content><![CDATA[openssl vector 测试]]></Content>"
                    + "<MsgId>1234567890123456</MsgId></xml>";

    /** 整块填充边界向量（补齐后恰为 16 倍数，PKCS7 追加整块 16 字节） */
    private static final String CIPHER_FULL_BLOCK =
            "FQYQTP4MGa9ihUr7rjp8i835S7cwi17CggfJpUX5dMLM2jv0GhVOjXzzTYYkiExiVxO3FVFYOYM4LM33jyFjzT82J5AQuq3McpUNnv/GeON1Sa/Qt21ivVreRNRZtcP7qNw1BXmZKZn+8iLvPvv+CfbByxKchLhftEoZVNh2FxK3Qrv2Wg6OqzFoC3pM1FmFyB9syXRBDOxWwTDqcmBGOSXsU1Bi2DG9cvNZGQLCvi45x0klMZAPWHuMTIN1kK0OXWK59sl4RRV2QEIVplQ6IqQCgez15s6lJbvJ+Oy3SpFYrzkNt7wYeqFrqsF2c3T6Q8Md04hDam7mY6f5AUjqxH/i1tnqqQc4JhHC14fn02WG4ukQCJK17iRSy/uamocENZHdDhk16xnU5WGhnw9kBA==";

    private final WxMsgCryptor cryptor = new WxMsgCryptor();

    @Test
    void decryptsOpensslVector() {
        assertEquals(PLAIN_1, cryptor.decrypt(ENC_KEY, APP_ID, CIPHER_1),
                "OpenSSL 独立实现的密文必须按微信布局解回原明文");
    }

    @Test
    void decryptsFullBlockPaddedVector() {
        assertTrue(cryptor.decrypt(ENC_KEY, APP_ID, CIPHER_FULL_BLOCK).contains("full block edge"),
                "整块 PKCS7 填充边界也要正确剥除");
    }

    @Test
    void rejectsWrongReceiveId() {
        assertThrows(IllegalArgumentException.class,
                () -> cryptor.decrypt(ENC_KEY, "wxOTHERAPPID", CIPHER_1),
                "receiveid 与 appId 不符必须拒绝（防跨号重放）");
    }

    @Test
    void rejectsWrongKeyAndTruncatedCipher() {
        assertThrows(IllegalArgumentException.class,
                () -> cryptor.decrypt("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ", APP_ID, CIPHER_1),
                "错密钥解出的垃圾布局必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> cryptor.decrypt(ENC_KEY, APP_ID, CIPHER_1.substring(0, CIPHER_1.length() - 24)),
                "截断密文必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> cryptor.decrypt(ENC_KEY, APP_ID, "not-base64!!!"),
                "非 base64 必须拒绝");
    }

    @Test
    void encryptRoundTripsAndRandomizesPrefix() {
        String c1 = cryptor.encrypt(ENC_KEY, APP_ID, PLAIN_1);
        String c2 = cryptor.encrypt(ENC_KEY, APP_ID, PLAIN_1);
        assertNotEquals(c1, c2, "随机前缀应使同明文两次密文不同");
        assertEquals(PLAIN_1, cryptor.decrypt(ENC_KEY, APP_ID, c1), "自加密密文应能解回");
    }

    @Test
    void msgSignatureMatchesSortedSha1OfFourParts() throws Exception {
        String encrypt = CIPHER_1;
        String sign = cryptor.signMsg("TOKEN1", "1700000000", "nonceX", encrypt);
        String expected = sha1Hex(sortedJoin("TOKEN1", "1700000000", "nonceX", encrypt));
        assertEquals(expected, sign, "msg_signature = SHA1(字典序(token,timestamp,nonce,encrypt) 拼接)");
        assertTrue(cryptor.checkMsgSignature("TOKEN1", "1700000000", "nonceX", encrypt, sign));
        assertFalse(cryptor.checkMsgSignature("TOKEN1", "1700000000", "nonceX", encrypt + "x", sign),
                "密文被篡改签名必须不命中");
        assertFalse(cryptor.checkMsgSignature("TOKEN1", "1700000000", "nonceX", encrypt, "deadbeef"));
    }

    // ---------- 独立实现（与被测代码同规格，测试侧自算，WxOfficialAccountHttpTest 同款） ----------

    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sortedJoin(String... parts) {
        return String.join("", Arrays.stream(parts).sorted().toArray(String[]::new));
    }
}
