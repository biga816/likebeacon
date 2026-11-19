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
        batch.addReaction(2L, ReactionMilestones.NONE, 2_000L);
        batch.addReaction(3L, ReactionMilestones.NONE, 9_000L);

        assertFalse(batch.isDue(9_999L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertTrue(batch.isDue(10_000L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertEquals("first-reactor", batch.firstReactorName());
        assertEquals(3L, batch.newReactionCount());
        assertEquals(3L, batch.latestReactionCount());
    }

    @Test
    void quietPeriodUsesMostRecentReactionTime() {
        ReactionNotificationBatch batch = batch(0L);
        batch.addReaction(2L, ReactionMilestones.NONE, 2_000L);

        assertFalse(batch.isDue(4_999L, QUIET_MILLIS, MAX_WAIT_MILLIS));
        assertTrue(batch.isDue(5_000L, QUIET_MILLIS, MAX_WAIT_MILLIS));
    }

    @Test
    void keepsHighestMilestonesReachedInBatch() {
        ReactionNotificationBatch batch = batch(0L);
        batch.addReaction(3L, new ReactionMilestones(3L, 0L), 1_000L);
        batch.addReaction(10L, new ReactionMilestones(10L, 50L), 2_000L);
        batch.addReaction(11L, ReactionMilestones.NONE, 3_000L);

        assertEquals(new ReactionMilestones(10L, 50L), batch.milestones());
    }

    private static ReactionNotificationBatch batch(long now) {
        FeedItem item = FeedItem.chat(
                "item", "test", "ABCD", 1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "body", null, null, null, null);
        return new ReactionNotificationBatch("first-reactor", item, null, 1L, ReactionMilestones.NONE, now);
    }
}
