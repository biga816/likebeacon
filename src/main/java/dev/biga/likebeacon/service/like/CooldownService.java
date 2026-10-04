package dev.biga.likebeacon.service.like;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Manages in-memory cooldowns for directional sender-target pairs. */
public final class CooldownService {

    private final ConcurrentHashMap<PlayerPair, Long> expiryByPair = new ConcurrentHashMap<>();
    private final long cooldownMillis;
    private final LongSupplier clock;

    public CooldownService(int cooldownSeconds) {
        this(cooldownSeconds, System::currentTimeMillis);
    }

    CooldownService(int cooldownSeconds, LongSupplier clock) {
        this.cooldownMillis = cooldownSeconds * 1_000L;
        this.clock = clock;
    }

    public OptionalLong remainingSeconds(UUID sender, UUID target) {
        PlayerPair pair = new PlayerPair(sender, target);
        Long expiry = expiryByPair.get(pair);
        if (expiry == null)
            return OptionalLong.empty();

        long remainingMillis = expiry - clock.getAsLong();
        if (remainingMillis <= 0L) {
            expiryByPair.remove(pair, expiry);
            return OptionalLong.empty();
        }
        return OptionalLong.of(remainingMillis / 1_000L);
    }

    public void setCooldown(UUID sender, UUID target) {
        expiryByPair.put(new PlayerPair(sender, target), clock.getAsLong() + cooldownMillis);
    }

    private record PlayerPair(UUID sender, UUID target) {
    }
}
