package kr.study.tokenbucket.controller;

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

    public HomeController(RateLimiter tokenLimiter, LeakyRateLimiter leakyLimiter) {
        this.tokenLimiter = tokenLimiter;
        this.leakyLimiter = leakyLimiter;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tokenCapacity", tokenLimiter.capacity());
        model.addAttribute("tokenRefillIntervalSeconds", TokenBucketConfig.REFILL_INTERVAL_SECONDS);
        model.addAttribute("leakyCapacity", leakyLimiter.capacity());
        model.addAttribute("leakyLeakIntervalSeconds", LeakyBucketConfig.LEAK_INTERVAL_SECONDS);
        return "index";
    }
}
