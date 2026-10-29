package com.org.ecommerce.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window rate limiter keyed by an arbitrary string (e.g. "auth:203.0.113.5").
 * Suitable for a single instance; replace with a Redis-backed limiter when running several replicas.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60_000;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records a hit and returns true if the key is still within {@code limitPerMinute}. */
    public boolean tryAcquire(String key, int limitPerMinute) {
        long now = clock.millis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= WINDOW_MS) {
                deque.pollFirst();
            }
            if (deque.size() >= limitPerMinute) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** Drops idle keys so memory does not grow with unique clients. */
    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        long now = clock.millis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last >= WINDOW_MS;
            }
        });
    }

    /** For tests. */
    public void reset() {
        hits.clear();
    }
}
