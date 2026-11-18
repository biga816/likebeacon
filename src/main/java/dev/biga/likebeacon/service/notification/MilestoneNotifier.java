package dev.biga.likebeacon.service.notification;

import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.Bukkit;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.service.milestone.BroadcastRateLimiter;
import dev.biga.likebeacon.service.milestone.MilestoneDetector;
import dev.biga.likebeacon.service.support.PlayerNameResolver;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Notifies the author and the rest of the server when a Like milestone is reached.
 * All methods must be called on the server main thread.
 */
public final class MilestoneNotifier {

    private static final Logger log = Logger.getLogger(MilestoneNotifier.class.getName());

    private final MessageFactory messageFactory;
    private final PlayerNameResolver playerNameResolver;
    private final MilestoneDetector detector;
    private final BroadcastRateLimiter rateLimiter;

    public MilestoneNotifier(MessageFactory messageFactory, PlayerNameResolver playerNameResolver,
            MilestoneDetector detector, BroadcastRateLimiter rateLimiter) {
        this.messageFactory = messageFactory;
        this.playerNameResolver = playerNameResolver;
        this.detector = detector;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Sends a reactable announcement to everyone except the author, when allowed
     * by item age and the server-wide interval. The author is not notified here
     * because the received notification already shows the total count.
     */
    public void notifyItemMilestone(FeedItem item, String initiatorName, long milestone) {
        UUID authorUuid = item.authorUuid();
        if (!detector.isBroadcastable(item, System.currentTimeMillis())) {
            log.fine(() -> "Skipping milestone announcement for old item " + item.itemId());
            return;
        }
        if (!rateLimiter.tryAcquire()) {
            log.fine(() -> "Skipping milestone announcement within minimum interval for item "
                    + item.itemId());
            return;
        }

        Component authorDisplay = text(playerNameResolver.resolve(authorUuid));
        Component initiatorDisplay = text(nameOrResolve(initiatorName, item.initiatorUuid()));
        Audience others = Audience.audience(
                Stream.concat(
                        Bukkit.getOnlinePlayers().stream()
                                .filter(player -> !player.getUniqueId().equals(authorUuid)),
                        Stream.of(Bukkit.getConsoleSender()))
                        .toList());
        others.sendMessage(messageFactory.buildItemMilestoneMessage(
                item, initiatorDisplay, authorDisplay, milestone));
    }

    private String nameOrResolve(String name, UUID uuid) {
        return name != null ? name : playerNameResolver.resolve(uuid);
    }

    private static Component text(String value) {
        return Component.text(value).color(NamedTextColor.WHITE);
    }
}
