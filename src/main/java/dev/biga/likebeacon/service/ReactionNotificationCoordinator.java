package dev.biga.likebeacon.service;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.util.MessageFactory;

/** Connects Bukkit scheduling to reaction batching and notification delivery. */
public final class ReactionNotificationCoordinator {

    private static final long CHECK_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final boolean aggregationEnabled;
    private final ReactionNotificationRouter router;
    private BukkitTask task;

    public ReactionNotificationCoordinator(Plugin plugin, boolean aggregationEnabled,
            long quietMillis, long maxWaitMillis,
            MessageFactory messageFactory, LikeEffectService effectService) {
        this(plugin, aggregationEnabled,
                new ReactionNotificationRouter(
                        aggregationEnabled,
                        new ReactionNotificationBatcher(
                                quietMillis, maxWaitMillis, System::currentTimeMillis),
                        new ReactionNotificationSender(messageFactory, effectService)));
    }

    ReactionNotificationCoordinator(Plugin plugin, boolean aggregationEnabled,
            ReactionNotificationRouter router) {
        this.plugin = plugin;
        this.aggregationEnabled = aggregationEnabled;
        this.router = router;
    }

    public void start() {
        if (aggregationEnabled && task == null) {
            task = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::flushDue, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        router.clear();
    }

    public void notifyImmediately(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        router.notifyImmediately(reactorName, item, initiatorName, reactionCount);
    }

    public void enqueue(String reactorName, FeedItem item,
            String initiatorName, long reactionCount) {
        router.enqueue(reactorName, item, initiatorName, reactionCount);
    }

    private void flushDue() {
        router.flushDue();
    }
}
