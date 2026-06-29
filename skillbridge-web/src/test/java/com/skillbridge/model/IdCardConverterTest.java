package com.skillbridge.model;

import com.skillbridge.utils.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdCardConverterTest {

    private IdCardConverter converter;
    private CryptoUtil cryptoUtil;

    @BeforeEach
    void setUp() {
        cryptoUtil = new CryptoUtil("TestSecretKeyForIdCardConverter2026");
        converter = new IdCardConverter();
        IdCardConverter.setCryptoUtil(cryptoUtil);
    }

    @Test
    @DisplayName("明文身份证加密后写入数据库")
    void shouldEncryptPlainIdCard() {
        String plain = "440301199001011234";
        String encrypted = converter.convertToDatabaseColumn(plain);

        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        assertTrue(encrypted.length() > 36);
    }

    @Test
    @DisplayName("密文从数据库读取后自动解密")
    void shouldDecryptEncryptedIdCard() {
        String plain = "440301199001011234";
        String encrypted = converter.convertToDatabaseColumn(plain);

        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("已加密数据不重复加密")
    void shouldNotDoubleEncrypt() {
        String plain = "440301199001011234";
        String encrypted = converter.convertToDatabaseColumn(plain);

        String again = converter.convertToDatabaseColumn(encrypted);

        assertEquals(encrypted, again);
    }

    @Test
    @DisplayName("null 值原样返回")
    void shouldReturnNullForNullInput() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("空字符串原样返回")
    void shouldReturnEmptyForEmptyInput() {
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertEquals("", converter.convertToEntityAttribute(""));
    }

    @Test
    @DisplayName("未加密的短字符串（明文身份证）直接返回（无解密操作）")
    void shouldReturnPlaintextForNonEncryptedInput() {
        String plain = "440301199001011234";

        String result = converter.convertToEntityAttribute(plain);

        assertEquals(plain, result);
    }

    @Test
    @DisplayName("CryptoUtil 未初始化时返回原值")
    void shouldReturnOriginalWhenCryptoUtilNotSet() {
        IdCardConverter.setCryptoUtil(null);
        String plain = "440301199001011234";

        assertEquals(plain, converter.convertToDatabaseColumn(plain));
        assertEquals(plain, converter.convertToEntityAttribute(plain));
    }
}
