package dev.biga.likebeacon.service.notification;

import dev.biga.likebeacon.service.milestone.MilestoneDetector;

/**
 * Milestones reached by one or more reactions; {@link MilestoneDetector#NONE}
 * means no milestone of that kind was reached.
 */
record ReactionMilestones(long item, long player) {

    static final ReactionMilestones NONE = new ReactionMilestones(MilestoneDetector.NONE, MilestoneDetector.NONE);

    /** Keeps the highest milestone of each kind. */
    ReactionMilestones merge(ReactionMilestones other) {
        return new ReactionMilestones(Math.max(item, other.item), Math.max(player, other.player));
    }
}
