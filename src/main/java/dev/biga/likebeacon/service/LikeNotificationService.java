package dev.biga.likebeacon.service;

import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;
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

    public LikeNotificationService(RecentService recentService, MessageFactory messageFactory,
            LikeEffectService effectService, ReactionNotificationCoordinator reactionNotificationCoordinator) {
        this.recentService = recentService;
        this.messageFactory = messageFactory;
        this.effectService = effectService;
        this.reactionNotificationCoordinator = reactionNotificationCoordinator;
    }

    public void notifyDirectLike(UUID senderUuid, String senderName, UUID targetUuid, String targetName,
            FeedItem item) {
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
            target.sendMessage(messageFactory.notification(
                    "likebeacon.direct.received",
                    senderDisplay,
                    Component.text(item.bodyText()).color(NamedTextColor.WHITE)));
            effectService.showReceivedLikeEffect(target);
        }
    }

    public void notifyChatPromotion(UUID reactorUuid, String reactorName, FeedItem item, String authorName) {
        addNewItemForOnlinePlayers(item);
        notifyReaction(reactorUuid, reactorName, item, authorName, null, 1L, false);
    }

    public void notifyReaction(UUID reactorUuid, String reactorName, FeedItem item,
            String targetName, String initiatorName, long reactionCount, boolean aggregateRecipient) {
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
            reactionNotificationCoordinator.enqueue(reactorName, item, initiatorName, reactionCount);
        } else {
            reactionNotificationCoordinator.notifyImmediately(
                    reactorName, item, initiatorName, reactionCount);
        }
    }

    private void addNewItemForOnlinePlayers(FeedItem item) {
        recentService.add(item);
        Bukkit.getOnlinePlayers().forEach(
                player -> recentService.updateLastSeen(player.getUniqueId(), item.itemId()));
    }
}
