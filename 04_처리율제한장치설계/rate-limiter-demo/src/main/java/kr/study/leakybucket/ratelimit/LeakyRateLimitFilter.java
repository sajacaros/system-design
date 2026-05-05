package kr.study.leakybucket.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LeakyRateLimitFilter extends OncePerRequestFilter {

    private final LeakyRateLimiter limiter;

    public LeakyRateLimitFilter(LeakyRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Outcome outcome = limiter.tryEnqueue();
        response.setHeader("X-RateLimit-QueueCapacity", String.valueOf(limiter.capacity()));
        response.setContentType("application/json;charset=UTF-8");

        if (outcome instanceof Outcome.Accepted accepted) {
            response.setStatus(202);
            response.setHeader("X-RateLimit-RequestId", accepted.reqId());
            response.getWriter().write("{\"reqId\":\"" + accepted.reqId() + "\"}");
            return;
        }

        response.setStatus(429);
        response.getWriter().write("{\"error\":\"queue full\"}");
    }
}
