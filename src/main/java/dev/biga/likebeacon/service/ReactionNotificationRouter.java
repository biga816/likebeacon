package dev.biga.likebeacon.service;

import dev.biga.likebeacon.model.FeedItem;

/** Routes reactions through batching or immediate delivery based on configuration. */
final class ReactionNotificationRouter {

    private final boolean aggregationEnabled;
    private final ReactionNotificationBatcher batcher;
    private final ReactionNotificationSink sender;

    ReactionNotificationRouter(boolean aggregationEnabled,
            ReactionNotificationBatcher batcher, ReactionNotificationSink sender) {
        this.aggregationEnabled = aggregationEnabled;
        this.batcher = batcher;
        this.sender = sender;
    }

    void notifyImmediately(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        sender.send(batcher.single(reactorName, item, initiatorName, reactionCount));
    }

    void enqueue(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        if (!aggregationEnabled) {
            notifyImmediately(reactorName, item, initiatorName, reactionCount);
            return;
        }
        batcher.add(reactorName, item, initiatorName, reactionCount).forEach(sender::send);
    }

    void flushDue() {
        batcher.removeDue().forEach(sender::send);
    }

    void clear() {
        batcher.clear();
    }
}
