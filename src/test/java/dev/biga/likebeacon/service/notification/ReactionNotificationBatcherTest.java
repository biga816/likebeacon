package dev.biga.likebeacon.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.biga.likebeacon.model.FeedItem;

class ReactionNotificationBatcherTest {

    private final AtomicLong clock = new AtomicLong();
    private ReactionNotificationBatcher batcher;

    @BeforeEach
    void setUp() {
        batcher = new ReactionNotificationBatcher(3_000L, 10_000L, clock::get);
    }

    @Test
    void quietPeriodStartsAgainAtLatestReaction() {
        batcher.add("first", item("item"), null, 2L);
        clock.set(2_000L);
        batcher.add("second", item("item"), null, 3L);

        clock.set(4_999L);
        assertTrue(batcher.removeDue().isEmpty());
        clock.set(5_000L);
        ReactionNotificationBatch batch = onlyBatch(batcher.removeDue());

        assertEquals("first", batch.firstReactorName());
        assertEquals(2L, batch.newReactionCount());
        assertEquals(3L, batch.latestReactionCount());
        assertEquals(0, batcher.pendingCount());
    }

    @Test
    void maximumWaitIsMeasuredFromFirstReaction() {
        batcher.add("first", item("item"), null, 2L);
        clock.set(2_500L);
        batcher.add("second", item("item"), null, 3L);
        clock.set(5_000L);
        batcher.add("third", item("item"), null, 4L);
        clock.set(7_500L);
        batcher.add("fourth", item("item"), null, 5L);
        clock.set(9_999L);
        batcher.add("fifth", item("item"), null, 6L);

        assertTrue(batcher.removeDue().isEmpty());
        clock.set(10_000L);
        assertEquals(5L, onlyBatch(batcher.removeDue()).newReactionCount());
    }

    @Test
    void addingAfterDeadlineReturnsOldBatchAndStartsNewOne() {
        batcher.add("first", item("item"), null, 2L);
        clock.set(3_000L);

        ReactionNotificationBatch expired = onlyBatch(
                batcher.add("next", item("item"), null, 3L));

        assertEquals("first", expired.firstReactorName());
        assertEquals(1, batcher.pendingCount());
        clock.set(6_000L);
        assertEquals("next", onlyBatch(batcher.removeDue()).firstReactorName());
    }

    @Test
    void disabledCoordinatorSendsEveryReactionImmediately() {
        List<ReactionNotificationBatch> sent = new ArrayList<>();
        ReactionNotificationRouter router = new ReactionNotificationRouter(
                false, batcher, sent::add);

        router.enqueue("first", item("item"), null, 2L);
        router.enqueue("second", item("item"), null, 3L);

        assertEquals(2, sent.size());
        assertEquals(1L, sent.get(0).newReactionCount());
        assertEquals(1L, sent.get(1).newReactionCount());
        assertEquals(0, batcher.pendingCount());
    }

    private static ReactionNotificationBatch onlyBatch(List<ReactionNotificationBatch> batches) {
        assertEquals(1, batches.size());
        return batches.get(0);
    }

    private static FeedItem item(String itemId) {
        return FeedItem.chat(
                itemId, "test", "ABCD", 1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "body", null, null, null, null);
    }
}
