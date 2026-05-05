package kr.study.fixedwindow.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.study.fixedwindow.counter.FixedWindowCounter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class FixedWindowRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;

    public FixedWindowRateLimitFilter(FixedWindowRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean allowed = limiter.tryAcquire();
        addRateLimitHeaders(response);

        if (allowed) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Too Many Requests\"}");
    }

    private void addRateLimitHeaders(HttpServletResponse response) {
        FixedWindowCounter.Snapshot s = limiter.snapshot();
        long windowEndMs = s.windowStartMillis() + limiter.windowSizeMillis();
        long retryAfterSec = Math.max(0, (windowEndMs - System.currentTimeMillis() + 999) / 1000);
        int remaining = Math.max(0, s.threshold() - s.count());

        response.setHeader("X-Ratelimit-Limit", String.valueOf(s.threshold()));
        response.setHeader("X-Ratelimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-Ratelimit-Retry-After",
                remaining == 0 ? String.valueOf(retryAfterSec) : "0");
    }
}
