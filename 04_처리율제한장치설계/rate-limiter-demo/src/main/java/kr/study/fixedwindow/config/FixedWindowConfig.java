package kr.study.fixedwindow.config;

import kr.study.fixedwindow.counter.FixedWindowCounter;
import kr.study.fixedwindow.ratelimit.FixedWindowRateLimitFilter;
import kr.study.fixedwindow.ratelimit.FixedWindowRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class FixedWindowConfig {

    public static final int THRESHOLD = 5;
    public static final long WINDOW_SECONDS = 30;

    @Bean
    public FixedWindowCounter fixedWindowCounter() {
        return new FixedWindowCounter(THRESHOLD, WINDOW_SECONDS * 1000);
    }

    @Bean
    public FilterRegistrationBean<FixedWindowRateLimitFilter> fixedWindowRateLimitFilter(FixedWindowRateLimiter limiter) {
        FilterRegistrationBean<FixedWindowRateLimitFilter> reg =
                new FilterRegistrationBean<>(new FixedWindowRateLimitFilter(limiter));
        reg.addUrlPatterns("/api/fixed/ping");
        reg.setName("fixedWindowRateLimitFilter");
        return reg;
    }

    @Bean(name = "fixedWindowTickScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService tickScheduler(FixedWindowRateLimiter limiter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fixed-window-tick");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(limiter::tick, 1, 1, TimeUnit.SECONDS);
        return scheduler;
    }
}
