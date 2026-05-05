package kr.study.tokenbucket.config;

import kr.study.tokenbucket.bucket.TokenBucket;
import kr.study.tokenbucket.ratelimit.RateLimitFilter;
import kr.study.tokenbucket.ratelimit.RateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimiterConfig {

    static final int CAPACITY = 5;
    static final int REFILL_INTERVAL_SECONDS = 10;

    @Bean
    public TokenBucket tokenBucket() {
        return new TokenBucket(CAPACITY, CAPACITY);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter) {
        FilterRegistrationBean<RateLimitFilter> reg =
                new FilterRegistrationBean<>(new RateLimitFilter(limiter));
        reg.addUrlPatterns("/api/ping");
        reg.setName("rateLimitFilter");
        return reg;
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService refillScheduler(RateLimiter limiter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-bucket-refill");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                limiter::refill,
                REFILL_INTERVAL_SECONDS,
                REFILL_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        return scheduler;
    }
}
