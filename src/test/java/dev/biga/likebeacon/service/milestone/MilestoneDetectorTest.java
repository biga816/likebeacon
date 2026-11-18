package dev.biga.likebeacon.service.milestone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.biga.likebeacon.model.FeedItem;

class MilestoneDetectorTest {

    private static final long HOUR = 3_600_000L;
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INITIATOR = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void chatItemReachesEveryConfiguredThreshold() {
        MilestoneDetector detector = detector(true, 10L, 24L);

        assertEquals(MilestoneDetector.NONE, detector.itemMilestone(chat(0L), 2L));
        assertEquals(3L, detector.itemMilestone(chat(0L), 3L));
        assertEquals(MilestoneDetector.NONE, detector.itemMilestone(chat(0L), 4L));
        assertEquals(10L, detector.itemMilestone(chat(0L), 10L));
        assertEquals(30L, detector.itemMilestone(chat(0L), 30L));
    }

    @Test
    void directItemSkipsThresholdsBelowDirectMinimum() {
        MilestoneDetector detector = detector(true, 10L, 24L);

        assertEquals(MilestoneDetector.NONE, detector.itemMilestone(direct(0L), 3L));
        assertEquals(10L, detector.itemMilestone(direct(0L), 10L));
        assertEquals(30L, detector.itemMilestone(direct(0L), 30L));
    }

    @Test
    void disabledConfigReachesNoMilestone() {
        MilestoneDetector detector = detector(false, 10L, 24L);

        assertEquals(MilestoneDetector.NONE, detector.itemMilestone(chat(0L), 3L));
    }

    @Test
    void broadcastIsLimitedByItemAge() {
        MilestoneDetector detector = detector(true, 10L, 24L);

        assertTrue(detector.isBroadcastable(chat(0L), 24L * HOUR));
        assertFalse(detector.isBroadcastable(chat(0L), 24L * HOUR + 1L));
    }

    @Test
    void zeroMaximumAgeAllowsAnyItem() {
        MilestoneDetector detector = detector(true, 10L, 0L);

        assertTrue(detector.isBroadcastable(chat(0L), 10_000L * HOUR));
    }

    private static MilestoneDetector detector(boolean enabled, long directMin, long maxAgeHours) {
        return new MilestoneDetector(MilestoneConfig.of(
                enabled, 30L, List.of(3, 10, 30), directMin, maxAgeHours, message -> {
                }));
    }

    private static FeedItem chat(long createdAt) {
        return FeedItem.chat("chat", "test", "ABCD", createdAt, AUTHOR, "body", null, null, null, null);
    }

    private static FeedItem direct(long createdAt) {
        return FeedItem.direct("direct", "test", "EFGH", createdAt, AUTHOR, INITIATOR, "body",
                null, null, null, null);
    }
}
