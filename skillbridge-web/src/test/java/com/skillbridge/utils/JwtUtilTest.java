package com.skillbridge.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试。
 * 覆盖：生成 Token、解析 Token、篡改/过期校验、isValid、getUserId/getRole。
 */
class JwtUtilTest {

    /** 测试用密钥（>= 32 字节，满足 HS256 要求） */
    private static final String SECRET = "skillbridge-test-secret-key-must-be-at-least-32-bytes";
    /** Token 有效期：2 小时（毫秒） */
    private static final long EXPIRATION_MILLIS = 7200000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MILLIS);
    }

    /* ===================== generateToken ===================== */

    @Nested
    @DisplayName("generateToken 生成 Token")
    class GenerateToken {

        @Test
        @DisplayName("生成的 Token 非空且非空白")
        void shouldGenerateNonEmptyToken() {
            String token = jwtUtil.generateToken("T001", "teacher");

            assertNotNull(token);
            assertFalse(token.isBlank(), "Token 不应为空白");
        }

        @Test
        @DisplayName("不同用户生成不同 Token")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            String token1 = jwtUtil.generateToken("T001", "teacher");
            String token2 = jwtUtil.generateToken("S001", "student");

            assertNotEquals(token1, token2);
        }
    }

    /* ===================== parseToken ===================== */

    @Nested
    @DisplayName("parseToken 解析 Token")
    class ParseToken {

        @Test
        @DisplayName("成功解析有效 Token 并提取 userId 和 role")
        void shouldParseValidTokenAndExtractClaims() {
            String token = jwtUtil.generateToken("T001", "teacher");

            Claims claims = jwtUtil.parseToken(token);

            assertNotNull(claims);
            assertEquals("T001", claims.get(JwtUtil.CLAIM_USER_ID));
            assertEquals("teacher", claims.get(JwtUtil.CLAIM_ROLE));
        }

        @Test
        @DisplayName("被篡改的 Token（修改一个字符）解析时抛出异常")
        void shouldThrowWhenTokenTampered() {
            String token = jwtUtil.generateToken("T001", "teacher");
            // 在 payload 段中间位置篡改一个字符（避免修改分隔符 .）
            int firstDot = token.indexOf('.');
            int secondDot = token.indexOf('.', firstDot + 1);
            int tamperPos = (firstDot + secondDot) / 2;
            char original = token.charAt(tamperPos);
            char replaced = (original == 'A') ? 'B' : 'A';
            String tampered = token.substring(0, tamperPos) + replaced + token.substring(tamperPos + 1);

            assertThrows(Exception.class, () -> jwtUtil.parseToken(tampered));
        }

        @Test
        @DisplayName("过期的 Token 解析时抛出异常")
        void shouldThrowWhenTokenExpired() throws InterruptedException {
            // 使用极短过期时间（1ms）的 JwtUtil 生成 Token
            JwtUtil shortLivedJwtUtil = new JwtUtil(SECRET, 1L);
            String token = shortLivedJwtUtil.generateToken("T001", "teacher");
            // 等待 Token 过期
            Thread.sleep(10);

            assertThrows(Exception.class, () -> shortLivedJwtUtil.parseToken(token));
        }
    }

    /* ===================== isValid ===================== */

    @Nested
    @DisplayName("isValid 校验 Token")
    class IsValid {

        @Test
        @DisplayName("有效 Token 返回 true")
        void shouldReturnTrueForValidToken() {
            String token = jwtUtil.generateToken("T001", "teacher");

            assertTrue(jwtUtil.isValid(token));
        }

        @Test
        @DisplayName("被篡改的 Token 返回 false")
        void shouldReturnFalseForTamperedToken() {
            String token = jwtUtil.generateToken("T001", "teacher");
            int firstDot = token.indexOf('.');
            int secondDot = token.indexOf('.', firstDot + 1);
            int tamperPos = (firstDot + secondDot) / 2;
            char original = token.charAt(tamperPos);
            char replaced = (original == 'A') ? 'B' : 'A';
            String tampered = token.substring(0, tamperPos) + replaced + token.substring(tamperPos + 1);

            assertFalse(jwtUtil.isValid(tampered));
        }

        @Test
        @DisplayName("非法格式 Token 返回 false")
        void shouldReturnFalseForMalformedToken() {
            assertFalse(jwtUtil.isValid("not-a-valid-token"));
        }

        @Test
        @DisplayName("null Token 返回 false")
        void shouldReturnFalseForNullToken() {
            assertFalse(jwtUtil.isValid(null));
        }
    }

    /* ===================== getUserId / getRole ===================== */

    @Nested
    @DisplayName("getUserId / getRole 提取 Claims")
    class ExtractClaims {

        @Test
        @DisplayName("从 Claims 中正确提取教师 userId 和 role")
        void shouldExtractTeacherUserIdAndRoleFromClaims() {
            String token = jwtUtil.generateToken("T001", "teacher");
            Claims claims = jwtUtil.parseToken(token);

            assertEquals("T001", jwtUtil.getUserId(claims));
            assertEquals("teacher", jwtUtil.getRole(claims));
        }

        @Test
        @DisplayName("从 Claims 中正确提取学生 userId 和 role")
        void shouldExtractStudentUserIdAndRoleFromClaims() {
            String token = jwtUtil.generateToken("2024001", "student");
            Claims claims = jwtUtil.parseToken(token);

            assertEquals("2024001", jwtUtil.getUserId(claims));
            assertEquals("student", jwtUtil.getRole(claims));
        }
    }
}
