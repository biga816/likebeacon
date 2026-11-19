package dev.biga.likebeacon.service.notification;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.service.milestone.MilestoneDetector;
import dev.biga.likebeacon.util.MessageFactory;

/** Connects Bukkit scheduling to reaction batching and notification delivery. */
public final class ReactionNotificationCoordinator {

    private static final long CHECK_PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final boolean aggregationEnabled;
    private final ReactionNotificationRouter router;
    private final MilestoneDetector milestoneDetector;
    private BukkitTask task;

    public ReactionNotificationCoordinator(Plugin plugin, boolean aggregationEnabled,
            long quietMillis, long maxWaitMillis,
            MessageFactory messageFactory, LikeEffectService effectService,
            MilestoneDetector milestoneDetector, MilestoneNotifier milestoneNotifier) {
        this(plugin, aggregationEnabled,
                new ReactionNotificationRouter(
                        aggregationEnabled,
                        new ReactionNotificationBatcher(
                                quietMillis, maxWaitMillis, System::currentTimeMillis),
                        new ReactionNotificationSender(messageFactory, effectService, milestoneNotifier)),
                milestoneDetector);
    }

    ReactionNotificationCoordinator(Plugin plugin, boolean aggregationEnabled,
            ReactionNotificationRouter router, MilestoneDetector milestoneDetector) {
        this.plugin = plugin;
        this.aggregationEnabled = aggregationEnabled;
        this.router = router;
        this.milestoneDetector = milestoneDetector;
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

    /**
     * Sends a reaction notification now.
     *
     * @param reactionCount the item's reaction count after this reaction
     * @param receivedCount the author's received count after this reaction
     */
    public void notifyImmediately(String reactorName, FeedItem item,
            String initiatorName, long reactionCount, long receivedCount) {
        router.notifyImmediately(reactorName, item, initiatorName, reactionCount,
                milestones(item, reactionCount, receivedCount));
    }

    /**
     * Adds a reaction to its item's notification batch.
     *
     * @param reactionCount the item's reaction count after this reaction
     * @param receivedCount the author's received count after this reaction
     */
    public void enqueue(String reactorName, FeedItem item,
            String initiatorName, long reactionCount, long receivedCount) {
        router.enqueue(reactorName, item, initiatorName, reactionCount,
                milestones(item, reactionCount, receivedCount));
    }

    private ReactionMilestones milestones(FeedItem item, long reactionCount, long receivedCount) {
        return new ReactionMilestones(
                milestoneDetector.itemMilestone(item, reactionCount),
                milestoneDetector.playerMilestone(receivedCount));
    }

    private void flushDue() {
        router.flushDue();
    }
}
