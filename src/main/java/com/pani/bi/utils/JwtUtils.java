package com.pani.bi.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * @author Pani
 * @date Created in 2024/3/20 9:04
 * @description for test
 */
@Slf4j
public class JwtUtils {
    /**
     * 过期时间 20min
     */
    private static final long EXPIRE_TIME = 20 * 60 * 1000;
    /**
     * 密钥盐
     */
    private static final String TOKEN_SECRET = "kookv";

    /**
     * 签名生成
     *
     * @return
     */
    public static String signAndGetToken(String userId, String name) {

        String token = null;
        try {
            Date expiresAt = new Date(System.currentTimeMillis() + EXPIRE_TIME);
            token = JWT.create()
                    .withIssuer("auth0")
                    .withClaim("userId", userId)
                    .withClaim("username", name)
                    .withExpiresAt(expiresAt)
                    // 使用了HMAC256加密算法。
                    .sign(Algorithm.HMAC256(TOKEN_SECRET));
        } catch (Exception e) {
            log.error("生成token时报错{}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return token;

    }

    /**
     * 签名验证
     *
     * @param token
     * @return
     */
    public static boolean verify(String token) {

        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(TOKEN_SECRET)).withIssuer("auth0").build();
            DecodedJWT jwt = verifier.verify(token);
            log.info("JWT认证通过：userId = {} , username = {} , 过期时间 ： {}",
                    jwt.getClaim("userId").asString(), jwt.getClaim("username").asString(), jwt.getExpiresAt());
            return true;
        } catch (Exception e) {
            log.error("jwt校验失败!!!");
            return false;
        }
    }

    /**
     * 通过token得到用户id
     *
     * @param token
     * @return
     */
    public static String getUserId(String token) {
        //先认证
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(TOKEN_SECRET)).withIssuer("auth0").build();
            DecodedJWT jwt = verifier.verify(token);
            String id = jwt.getClaim("userId").asString();
            return id;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

    }
}
