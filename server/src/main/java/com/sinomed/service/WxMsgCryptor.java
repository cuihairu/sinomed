package com.sinomed.service;

import com.sinomed.util.WxSignatureUtil;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 安全模式消息加解密（设计稿 §5.3，AES-256-CBC + PKCS7 + receiveid 绑定，官方规范手写实现——
 * 落地口径沿用 D-OA3「不引 SDK」决策族，向量锚定见 WxMsgCryptorTest 的 OpenSSL KAT）：
 * 布局 = 16 字节随机串 + 4 字节大端 msg_len + 明文 XML + receiveid；
 * 密钥 = Base64Decode(EncodingAESKey + "=")（43 字符 → 32 字节），IV = 密钥前 16 字节；
 * msg_signature = SHA1(字典序(token, timestamp, nonce, 密文) 拼接)。
 * 结构不符（非 base64、长度不对齐、msg_len 越界、receiveid 不符、填充非法）一律抛
 * IllegalArgumentException——由调用方决定 400/审计口径，异常不落在密码学工具内。
 */
@Component
public class WxMsgCryptor {

    private static final int BLOCK = 16;
    private static final int RANDOM_LEN = 16;
    private static final int LEN_BYTES = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 解密微信密文消息体（Encrypt 元素内的 base64 串），返回明文 XML */
    public String decrypt(String encodingAesKey, String receiveId, String encryptBase64) {
        byte[] plain = aes(Cipher.DECRYPT_MODE, aesKey(encodingAesKey), base64(encryptBase64));
        if (plain.length < RANDOM_LEN + LEN_BYTES + 1) {
            throw new IllegalArgumentException("解密后长度不足以承载消息布局");
        }
        int msgLen = ((plain[RANDOM_LEN] & 0xff) << 24) | ((plain[RANDOM_LEN + 1] & 0xff) << 16)
                | ((plain[RANDOM_LEN + 2] & 0xff) << 8) | (plain[RANDOM_LEN + 3] & 0xff);
        if (msgLen <= 0 || RANDOM_LEN + LEN_BYTES + msgLen > plain.length) {
            throw new IllegalArgumentException("布局 msg_len 与实际长度不符");
        }
        byte[] msg = Arrays.copyOfRange(plain, RANDOM_LEN + LEN_BYTES, RANDOM_LEN + LEN_BYTES + msgLen);
        String tail = new String(Arrays.copyOfRange(plain, RANDOM_LEN + LEN_BYTES + msgLen, plain.length),
                StandardCharsets.UTF_8);
        if (receiveId != null && !receiveId.equals(tail)) {
            throw new IllegalArgumentException("receiveid 与本账号不符");
        }
        return new String(msg, StandardCharsets.UTF_8);
    }

    /** 加密明文 XML 为微信密文（base64）；随机前缀由 SecureRandom 生成 */
    public String encrypt(String encodingAesKey, String receiveId, String plainXml) {
        byte[] xml = plainXml.getBytes(StandardCharsets.UTF_8);
        byte[] receive = receiveId.getBytes(StandardCharsets.UTF_8);
        byte[] plain = new byte[RANDOM_LEN + LEN_BYTES + xml.length + receive.length];
        byte[] random = new byte[RANDOM_LEN];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, plain, 0, RANDOM_LEN);
        plain[RANDOM_LEN] = (byte) (xml.length >>> 24);
        plain[RANDOM_LEN + 1] = (byte) (xml.length >>> 16);
        plain[RANDOM_LEN + 2] = (byte) (xml.length >>> 8);
        plain[RANDOM_LEN + 3] = (byte) xml.length;
        System.arraycopy(xml, 0, plain, RANDOM_LEN + LEN_BYTES, xml.length);
        System.arraycopy(receive, 0, plain, RANDOM_LEN + LEN_BYTES + xml.length, receive.length);
        return Base64.getEncoder().encodeToString(aes(Cipher.ENCRYPT_MODE, aesKey(encodingAesKey), plain));
    }

    /** 安全模式回复/回调的消息体签名：SHA1(字典序(token, timestamp, nonce, encrypt) 拼接) */
    public String signMsg(String token, String timestamp, String nonce, String encrypt) {
        return WxSignatureUtil.sha1Hex(WxSignatureUtil.sortJoin(token, timestamp, nonce, encrypt));
    }

    /** 验证 msg_signature（常量时间比较同 WxSignatureUtil.check 口径，任一参数空即 false） */
    public boolean checkMsgSignature(String token, String timestamp, String nonce, String encrypt,
                                     String msgSignature) {
        if (token == null || token.isBlank() || timestamp == null || timestamp.isBlank()
                || nonce == null || nonce.isBlank() || encrypt == null || encrypt.isBlank()
                || msgSignature == null || msgSignature.isBlank()) {
            return false;
        }
        return signMsg(token, timestamp, nonce, encrypt).equalsIgnoreCase(msgSignature);
    }

    private static byte[] aes(int mode, byte[] key, byte[] payload) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // 16 字节块下 PKCS5 == PKCS7
            cipher.init(mode, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(key, 0, BLOCK)));
            return cipher.doFinal(payload);
        } catch (IllegalArgumentException e) {
            throw e; // 布局/参数错误原样上抛，不吞
        } catch (Exception e) {
            // 错钥/截断/坏填充在 JCE 侧表现为 AEADBadTagException/IllegalBlockSize/BadPadding——统一为布局拒绝
            throw new IllegalArgumentException("消息体解密失败", e);
        }
    }

    private static byte[] aesKey(String encodingAesKey) {
        if (encodingAesKey == null || encodingAesKey.isBlank() || encodingAesKey.length() != 43) {
            throw new IllegalArgumentException("EncodingAESKey 必须为 43 字符");
        }
        return base64(encodingAesKey + "=");
    }

    private static byte[] base64(String text) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("密文非合法 base64", e);
        }
        if (decoded.length == 0 || decoded.length % BLOCK != 0) {
            throw new IllegalArgumentException("密文长度非 16 字节对齐");
        }
        return decoded;
    }
}
