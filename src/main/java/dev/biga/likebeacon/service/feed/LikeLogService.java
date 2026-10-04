package dev.biga.likebeacon.service.feed;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.service.support.PlayerNameResolver;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Loads and renders the chat-based {@code /like log} view. */
public final class LikeLogService {

    private static final Logger log = Logger.getLogger(LikeLogService.class.getName());
    private static final int LOG_LIMIT = 5;

    private final RecentService recentService;
    private final ItemStatsRepository itemStatsRepository;
    private final ReactionRepository reactionRepository;
    private final DatabaseReadExecutor readExecutor;
    private final PlayerNameResolver playerNameResolver;
    private final MessageFactory messageFactory;
    private final Plugin plugin;

    public LikeLogService(RecentService recentService, ItemStatsRepository itemStatsRepository,
            ReactionRepository reactionRepository, DatabaseReadExecutor readExecutor,
            PlayerNameResolver playerNameResolver, MessageFactory messageFactory, Plugin plugin) {
        this.recentService = recentService;
        this.itemStatsRepository = itemStatsRepository;
        this.reactionRepository = reactionRepository;
        this.readExecutor = readExecutor;
        this.playerNameResolver = playerNameResolver;
        this.messageFactory = messageFactory;
        this.plugin = plugin;
    }

    public void show(Player player) {
        List<FeedItem> recent = recentService.getRecent(LOG_LIMIT);
        if (recent.isEmpty()) {
            player.sendMessage(messageFactory.info("likebeacon.command.log.empty"));
            return;
        }

        List<String> itemIds = recent.stream().map(FeedItem::itemId).toList();
        UUID playerUuid = player.getUniqueId();
        readExecutor.submit(connection -> new LogData(
                itemStatsRepository.reactionCountByItemIds(itemIds),
                reactionRepository.reactedItemIds(itemIds, playerUuid)))
                .whenComplete((data, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerUuid);
                    if (online == null)
                        return;
                    if (failure != null) {
                        log.log(Level.WARNING, "Failed to get reaction data for recent items", failure);
                        online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                        return;
                    }
                    render(online, recent, data);
                }));
    }

    private void render(Player player, List<FeedItem> recent, LogData data) {
        player.sendMessage(messageFactory.info("likebeacon.command.log.title"));
        for (FeedItem item : recent) {
            boolean ownSend = player.getUniqueId().equals(item.initiatorUuid());
            Component sender = item.initiatorUuid() == null ? Component.empty()
                    : ownSend
                            ? Component.translatable("likebeacon.item.you").color(NamedTextColor.GREEN)
                            : Component.text(playerNameResolver.resolve(item.initiatorUuid()))
                                    .color(NamedTextColor.WHITE);
            boolean ownLike = item.authorUuid().equals(player.getUniqueId());
            Component target = ownLike
                    ? Component.translatable("likebeacon.item.you").color(NamedTextColor.GREEN)
                    : Component.text(playerNameResolver.resolve(item.authorUuid())).color(NamedTextColor.WHITE);
            int count = data.counts().getOrDefault(item.itemId(), 0L).intValue();
            boolean reacted = data.reactedIds().contains(item.itemId());
            player.sendMessage(messageFactory.buildLogItemMessage(
                    item, sender, target, count, reacted, !ownLike));
        }
        recentService.updateLastSeen(player.getUniqueId(), recent.get(0).itemId());
    }

    private record LogData(Map<String, Long> counts, Set<String> reactedIds) {
    }
}
