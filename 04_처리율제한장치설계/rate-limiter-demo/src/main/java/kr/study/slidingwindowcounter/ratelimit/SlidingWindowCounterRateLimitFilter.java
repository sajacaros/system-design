package kr.study.slidingwindowcounter.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.study.slidingwindowcounter.counter.SlidingWindowCounter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SlidingWindowCounterRateLimitFilter extends OncePerRequestFilter {

    private final SlidingWindowCounterRateLimiter limiter;

    public SlidingWindowCounterRateLimitFilter(SlidingWindowCounterRateLimiter limiter) {
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
        SlidingWindowCounter.Snapshot s = limiter.snapshot();
        int remaining = (int) Math.max(0, Math.floor(s.threshold() - s.weighted()));
        response.setHeader("X-Ratelimit-Limit", String.valueOf(s.threshold()));
        response.setHeader("X-Ratelimit-Remaining", String.valueOf(remaining));
    }
}
