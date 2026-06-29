package com.skillbridge.model;

import com.skillbridge.utils.CryptoUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 身份证号 JPA 属性转换器。
 * <p>
 * 写入数据库时自动加密，读取时自动解密，对业务层透明。
 * 使用 @Converter(autoApply = false) + @Convert 注解显式指定字段。
 */
@Converter
public class IdCardConverter implements AttributeConverter<String, String> {

    private static volatile CryptoUtil cryptoUtil;

    public static void setCryptoUtil(CryptoUtil cu) {
        IdCardConverter.cryptoUtil = cu;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (cryptoUtil == null || attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        // 已加密的数据（Base64 格式）不再重复加密
        if (isEncrypted(attribute)) {
            return attribute;
        }
        return cryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (cryptoUtil == null || dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        if (!isEncrypted(dbData)) {
            return dbData;
        }
        return cryptoUtil.decrypt(dbData);
    }

    /**
     * 简单判断是否为已加密数据（Base64 且长度 > 36）。
     * 明文身份证号固定 18 位，加密后 Base64 至少 40+ 字符。
     */
    private boolean isEncrypted(String value) {
        return value.length() > 36 && value.matches("^[A-Za-z0-9+/=]+$");
    }
}
