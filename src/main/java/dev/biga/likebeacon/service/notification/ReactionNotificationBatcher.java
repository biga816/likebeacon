package dev.biga.likebeacon.service.notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import dev.biga.likebeacon.model.FeedItem;

/** Pure-Java state machine for grouping reactions by feed item and deadline. */
final class ReactionNotificationBatcher {

    private final long quietMillis;
    private final long maxWaitMillis;
    private final LongSupplier clock;
    private final Map<String, ReactionNotificationBatch> batches = new HashMap<>();

    ReactionNotificationBatcher(long quietMillis, long maxWaitMillis, LongSupplier clock) {
        if (quietMillis <= 0L)
            throw new IllegalArgumentException("quietMillis must be positive");
        if (maxWaitMillis < quietMillis)
            throw new IllegalArgumentException("maxWaitMillis must be at least quietMillis");
        this.quietMillis = quietMillis;
        this.maxWaitMillis = maxWaitMillis;
        this.clock = clock;
    }

    List<ReactionNotificationBatch> add(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        long now = clock.getAsLong();
        List<ReactionNotificationBatch> due = new ArrayList<>(1);
        ReactionNotificationBatch batch = batches.get(item.itemId());
        if (batch != null && batch.isDue(now, quietMillis, maxWaitMillis)) {
            batches.remove(item.itemId());
            due.add(batch);
            batch = null;
        }

        if (batch == null) {
            batches.put(item.itemId(), new ReactionNotificationBatch(
                    reactorName, item, initiatorName, reactionCount, now));
        } else {
            batch.addReaction(reactionCount, now);
        }
        return List.copyOf(due);
    }

    List<ReactionNotificationBatch> removeDue() {
        long now = clock.getAsLong();
        List<ReactionNotificationBatch> due = new ArrayList<>();
        Iterator<ReactionNotificationBatch> iterator = batches.values().iterator();
        while (iterator.hasNext()) {
            ReactionNotificationBatch batch = iterator.next();
            if (batch.isDue(now, quietMillis, maxWaitMillis)) {
                iterator.remove();
                due.add(batch);
            }
        }
        return List.copyOf(due);
    }

    ReactionNotificationBatch single(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        return new ReactionNotificationBatch(
                reactorName, item, initiatorName, reactionCount, clock.getAsLong());
    }

    void clear() {
        batches.clear();
    }

    int pendingCount() {
        return batches.size();
    }
}
