package com.pani.bi.constant;

/**
 * @author Pani
 * @date Created in 2024/3/20 8:57
 * @description
 */
public interface RedisConstant {
    String LOGIN_CODE_KEY = "login:code:";
    Long LOGIN_CODE_TTL = 2L;
    String LOGIN_USER_KEY = "login:token:";
    Long LOGIN_USER_TTL = 20L;
}
