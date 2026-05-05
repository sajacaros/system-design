package kr.study.leakybucket.config;

import kr.study.leakybucket.bucket.LeakyBucket;
import kr.study.leakybucket.ratelimit.LeakyRateLimitFilter;
import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class LeakyBucketConfig {

    public static final int CAPACITY = 5;
    public static final int LEAK_INTERVAL_SECONDS = 3;

    @Bean
    public LeakyBucket leakyBucket() {
        return new LeakyBucket(CAPACITY);
    }

    @Bean
    public FilterRegistrationBean<LeakyRateLimitFilter> leakyRateLimitFilter(LeakyRateLimiter limiter) {
        FilterRegistrationBean<LeakyRateLimitFilter> reg =
                new FilterRegistrationBean<>(new LeakyRateLimitFilter(limiter));
        reg.addUrlPatterns("/api/leaky/ping");
        reg.setName("leakyRateLimitFilter");
        return reg;
    }

    @Bean(name = "leakyBucketLeakerScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService leakerScheduler(LeakyRateLimiter limiter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "leaky-bucket-leaker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                limiter::leakOne,
                LEAK_INTERVAL_SECONDS,
                LEAK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        return scheduler;
    }
}
