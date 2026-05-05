package kr.study.tokenbucket.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
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
        int remaining = limiter.tokens();
        response.setHeader("X-Ratelimit-Limit", String.valueOf(limiter.capacity()));
        response.setHeader("X-Ratelimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-Ratelimit-Retry-After", remaining == 0 ? "10" : "0");
    }
}
