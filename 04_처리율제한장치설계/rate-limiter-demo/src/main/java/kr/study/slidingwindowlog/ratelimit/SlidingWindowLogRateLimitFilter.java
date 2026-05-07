package kr.study.slidingwindowlog.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SlidingWindowLogRateLimitFilter extends OncePerRequestFilter {

    private final SlidingWindowLogRateLimiter limiter;

    public SlidingWindowLogRateLimitFilter(SlidingWindowLogRateLimiter limiter) {
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
        int threshold = limiter.threshold();
        int size = limiter.snapshot().size();
        int remaining = Math.max(0, threshold - size);
        response.setHeader("X-Ratelimit-Limit", String.valueOf(threshold));
        response.setHeader("X-Ratelimit-Remaining", String.valueOf(remaining));
    }
}
