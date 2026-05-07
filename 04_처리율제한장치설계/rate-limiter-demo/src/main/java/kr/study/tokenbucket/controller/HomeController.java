package kr.study.tokenbucket.controller;

import kr.study.fixedwindow.config.FixedWindowConfig;
import kr.study.fixedwindow.ratelimit.FixedWindowRateLimiter;
import kr.study.leakybucket.config.LeakyBucketConfig;
import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
import kr.study.slidingwindowcounter.config.SlidingWindowCounterConfig;
import kr.study.slidingwindowcounter.ratelimit.SlidingWindowCounterRateLimiter;
import kr.study.slidingwindowlog.config.SlidingWindowLogConfig;
import kr.study.slidingwindowlog.ratelimit.SlidingWindowLogRateLimiter;
import kr.study.tokenbucket.config.TokenBucketConfig;
import kr.study.tokenbucket.ratelimit.RateLimiter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RateLimiter tokenLimiter;
    private final LeakyRateLimiter leakyLimiter;
    private final FixedWindowRateLimiter fixedLimiter;
    private final SlidingWindowLogRateLimiter slidingLogLimiter;
    private final SlidingWindowCounterRateLimiter slidingCounterLimiter;

    public HomeController(RateLimiter tokenLimiter,
                          LeakyRateLimiter leakyLimiter,
                          FixedWindowRateLimiter fixedLimiter,
                          SlidingWindowLogRateLimiter slidingLogLimiter,
                          SlidingWindowCounterRateLimiter slidingCounterLimiter) {
        this.tokenLimiter = tokenLimiter;
        this.leakyLimiter = leakyLimiter;
        this.fixedLimiter = fixedLimiter;
        this.slidingLogLimiter = slidingLogLimiter;
        this.slidingCounterLimiter = slidingCounterLimiter;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tokenCapacity", tokenLimiter.capacity());
        model.addAttribute("tokenRefillIntervalSeconds", TokenBucketConfig.REFILL_INTERVAL_SECONDS);
        model.addAttribute("leakyCapacity", leakyLimiter.capacity());
        model.addAttribute("leakyLeakIntervalSeconds", LeakyBucketConfig.LEAK_INTERVAL_SECONDS);
        model.addAttribute("fixedThreshold", fixedLimiter.threshold());
        model.addAttribute("fixedWindowSeconds", FixedWindowConfig.WINDOW_SECONDS);
        model.addAttribute("slidingThreshold", slidingLogLimiter.threshold());
        model.addAttribute("slidingWindowSeconds", SlidingWindowLogConfig.WINDOW_SECONDS);
        model.addAttribute("slidingCounterThreshold", slidingCounterLimiter.threshold());
        model.addAttribute("slidingCounterWindowSeconds", SlidingWindowCounterConfig.WINDOW_SECONDS);
        return "index";
    }
}
