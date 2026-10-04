package dev.biga.likebeacon.service;

@FunctionalInterface
interface ReactionNotificationSink {
    void send(ReactionNotificationBatch batch);
}
