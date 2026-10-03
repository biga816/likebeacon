package dev.biga.likebeacon.service;

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

    ReactionNotificationBatch(String firstReactorName, FeedItem item,
            String initiatorName, long reactionCount, long now) {
        this.firstReactorName = firstReactorName;
        this.item = item;
        this.initiatorName = initiatorName;
        this.latestReactionCount = reactionCount;
        this.firstAt = now;
        this.lastAt = now;
    }

    void addReaction(long reactionCount, long now) {
        newReactionCount++;
        latestReactionCount = reactionCount;
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
}
