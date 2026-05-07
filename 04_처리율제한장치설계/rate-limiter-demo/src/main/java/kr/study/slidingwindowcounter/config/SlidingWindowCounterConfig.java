package kr.study.slidingwindowcounter.config;

import kr.study.slidingwindowcounter.counter.SlidingWindowCounter;
import kr.study.slidingwindowcounter.ratelimit.SlidingWindowCounterRateLimitFilter;
import kr.study.slidingwindowcounter.ratelimit.SlidingWindowCounterRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class SlidingWindowCounterConfig {

    public static final int THRESHOLD = 7;
    public static final long WINDOW_SECONDS = 10;

    @Bean
    public SlidingWindowCounter slidingWindowCounter() {
        return new SlidingWindowCounter(THRESHOLD, WINDOW_SECONDS * 1000);
    }

    @Bean
    public FilterRegistrationBean<SlidingWindowCounterRateLimitFilter> slidingWindowCounterRateLimitFilter(
            SlidingWindowCounterRateLimiter limiter) {
        FilterRegistrationBean<SlidingWindowCounterRateLimitFilter> reg =
                new FilterRegistrationBean<>(new SlidingWindowCounterRateLimitFilter(limiter));
        reg.addUrlPatterns("/api/sliding-counter/ping");
        reg.setName("slidingWindowCounterRateLimitFilter");
        return reg;
    }

    @Bean(name = "slidingWindowCounterTickScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService tickScheduler(SlidingWindowCounterRateLimiter limiter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sliding-counter-tick");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(limiter::tick, 1, 1, TimeUnit.SECONDS);
        return scheduler;
    }
}
