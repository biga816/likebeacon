package dev.biga.likebeacon.service.like;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class CooldownServiceTest {

    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void returnsRemainingTimeAndRemovesExpiredPair() {
        AtomicLong clock = new AtomicLong(1_000L);
        CooldownService service = new CooldownService(60, clock::get);

        assertTrue(service.remainingSeconds(SENDER, TARGET).isEmpty());
        service.setCooldown(SENDER, TARGET);
        assertEquals(OptionalLong.of(60L), service.remainingSeconds(SENDER, TARGET));

        clock.set(2_500L);
        assertEquals(OptionalLong.of(58L), service.remainingSeconds(SENDER, TARGET));
        clock.set(61_000L);
        assertTrue(service.remainingSeconds(SENDER, TARGET).isEmpty());
        assertTrue(service.remainingSeconds(SENDER, TARGET).isEmpty());
    }

    @Test
    void treatsOppositeDirectionAsDifferentPair() {
        CooldownService service = new CooldownService(60, () -> 0L);

        service.setCooldown(SENDER, TARGET);

        assertTrue(service.remainingSeconds(SENDER, TARGET).isPresent());
        assertTrue(service.remainingSeconds(TARGET, SENDER).isEmpty());
    }
}
