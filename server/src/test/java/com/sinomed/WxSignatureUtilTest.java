package com.sinomed;

import com.sinomed.util.WxSignatureUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信回调验签算法单测（协议口径：SHA1(token,timestamp,nonce 字典序拼接)）。
 * 期望值为独立预计算的已知向量，避免与实现同源循环验证。
 */
class WxSignatureUtilTest {

    /** sha1("1234567890n1token")，即 token=token/timestamp=1234567890/nonce=n1 的字典序拼接 */
    private static final String KNOWN_SIGNATURE = "97f7e8cb035f5b85e63fa5da106b5e3bd3196ec9";

    @Test
    void acceptsKnownVector() {
        assertTrue(WxSignatureUtil.check("token", "1234567890", "n1", KNOWN_SIGNATURE),
                "字典序拼接 SHA1 已知向量应验签通过");
    }

    @Test
    void rejectsWrongSignature() {
        assertFalse(WxSignatureUtil.check("token", "1234567890", "n1", "deadbeef"),
                "签名不符应拒绝");
        assertFalse(WxSignatureUtil.check("otherToken", "1234567890", "n1", KNOWN_SIGNATURE),
                "token 不同签名必不同，应拒绝");
    }

    @Test
    void toleratesNullInputs() {
        assertFalse(WxSignatureUtil.check(null, "1", "2", KNOWN_SIGNATURE), "空 token 不应抛异常");
        assertFalse(WxSignatureUtil.check("token", null, null, null), "空参数不应抛异常");
    }
}
