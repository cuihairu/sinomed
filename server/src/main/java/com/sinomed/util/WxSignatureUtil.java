package com.sinomed.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * 微信回调验签（协议口径：SHA1(sort(token, timestamp, nonce) 字典序拼接)）。
 * URL 验证（GET）与明文消息推送（POST）共用；安全模式的 msg_signature（含密文）第二期随
 * WxJava 接入，本类不承担解密。
 */
public final class WxSignatureUtil {

    private WxSignatureUtil() {
    }

    /** 验签：任一参数为空或签名不符返回 false（不抛异常——回调路径异常会放大微信侧重试） */
    public static boolean check(String token, String timestamp, String nonce, String signature) {
        if (isBlank(token) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return false;
        }
        return sha1Hex(sortJoin(token, timestamp, nonce)).equalsIgnoreCase(signature);
    }

    /** 字典序排序后拼接（协议规定） */
    static String sortJoin(String... parts) {
        return Arrays.stream(parts).sorted().reduce("", String::concat);
    }

    static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-1 属 JDK 必备算法，不可达分支
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
