package dev.biga.likebeacon.service.milestone;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class BroadcastRateLimiterTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    @Test
    void firstBroadcastIsAlwaysAllowed() {
        assertTrue(new BroadcastRateLimiter(30_000L, clock::get).tryAcquire());
    }

    @Test
    void rejectsBroadcastsInsideInterval() {
        BroadcastRateLimiter limiter = new BroadcastRateLimiter(30_000L, clock::get);
        assertTrue(limiter.tryAcquire());

        clock.addAndGet(29_999L);
        assertFalse(limiter.tryAcquire());
        clock.addAndGet(1L);
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void rejectedBroadcastDoesNotRestartInterval() {
        BroadcastRateLimiter limiter = new BroadcastRateLimiter(30_000L, clock::get);
        assertTrue(limiter.tryAcquire());

        clock.addAndGet(20_000L);
        assertFalse(limiter.tryAcquire());
        clock.addAndGet(10_000L);
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void zeroIntervalAllowsEveryBroadcast() {
        BroadcastRateLimiter limiter = new BroadcastRateLimiter(0L, clock::get);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
    }
}
