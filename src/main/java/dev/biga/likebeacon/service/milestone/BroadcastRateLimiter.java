package dev.biga.likebeacon.service.milestone;

import java.util.function.LongSupplier;

/**
 * Enforces a minimum server-wide interval between milestone announcements.
 * Must only be used from the server main thread.
 */
public final class BroadcastRateLimiter {

    private final long minIntervalMillis;
    private final LongSupplier clock;
    private long lastBroadcastAt;
    private boolean broadcasted;

    public BroadcastRateLimiter(long minIntervalMillis, LongSupplier clock) {
        this.minIntervalMillis = minIntervalMillis;
        this.clock = clock;
    }

    /** Records a broadcast and returns {@code true} when the interval has elapsed. */
    public boolean tryAcquire() {
        long now = clock.getAsLong();
        if (broadcasted && now - lastBroadcastAt < minIntervalMillis)
            return false;
        broadcasted = true;
        lastBroadcastAt = now;
        return true;
    }
}
