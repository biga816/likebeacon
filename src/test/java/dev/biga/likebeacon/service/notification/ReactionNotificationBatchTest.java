package dev.biga.likebeacon.service.notification;

import dev.biga.likebeacon.model.FeedItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionNotificationBatchTest {

    private static final long QUIET_MILLIS = 3_000L;
    private static final long MAX_WAIT_MILLIS = 10_000L;

    @Test
    void becomesDueAfterQuietPeriod() {
        ReactionNotificationBatch batch = batch(0L);

        assertFalse(batch.isDue(2_999L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertTrue(batch.isDue(3_000L, QUIET_MILLIS, MAX_WAIT_MILLIS));
    }

    @Test
    void newReactionResetsQuietPeriodButNotMaximumWait() {
        ReactionNotificationBatch batch = batch(0L);
        batch.addReaction(2L, 0L, 2_000L);
        batch.addReaction(3L, 0L, 9_000L);

        assertFalse(batch.isDue(9_999L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertTrue(batch.isDue(10_000L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertEquals("first-reactor", batch.firstReactorName());
        assertEquals(3L, batch.newReactionCount());
        assertEquals(3L, batch.latestReactionCount());
    }

    @Test
    void quietPeriodUsesMostRecentReactionTime() {
        ReactionNotificationBatch batch = batch(0L);
        batch.addReaction(2L, 0L, 2_000L);

        assertFalse(batch.isDue(4_999L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertTrue(batch.isDue(5_000L, QUIET_MILLIS, MAX_WAIT_MILLIS));
    }

    @Test
    void keepsHighestItemMilestoneReachedInBatch() {
        ReactionNotificationBatch batch = batch(0L);
        batch.addReaction(3L, 3L, 1_000L);
        batch.addReaction(10L, 10L, 2_000L);
        batch.addReaction(11L, 0L, 3_000L);

        assertEquals(10L, batch.itemMilestone());
    }

    private static ReactionNotificationBatch batch(long now) {
        FeedItem item = FeedItem.chat(
                "item", "test", "ABCD", 1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "body", null, null, null, null);
        return new ReactionNotificationBatch("first-reactor", item, null, 1L, 0L, now);
    }
}
