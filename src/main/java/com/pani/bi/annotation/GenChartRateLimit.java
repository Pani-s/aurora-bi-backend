package com.pani.bi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Pani
 * @date Created in 2024/3/19 16:43
 * @description
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GenChartRateLimit {
    /**
     * 每秒允许的请求数
     */
    double permitsPerSecond() default 1d;

}
