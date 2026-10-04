package dev.biga.likebeacon.service;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.biga.likebeacon.database.DatabaseManager;
import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.database.DatabaseWriteExecutor;
import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.PlayerStatsRepository;
import dev.biga.likebeacon.database.PlayerStatType;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.Reaction;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;

/** Resolves and persists reactions to existing feed items. */
public final class ReactionService {

    private static final Logger log = Logger.getLogger(ReactionService.class.getName());

    private final FeedItemRepository itemRepository;
    private final ReactionRepository reactionRepository;
    private final ItemStatsRepository itemStatsRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final DatabaseManager databaseManager;
    private final DatabaseReadExecutor readExecutor;
    private final DatabaseWriteExecutor writeExecutor;
    private final RecentService recentService;
    private final ChatPromotionService chatPromotionService;
    private final LikeNotificationService notificationService;
    private final PlayerNameResolver playerNameResolver;
    private final MessageFactory messageFactory;
    private final Plugin plugin;
    private final String serverId;

    public ReactionService(FeedItemRepository itemRepository, ReactionRepository reactionRepository,
            ItemStatsRepository itemStatsRepository, PlayerStatsRepository playerStatsRepository,
            DatabaseManager databaseManager, DatabaseReadExecutor readExecutor,
            DatabaseWriteExecutor writeExecutor, RecentService recentService,
            ChatPromotionService chatPromotionService, LikeNotificationService notificationService,
            PlayerNameResolver playerNameResolver, MessageFactory messageFactory,
            Plugin plugin, String serverId) {
        this.itemRepository = itemRepository;
        this.reactionRepository = reactionRepository;
        this.itemStatsRepository = itemStatsRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.databaseManager = databaseManager;
        this.readExecutor = readExecutor;
        this.writeExecutor = writeExecutor;
        this.recentService = recentService;
        this.chatPromotionService = chatPromotionService;
        this.notificationService = notificationService;
        this.playerNameResolver = playerNameResolver;
        this.messageFactory = messageFactory;
        this.plugin = plugin;
        this.serverId = serverId;
    }

    public void react(Player sender, String displayCode) {
        UUID senderUuid = sender.getUniqueId();
        readExecutor.submit(connection -> itemRepository.findLatestByDisplayCode(serverId, displayCode))
                .whenComplete((item, failure) -> runOnMainThread(() -> {
                    Player online = Bukkit.getPlayer(senderUuid);
                    if (online == null)
                        return;
                    if (failure != null) {
                        log.log(Level.SEVERE, "Failed to find item by displayCode: " + displayCode,
                                AsyncFailures.unwrap(failure));
                        online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                    } else if (item.isEmpty()) {
                        chatPromotionService.reactToPending(online, displayCode, this::reactToItem);
                    } else {
                        reactToItem(online, item.get());
                    }
                }));
    }

    public void react(Player sender) {
        var itemId = recentService.getLastSeenItemId(sender.getUniqueId());
        if (itemId.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.no-recent"));
            return;
        }
        var item = recentService.findById(itemId.get());
        if (item.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.no-recent"));
            return;
        }
        reactToItem(sender, item.get());
    }

    private void reactToItem(Player sender, FeedItem item) {
        if (sender.getUniqueId().equals(item.authorUuid())) {
            sender.sendMessage(messageFactory.error("likebeacon.error.self"));
            return;
        }

        Component displayCode = messageFactory.displayCodeLabel(item.displayCode());
        UUID senderUuid = sender.getUniqueId();
        String senderName = sender.getName();
        String targetName = playerNameResolver.resolve(item.authorUuid());
        String initiatorName = item.initiatorUuid() == null
                ? null
                : playerNameResolver.resolve(item.initiatorUuid());
        long now = System.currentTimeMillis();
        Reaction reaction = new Reaction(UUID.randomUUID().toString(), serverId, now,
                item.itemId(), senderUuid, item.authorUuid(), "LIKE");

        writeExecutor.submit(() -> databaseManager.executeInTransactionWithResult(connection -> {
            reactionRepository.save(connection, reaction);
            long reactionCount = itemStatsRepository.incrementReactionCount(
                    connection, serverId, item.itemId(), now);
            playerStatsRepository.incrementCount(
                    connection, PlayerStatType.REACTED, serverId, senderUuid, senderName, now);
            playerStatsRepository.incrementCount(
                    connection, PlayerStatType.RECEIVED, serverId, item.authorUuid(), targetName, now);
            return reactionCount;
        })).whenComplete((reactionCount, failure) -> runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(senderUuid);
            if (failure != null) {
                Throwable cause = AsyncFailures.unwrap(failure);
                if (AsyncFailures.isConstraintViolation(cause)) {
                    if (online != null) {
                        online.sendMessage(messageFactory.error(
                                "likebeacon.error.already-reacted", displayCode));
                    }
                } else {
                    log.log(Level.SEVERE, "Failed to persist reaction on itemId: " + item.itemId(), cause);
                    if (online != null)
                        online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                }
                return;
            }

            notificationService.notifyReaction(
                    senderUuid, senderName, item, targetName, initiatorName, reactionCount, true);
        }));
    }

    private void runOnMainThread(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
