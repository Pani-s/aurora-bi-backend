package com.pani.bi.manager;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Pani
 * @date Created in 2024/3/19 16:54
 * @description 限流
 */
@Component
public class UserRateLimiterManager {
    private final Map<Long, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();

    public RateLimiter getRateLimiter(Long userId, double permitsPerSecond) {
        /*RateLimiter rateLimiter = rateLimiterMap.get(userId);
        if (rateLimiter == null) {
            rateLimiter = RateLimiter.create(permitsPerSecond);
            rateLimiterMap.putIfAbsent(userId, rateLimiter);
        }
        return rateLimiter;*/
        return rateLimiterMap.computeIfAbsent(userId, id -> RateLimiter.create(permitsPerSecond));
    }
}
