package dev.biga.likebeacon.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Builds and sends one completed reaction-notification batch. */
final class ReactionNotificationSender implements ReactionNotificationSink {

    private final MessageFactory messageFactory;
    private final LikeEffectService effectService;

    ReactionNotificationSender(MessageFactory messageFactory, LikeEffectService effectService) {
        this.messageFactory = messageFactory;
        this.effectService = effectService;
    }

    @Override
    public void send(ReactionNotificationBatch batch) {
        Player target = Bukkit.getPlayer(batch.item().authorUuid());
        if (target == null)
            return;

        Component reactor = text(batch.firstReactorName());
        Component body = text(batch.item().bodyText());
        Component total = number(batch.latestReactionCount());
        if (batch.newReactionCount() == 1L) {
            sendSingle(target, batch, reactor, body, total);
        } else {
            sendGroup(target, batch, reactor, body, total);
        }
        effectService.showReceivedLikeEffect(target);
    }

    private void sendSingle(Player target, ReactionNotificationBatch batch,
            Component reactor, Component body, Component total) {
        if (batch.item().type() == FeedItemType.CHAT) {
            target.sendMessage(messageFactory.notification(
                    "likebeacon.reaction.chat.received", reactor, body, total));
            return;
        }
        target.sendMessage(messageFactory.notification(
                "likebeacon.reaction.direct.received",
                reactor, initiator(batch), body, total));
    }

    private void sendGroup(Player target, ReactionNotificationBatch batch,
            Component reactor, Component body, Component total) {
        Component others = number(batch.newReactionCount() - 1L);
        Component added = number(batch.newReactionCount());
        if (batch.item().type() == FeedItemType.CHAT) {
            target.sendMessage(messageFactory.notification(
                    "likebeacon.reaction.chat.received-group",
                    reactor, others, body, added, total));
            return;
        }
        target.sendMessage(messageFactory.notification(
                "likebeacon.reaction.direct.received-group",
                reactor, others, initiator(batch), body, added, total));
    }

    private static Component initiator(ReactionNotificationBatch batch) {
        String name = batch.initiatorName() != null
                ? batch.initiatorName()
                : batch.item().authorUuid().toString();
        return text(name);
    }

    private static Component number(long value) {
        return text(Long.toString(value));
    }

    private static Component text(String value) {
        return Component.text(value).color(NamedTextColor.WHITE);
    }
}
