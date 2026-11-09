package com.examprep.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Slows down password guessing: at most {@code maxAttempts} POSTs to the login URL per client
 * address within {@code windowMillis}. In-memory and per instance, which is enough for the
 * single-node deployment this project targets; put a shared limiter in front for clusters.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public LoginThrottleFilter(int maxAttempts, long windowMillis, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !(request.getContextPath() + "/login").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowMillis / 1000));
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too many sign-in attempts. Please wait a few minutes and try again.");
            return;
        }
        chain.doFilter(request, response);
    }

    boolean allow(String client) {
        long now = clock.millis();
        if (attempts.size() > 10_000) {
            attempts.values().removeIf(q -> {
                synchronized (q) {
                    return q.isEmpty() || now - q.peekLast() > windowMillis;
                }
            });
        }
        Deque<Long> queue = attempts.computeIfAbsent(client, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                queue.pollFirst();
            }
            if (queue.size() >= maxAttempts) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}
