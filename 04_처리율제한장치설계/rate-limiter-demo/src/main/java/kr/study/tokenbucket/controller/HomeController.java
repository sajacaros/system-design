package kr.study.tokenbucket.controller;

import kr.study.tokenbucket.config.TokenBucketConfig;
import kr.study.tokenbucket.ratelimit.RateLimiter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RateLimiter limiter;

    public HomeController(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("capacity", limiter.capacity());
        model.addAttribute("refillIntervalSeconds", TokenBucketConfig.REFILL_INTERVAL_SECONDS);
        return "index";
    }
}
