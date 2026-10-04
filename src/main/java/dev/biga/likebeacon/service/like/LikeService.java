package dev.biga.likebeacon.service.like;

import org.bukkit.entity.Player;

import dev.biga.likebeacon.service.reaction.ReactionService;

/** Public facade used by command handlers for Like-related use cases. */
public final class LikeService {

    private final DirectLikeService directLikeService;
    private final ReactionService reactionService;

    public LikeService(DirectLikeService directLikeService, ReactionService reactionService) {
        this.directLikeService = directLikeService;
        this.reactionService = reactionService;
    }

    public void sendLike(Player sender, Player target, String reason) {
        directLikeService.sendLike(sender, target, reason);
    }

    public void react(Player sender, String displayCode) {
        reactionService.react(sender, displayCode);
    }

    public void react(Player sender) {
        reactionService.react(sender);
    }
}
