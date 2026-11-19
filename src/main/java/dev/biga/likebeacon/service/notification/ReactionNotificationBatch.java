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
    private ReactionMilestones milestones;

    ReactionNotificationBatch(String firstReactorName, FeedItem item,
            String initiatorName, long reactionCount, ReactionMilestones milestones, long now) {
        this.firstReactorName = firstReactorName;
        this.item = item;
        this.initiatorName = initiatorName;
        this.latestReactionCount = reactionCount;
        this.milestones = milestones;
        this.firstAt = now;
        this.lastAt = now;
    }

    void addReaction(long reactionCount, ReactionMilestones milestones, long now) {
        newReactionCount++;
        latestReactionCount = reactionCount;
        this.milestones = this.milestones.merge(milestones);
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

    /** Highest milestones of each kind reached by reactions in this batch. */
    ReactionMilestones milestones() {
        return milestones;
    }
}
