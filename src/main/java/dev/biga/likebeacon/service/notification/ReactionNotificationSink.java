package dev.biga.likebeacon.service.notification;

@FunctionalInterface
interface ReactionNotificationSink {
    void send(ReactionNotificationBatch batch);
}
