package dev.biga.likebeacon.service.milestone;

import java.util.Set;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;

/** Decides whether a saved reaction count reaches a configured milestone. */
public final class MilestoneDetector {

    /** Returned when a count reaches no milestone. */
    public static final long NONE = 0L;

    private final MilestoneConfig config;
    private final Set<Long> itemThresholds;

    public MilestoneDetector(MilestoneConfig config) {
        this.config = config;
        this.itemThresholds = Set.copyOf(config.itemThresholds());
    }

    /**
     * Returns the item milestone reached by exactly this reaction count, or
     * {@link #NONE}. Reaction counts never decrease, so each milestone is
     * returned for at most one reaction per item.
     */
    public long itemMilestone(FeedItem item, long reactionCount) {
        if (!config.enabled() || !itemThresholds.contains(reactionCount))
            return NONE;
        if (item.type() == FeedItemType.DIRECT && reactionCount < config.directMinThreshold())
            return NONE;
        return reactionCount;
    }

    /** Whether an item is recent enough for its milestones to be announced server-wide. */
    public boolean isBroadcastable(FeedItem item, long now) {
        return config.maxItemAgeMillis() == 0L
                || now - item.createdAt() <= config.maxItemAgeMillis();
    }
}
