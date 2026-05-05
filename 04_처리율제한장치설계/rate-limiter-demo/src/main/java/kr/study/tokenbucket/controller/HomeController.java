package kr.study.tokenbucket.controller;

import kr.study.fixedwindow.config.FixedWindowConfig;
import kr.study.fixedwindow.ratelimit.FixedWindowRateLimiter;
import kr.study.leakybucket.config.LeakyBucketConfig;
import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
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

    public HomeController(RateLimiter tokenLimiter,
                          LeakyRateLimiter leakyLimiter,
                          FixedWindowRateLimiter fixedLimiter) {
        this.tokenLimiter = tokenLimiter;
        this.leakyLimiter = leakyLimiter;
        this.fixedLimiter = fixedLimiter;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tokenCapacity", tokenLimiter.capacity());
        model.addAttribute("tokenRefillIntervalSeconds", TokenBucketConfig.REFILL_INTERVAL_SECONDS);
        model.addAttribute("leakyCapacity", leakyLimiter.capacity());
        model.addAttribute("leakyLeakIntervalSeconds", LeakyBucketConfig.LEAK_INTERVAL_SECONDS);
        model.addAttribute("fixedThreshold", fixedLimiter.threshold());
        model.addAttribute("fixedWindowSeconds", FixedWindowConfig.WINDOW_SECONDS);
        return "index";
    }
}
