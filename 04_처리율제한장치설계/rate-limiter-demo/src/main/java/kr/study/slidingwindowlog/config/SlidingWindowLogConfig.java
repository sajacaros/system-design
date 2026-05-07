package kr.study.slidingwindowlog.config;

import kr.study.slidingwindowlog.log.SlidingWindowLog;
import kr.study.slidingwindowlog.ratelimit.SlidingWindowLogRateLimitFilter;
import kr.study.slidingwindowlog.ratelimit.SlidingWindowLogRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlidingWindowLogConfig {

    public static final int THRESHOLD = 5;
    public static final long WINDOW_SECONDS = 8;

    @Bean
    public SlidingWindowLog slidingWindowLog() {
        return new SlidingWindowLog(THRESHOLD, WINDOW_SECONDS * 1000);
    }

    @Bean
    public FilterRegistrationBean<SlidingWindowLogRateLimitFilter> slidingWindowLogRateLimitFilter(
            SlidingWindowLogRateLimiter limiter) {
        FilterRegistrationBean<SlidingWindowLogRateLimitFilter> reg =
                new FilterRegistrationBean<>(new SlidingWindowLogRateLimitFilter(limiter));
        reg.addUrlPatterns("/api/sliding-log/ping");
        reg.setName("slidingWindowLogRateLimitFilter");
        return reg;
    }
}
