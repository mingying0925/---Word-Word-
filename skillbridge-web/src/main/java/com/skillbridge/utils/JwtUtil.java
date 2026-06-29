package com.skillbridge.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 核心工具类（无状态鉴权，不依赖 Redis）。
 * <p>
 * 职责：
 * 1. 生成 Token：载荷中携带角色(role: teacher/student)与用户唯一标识(userId: 学号/教师ID)。
 * 2. 解析 Token：还原 Claims，供 Controller 读取用户信息。
 * 3. 校验 Token：判断签名是否被篡改、是否已过期。
 * <p>
 * 采用 HS256 对称签名，密钥从 application.yml 注入，要求 >= 32 字节。
 */
@Component
public class JwtUtil {

    /** Claims 中角色的 key */
    public static final String CLAIM_ROLE = "role";
    /** Claims 中用户唯一标识的 key（学号 / 教师 ID） */
    public static final String CLAIM_USER_ID = "userId";

    private final SecretKey signingKey;
    private final long expirationMillis;

    /**
     * 通过构造注入读取配置，构建签名密钥。
     *
     * @param secret           jwt.secret，HS256 要求 >= 32 字节
     * @param expirationMillis jwt.expiration，Token 有效期（毫秒）
     */
    public JwtUtil(@Value("${jwt.secret:SkillBridgeLocalDevSecretKey2026DoNotUseInProduction0123456789}") String secret,
                   @Value("${jwt.expiration:7200000}") long expirationMillis) {
        // Keys.hmacShaKeyFor 会根据字节数组长度自动选择匹配的 HS 算法长度
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * 生成 JWT Token。
     *
     * @param userId 用户唯一标识（学生学号 / 教师 ID）
     * @param role   角色：teacher 或 student
     * @return 紧凑型 JWT 字符串
     */
    public String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ROLE, role);

        return Jwts.builder()
                .setClaims(claims)                       // 自定义载荷（角色 + 用户标识）
                .setSubject(userId)                      // subject 也存一份 userId，便于通用解析
                .setIssuedAt(now)                        // 签发时间
                .setExpiration(expiry)                   // 过期时间（默认 2 小时）
                .signWith(signingKey, SignatureAlgorithm.HS256)  // HS256 对称签名
                .compact();
    }

    /**
     * 解析 Token 并返回 Claims。
     * <p>
     * 注意：若 Token 被篡改或已过期，本方法会抛出异常：
     * - ExpiredJwtException：已过期
     * - SignatureException / JwtException：签名错误或格式非法
     * 调用方（拦截器）应捕获这些异常做统一处理。
     *
     * @param token JWT 字符串
     * @return Claims 载荷
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验 Token 是否有效（签名正确且未过期）。
     * 不抛异常、只返回布尔值，便于在非拦截器场景下快速判断。
     *
     * @param token JWT 字符串
     * @return true 表示有效
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 从 Claims 中取出用户唯一标识。 */
    public String getUserId(Claims claims) {
        Object v = claims.get(CLAIM_USER_ID);
        return v == null ? claims.getSubject() : String.valueOf(v);
    }

    /** 从 Claims 中取出角色。 */
    public String getRole(Claims claims) {
        Object v = claims.get(CLAIM_ROLE);
        return v == null ? null : String.valueOf(v);
    }
}
