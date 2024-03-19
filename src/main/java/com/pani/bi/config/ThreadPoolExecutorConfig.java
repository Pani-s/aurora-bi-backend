package com.pani.bi.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * @author Pani
 * @date Created in 2023/11/26 21:07
 * @description
 */
@Configuration
public class ThreadPoolExecutorConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int i = 1;

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("Chart Thread" + i);
                i++;
                return thread;
            }
        };
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor
                (2, 4, 100, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(5), threadFactory);
        //忽视策略，米安内
        return threadPoolExecutor;
    }
}
