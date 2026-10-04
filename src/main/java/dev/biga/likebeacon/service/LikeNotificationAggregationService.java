package dev.biga.likebeacon.service;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Aggregates recipient notifications for reactions to existing feed items.
 * All methods are called on the server main thread.
 */
public final class LikeNotificationAggregationService {

    private static final long CHECK_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final MessageFactory messageFactory;
    private final LikeEffectService effectService;
    private final boolean enabled;
    private final long quietMillis;
    private final long maxWaitMillis;
    private final Map<String, ReactionNotificationBatch> batches = new HashMap<>();

    private BukkitTask task;

    public LikeNotificationAggregationService(Plugin plugin, FileConfiguration config,
            MessageFactory messageFactory, LikeEffectService effectService) {
        this.plugin = plugin;
        this.messageFactory = messageFactory;
        this.effectService = effectService;
        this.enabled = config.getBoolean("notifications.reactionAggregation.enabled", true);

        long configuredQuietSeconds = Math.max(1L,
                config.getLong("notifications.reactionAggregation.quietSeconds", 3L));
        long configuredMaxWaitSeconds = Math.max(configuredQuietSeconds,
                config.getLong("notifications.reactionAggregation.maxWaitSeconds", 10L));
        this.quietMillis = configuredQuietSeconds * 1_000L;
        this.maxWaitMillis = configuredMaxWaitSeconds * 1_000L;
    }

    /** Starts the periodic deadline check when aggregation is enabled. */
    public void start() {
        if (enabled && task == null) {
            task = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::flushDue, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
        }
    }

    /** Cancels the deadline check and discards pending notifications. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        batches.clear();
    }

    /**
     * Sends an immediate recipient notification. Used for the first Like that
     * promotes a pending chat item.
     */
    public void notifyImmediately(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        send(new ReactionNotificationBatch(
                reactorName, item, initiatorName, reactionCount, System.currentTimeMillis()));
    }

    /** Adds a successful reaction to the batch for its feed item. */
    public void enqueue(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        if (!enabled) {
            notifyImmediately(reactorName, item, initiatorName, reactionCount);
            return;
        }

        long now = System.currentTimeMillis();
        ReactionNotificationBatch batch = batches.get(item.itemId());
        if (batch != null && batch.isDue(now, quietMillis, maxWaitMillis)) {
            batches.remove(item.itemId());
            send(batch);
            batch = null;
        }

        if (batch == null) {
            batches.put(item.itemId(), new ReactionNotificationBatch(
                    reactorName, item, initiatorName, reactionCount, now));
        } else {
            batch.addReaction(reactionCount, now);
        }
    }

    private void flushDue() {
        long now = System.currentTimeMillis();
        List<ReactionNotificationBatch> due = new ArrayList<>();
        Iterator<ReactionNotificationBatch> iterator = batches.values().iterator();
        while (iterator.hasNext()) {
            ReactionNotificationBatch batch = iterator.next();
            if (batch.isDue(now, quietMillis, maxWaitMillis)) {
                iterator.remove();
                due.add(batch);
            }
        }
        due.forEach(this::send);
    }

    private void send(ReactionNotificationBatch batch) {
        Player target = Bukkit.getPlayer(batch.item().authorUuid());
        if (target == null) {
            return;
        }

        Component reactorDisplay = Component.text(batch.firstReactorName()).color(NamedTextColor.WHITE);
        Component bodyDisplay = Component.text(batch.item().bodyText()).color(NamedTextColor.WHITE);
        Component totalDisplay = number(batch.latestReactionCount());

        if (batch.newReactionCount() == 1) {
            sendSingle(target, batch, reactorDisplay, bodyDisplay, totalDisplay);
        } else {
            sendGroup(target, batch, reactorDisplay, bodyDisplay, totalDisplay);
        }
        effectService.showReceivedLikeEffect(target);
    }

    private void sendSingle(Player target, ReactionNotificationBatch batch,
            Component reactorDisplay, Component bodyDisplay, Component totalDisplay) {
        if (batch.item().type() == FeedItemType.CHAT) {
            target.sendMessage(messageFactory.notification(
                    "likebeacon.reaction.chat.received",
                    reactorDisplay,
                    bodyDisplay,
                    totalDisplay));
            return;
        }

        target.sendMessage(messageFactory.notification(
                "likebeacon.reaction.direct.received",
                reactorDisplay,
                initiatorDisplay(batch),
                bodyDisplay,
                totalDisplay));
    }

    private void sendGroup(Player target, ReactionNotificationBatch batch,
            Component reactorDisplay, Component bodyDisplay, Component totalDisplay) {
        Component othersDisplay = number(batch.newReactionCount() - 1L);
        Component newCountDisplay = number(batch.newReactionCount());
        if (batch.item().type() == FeedItemType.CHAT) {
            target.sendMessage(messageFactory.notification(
                    "likebeacon.reaction.chat.received-group",
                    reactorDisplay,
                    othersDisplay,
                    bodyDisplay,
                    newCountDisplay,
                    totalDisplay));
            return;
        }

        target.sendMessage(messageFactory.notification(
                "likebeacon.reaction.direct.received-group",
                reactorDisplay,
                othersDisplay,
                initiatorDisplay(batch),
                bodyDisplay,
                newCountDisplay,
                totalDisplay));
    }

    private static Component initiatorDisplay(ReactionNotificationBatch batch) {
        String displayName = batch.initiatorName() != null
                ? batch.initiatorName()
                : batch.item().authorUuid().toString();
        return Component.text(displayName).color(NamedTextColor.WHITE);
    }

    private static Component number(long value) {
        return Component.text(Long.toString(value)).color(NamedTextColor.WHITE);
    }

}
