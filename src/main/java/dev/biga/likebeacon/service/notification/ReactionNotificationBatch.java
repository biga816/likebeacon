package dev.biga.likebeacon.service.notification;

import dev.biga.likebeacon.model.FeedItem;

/** Mutable timing and count state for one aggregated reaction notification. */
final class ReactionNotificationBatch {

    private final String firstReactorName;
    private final FeedItem item;
    private final String initiatorName;
    private final long firstAt;
    private long lastAt;
    private long latestReactionCount;
    private long newReactionCount = 1L;
    private long itemMilestone;

    ReactionNotificationBatch(String firstReactorName, FeedItem item,
            String initiatorName, long reactionCount, long itemMilestone, long now) {
        this.firstReactorName = firstReactorName;
        this.item = item;
        this.initiatorName = initiatorName;
        this.latestReactionCount = reactionCount;
        this.itemMilestone = itemMilestone;
        this.firstAt = now;
        this.lastAt = now;
    }

    void addReaction(long reactionCount, long itemMilestone, long now) {
        newReactionCount++;
        latestReactionCount = reactionCount;
        this.itemMilestone = Math.max(this.itemMilestone, itemMilestone);
        lastAt = now;
    }

    boolean isDue(long now, long quietMillis, long maxWaitMillis) {
        return now - lastAt >= quietMillis || now - firstAt >= maxWaitMillis;
    }

    String firstReactorName() {
        return firstReactorName;
    }

    FeedItem item() {
        return item;
    }

    String initiatorName() {
        return initiatorName;
    }

    long latestReactionCount() {
        return latestReactionCount;
    }

    long newReactionCount() {
        return newReactionCount;
    }

    /** Highest item milestone reached in this batch, or {@code 0} when none was reached. */
    long itemMilestone() {
        return itemMilestone;
    }
}
