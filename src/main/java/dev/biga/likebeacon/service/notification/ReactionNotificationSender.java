package dev.biga.likebeacon.service.notification;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.service.milestone.MilestoneDetector;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Builds and sends one completed reaction-notification batch. */
final class ReactionNotificationSender implements ReactionNotificationSink {

    private final MessageFactory messageFactory;
    private final LikeEffectService effectService;
    private final MilestoneNotifier milestoneNotifier;

    ReactionNotificationSender(MessageFactory messageFactory, LikeEffectService effectService,
            MilestoneNotifier milestoneNotifier) {
        this.messageFactory = messageFactory;
        this.effectService = effectService;
        this.milestoneNotifier = milestoneNotifier;
    }

    @Override
    public void send(ReactionNotificationBatch batch) {
        Player target = Bukkit.getPlayer(batch.item().authorUuid());
        if (target != null)
            sendReceived(target, batch);
        // Sent after the received notification so the author learns who liked
        // the item before seeing the server-wide announcement.
        ReactionMilestones milestones = batch.milestones();
        if (milestones.item() != MilestoneDetector.NONE)
            milestoneNotifier.notifyItemMilestone(batch.item(), batch.initiatorName(), milestones.item());
        if (milestones.player() != MilestoneDetector.NONE)
            milestoneNotifier.notifyPlayerMilestone(batch.item().authorUuid(), milestones.player());
    }

    private void sendReceived(Player target, ReactionNotificationBatch batch) {
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
            target.sendMessage(messageFactory.notification(target.locale(),
                    "likebeacon.reaction.chat.received", reactor, body, total));
            return;
        }
        target.sendMessage(messageFactory.notification(target.locale(),
                "likebeacon.reaction.direct.received",
                reactor, initiator(batch), body, total));
    }

    private void sendGroup(Player target, ReactionNotificationBatch batch,
            Component reactor, Component body, Component total) {
        Component others = number(batch.newReactionCount() - 1L);
        Component added = number(batch.newReactionCount());
        if (batch.item().type() == FeedItemType.CHAT) {
            target.sendMessage(messageFactory.notification(target.locale(),
                    "likebeacon.reaction.chat.received-group",
                    reactor, others, body, added, total));
            return;
        }
        target.sendMessage(messageFactory.notification(target.locale(),
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
