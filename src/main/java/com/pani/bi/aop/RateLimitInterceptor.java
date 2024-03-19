package com.pani.bi.aop;


import com.google.common.util.concurrent.RateLimiter;
import com.pani.bi.annotation.GenChartRateLimit;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.manager.UserRateLimiterManager;

import com.pani.bi.model.entity.User;
import com.pani.bi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;


/**
 * 生成图表 限流
 *
 * @author pani
 */
@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {
    @Resource
    private UserService userService;

    @Resource
    private UserRateLimiterManager rateLimiterManager;

    private final RateLimiter globalRateLimiter;

    public RateLimitInterceptor() {
        globalRateLimiter = RateLimiter.create(5);
    }

    /**
     * 执行拦截 分用户
     *
     * @param joinPoint
     * @param genChartRateLimit
     * @return
     */
    @Around("@annotation(genChartRateLimit)")
    public Object doInterceptorWithUser(ProceedingJoinPoint joinPoint, GenChartRateLimit genChartRateLimit) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.
                getRequestAttributes()).getRequest();
        User loginUser = userService.getLoginUser(request);
        if(loginUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long userId = loginUser.getId();
        if (userId != null) {
            RateLimiter rateLimiter = rateLimiterManager.getRateLimiter
                    (userId, genChartRateLimit.permitsPerSecond());
            if (!rateLimiter.tryAcquire()) {
                // 限流逻辑，返回错误信息或者服务降级
                throw new BusinessException(ErrorCode.TOO_MANY_REQUEST,"请求过于频繁，请稍后再试");
            }
        }
        log.info("用户{}限流判定，放行",userId);
        // 放行
        return joinPoint.proceed();
    }

    /**
     * 执行拦截 不分用户 仅针对API
     *
     * @param joinPoint
     * @param genChartRateLimit
     * @return
     */
    @Around("@annotation(genChartRateLimit)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, GenChartRateLimit genChartRateLimit) throws Throwable {
        if (!globalRateLimiter.tryAcquire()) {
                // 限流逻辑，返回错误信息或者服务降级
                log.info("生成图表限流判定，限流");
                throw new BusinessException(ErrorCode.TOO_MANY_REQUEST,"请求过于频繁，请稍后再试");
            }
        // 放行
        log.info("生成图表限流判定，放行");
        return joinPoint.proceed();
    }
}

