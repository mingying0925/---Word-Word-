package com.skillbridge.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    private CryptoUtil cryptoUtil;

    @BeforeEach
    void setUp() {
        cryptoUtil = new CryptoUtil("TestSecretKey2026ForUnitTest");
    }

    @Test
    @DisplayName("加密后解密应还原明文")
    void shouldDecryptEncryptedValue() {
        String plaintext = "440301199001011234";
        String encrypted = cryptoUtil.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        String decrypted = cryptoUtil.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("null 和空字符串加密原样返回")
    void shouldReturnOriginalForNullOrEmpty() {
        assertNull(cryptoUtil.encrypt(null));
        assertEquals("", cryptoUtil.encrypt(""));
    }

    @Test
    @DisplayName("null 和空字符串解密原样返回")
    void shouldReturnOriginalForNullOrEmptyDecrypt() {
        assertNull(cryptoUtil.decrypt(null));
        assertEquals("", cryptoUtil.decrypt(""));
    }

    @Test
    @DisplayName("脱敏显示保留前6后4中间用*代替")
    void shouldMaskCorrectly() {
        assertEquals("440301********1234", CryptoUtil.mask("440301199001011234"));
    }

    @Test
    @DisplayName("过短的字符串脱敏原样返回")
    void shouldReturnOriginalForShortMask() {
        assertEquals("12345", CryptoUtil.mask("12345"));
    }

    @Test
    @DisplayName("每次加密产生不同密文（随机IV）")
    void shouldProduceDifferentCiphertexts() {
        String plaintext = "440301199001011234";
        String enc1 = cryptoUtil.encrypt(plaintext);
        String enc2 = cryptoUtil.encrypt(plaintext);
        assertNotEquals(enc1, enc2);
        // 但都能正确解密
        assertEquals(plaintext, cryptoUtil.decrypt(enc1));
        assertEquals(plaintext, cryptoUtil.decrypt(enc2));
    }
}