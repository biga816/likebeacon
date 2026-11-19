package dev.biga.likebeacon.service.notification;

import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.service.feed.RecentService;
import dev.biga.likebeacon.service.milestone.MilestoneDetector;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Delivers successful Like notifications and updates their visible feed state. */
public final class LikeNotificationService {

    private final RecentService recentService;
    private final MessageFactory messageFactory;
    private final LikeEffectService effectService;
    private final ReactionNotificationCoordinator reactionNotificationCoordinator;
    private final MilestoneDetector milestoneDetector;
    private final MilestoneNotifier milestoneNotifier;

    public LikeNotificationService(RecentService recentService, MessageFactory messageFactory,
            LikeEffectService effectService, ReactionNotificationCoordinator reactionNotificationCoordinator,
            MilestoneDetector milestoneDetector, MilestoneNotifier milestoneNotifier) {
        this.recentService = recentService;
        this.messageFactory = messageFactory;
        this.effectService = effectService;
        this.reactionNotificationCoordinator = reactionNotificationCoordinator;
        this.milestoneDetector = milestoneDetector;
        this.milestoneNotifier = milestoneNotifier;
    }

    /**
     * Notifies everyone of a new direct Like.
     *
     * @param receivedCount the target's received count after this Like
     */
    public void notifyDirectLike(UUID senderUuid, String senderName, UUID targetUuid, String targetName,
            FeedItem item, long receivedCount) {
        addNewItemForOnlinePlayers(item);

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender != null) {
            sender.sendMessage(messageFactory.success(
                    "likebeacon.direct.sent",
                    Component.text(targetName).color(NamedTextColor.WHITE),
                    Component.text(item.bodyText()).color(NamedTextColor.WHITE)));
        }

        Component senderDisplay = Component.text(senderName).color(NamedTextColor.WHITE);
        Component targetDisplay = Component.text(targetName).color(NamedTextColor.WHITE);
        Audience others = Audience.audience(
                Stream.concat(
                        Bukkit.getOnlinePlayers().stream()
                                .filter(player -> !player.getUniqueId().equals(targetUuid)
                                        && !player.getUniqueId().equals(senderUuid)),
                        Stream.of(Bukkit.getConsoleSender()))
                        .toList());
        others.sendMessage(messageFactory.buildItemMessage(
                item, senderDisplay, targetDisplay, -1, false, true, true));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(messageFactory.notification(target.locale(),
                    "likebeacon.direct.received",
                    senderDisplay,
                    Component.text(item.bodyText()).color(NamedTextColor.WHITE)));
            effectService.showReceivedLikeEffect(target);
        }

        long playerMilestone = milestoneDetector.playerMilestone(receivedCount);
        if (playerMilestone != MilestoneDetector.NONE)
            milestoneNotifier.notifyPlayerMilestone(targetUuid, playerMilestone);
    }

    public void notifyChatPromotion(UUID reactorUuid, String reactorName, FeedItem item, String authorName,
            long receivedCount) {
        addNewItemForOnlinePlayers(item);
        notifyReaction(reactorUuid, reactorName, item, authorName, null, 1L, receivedCount, false);
    }

    /**
     * Confirms a reaction to the reactor and notifies the item author.
     *
     * @param reactionCount the item's reaction count after this reaction
     * @param receivedCount the author's received count after this reaction
     */
    public void notifyReaction(UUID reactorUuid, String reactorName, FeedItem item,
            String targetName, String initiatorName, long reactionCount, long receivedCount,
            boolean aggregateRecipient) {
        Player reactor = Bukkit.getPlayer(reactorUuid);
        if (reactor != null) {
            String key = item.type() == FeedItemType.CHAT
                    ? "likebeacon.reaction.chat.sent"
                    : "likebeacon.reaction.direct.sent";
            reactor.sendMessage(messageFactory.success(
                    key,
                    Component.text(targetName).color(NamedTextColor.WHITE),
                    Component.text(item.bodyText()).color(NamedTextColor.WHITE),
                    messageFactory.displayCodeLabel(item.displayCode())));
        }

        recentService.updateLastSeen(reactorUuid, item.itemId());
        if (aggregateRecipient) {
            reactionNotificationCoordinator.enqueue(
                    reactorName, item, initiatorName, reactionCount, receivedCount);
        } else {
            reactionNotificationCoordinator.notifyImmediately(
                    reactorName, item, initiatorName, reactionCount, receivedCount);
        }
    }

    private void addNewItemForOnlinePlayers(FeedItem item) {
        recentService.add(item);
        Bukkit.getOnlinePlayers().forEach(
                player -> recentService.updateLastSeen(player.getUniqueId(), item.itemId()));
    }
}
