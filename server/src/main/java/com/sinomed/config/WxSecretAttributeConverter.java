package com.sinomed.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * wx_accounts 凭据列（app_secret/aes_key）加解密转换器：写路径加密落库、读路径解密还原，
 * 见 {@link WxCredentialCipher}。空值直通（不带凭据的账号无副作用）。
 */
@Converter
public class WxSecretAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        return WxCredentialCipher.encryptOrThrow(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        return WxCredentialCipher.decryptOrThrow(dbData);
    }
}
