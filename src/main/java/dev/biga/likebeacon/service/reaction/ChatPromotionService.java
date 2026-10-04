package dev.biga.likebeacon.service.reaction;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.biga.likebeacon.database.DatabaseManager;
import dev.biga.likebeacon.database.DatabaseWriteExecutor;
import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.PlayerStatsRepository;
import dev.biga.likebeacon.database.PlayerStatType;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.PendingChat;
import dev.biga.likebeacon.model.Reaction;
import dev.biga.likebeacon.service.chat.PendingChatService;
import dev.biga.likebeacon.service.notification.LikeNotificationService;
import dev.biga.likebeacon.service.support.AsyncFailures;
import dev.biga.likebeacon.util.MessageFactory;

/** Claims pending chat messages and atomically promotes their first reaction. */
public final class ChatPromotionService {

    private static final Logger log = Logger.getLogger(ChatPromotionService.class.getName());

    private final FeedItemRepository itemRepository;
    private final ReactionRepository reactionRepository;
    private final ItemStatsRepository itemStatsRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final DatabaseManager databaseManager;
    private final DatabaseWriteExecutor writeExecutor;
    private final PendingChatService pendingChatService;
    private final LikeNotificationService notificationService;
    private final MessageFactory messageFactory;
    private final Plugin plugin;
    private final String serverId;

    public ChatPromotionService(FeedItemRepository itemRepository, ReactionRepository reactionRepository,
            ItemStatsRepository itemStatsRepository, PlayerStatsRepository playerStatsRepository,
            DatabaseManager databaseManager, DatabaseWriteExecutor writeExecutor,
            PendingChatService pendingChatService, LikeNotificationService notificationService,
            MessageFactory messageFactory, Plugin plugin, String serverId) {
        this.itemRepository = itemRepository;
        this.reactionRepository = reactionRepository;
        this.itemStatsRepository = itemStatsRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.databaseManager = databaseManager;
        this.writeExecutor = writeExecutor;
        this.pendingChatService = pendingChatService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.plugin = plugin;
        this.serverId = serverId;
    }

    public void reactToPending(Player reactor, String displayCode,
            BiConsumer<Player, FeedItem> reactToPromotedItem) {
        var claim = pendingChatService.claim(displayCode);
        if (claim.isEmpty()) {
            reactor.sendMessage(messageFactory.error("likebeacon.error.chat-expired"));
            return;
        }

        switch (claim.get()) {
            case PendingChatService.Joined joined -> joinPromotion(
                    reactor.getUniqueId(), joined.completion(), reactToPromotedItem);
            case PendingChatService.Owner owner -> promote(reactor, owner.pending());
        }
    }

    private void joinPromotion(UUID reactorUuid, CompletableFuture<FeedItem> completion,
            BiConsumer<Player, FeedItem> reactToPromotedItem) {
        completion.whenComplete((item, failure) -> runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(reactorUuid);
            if (online == null)
                return;
            if (failure != null)
                online.sendMessage(messageFactory.error("likebeacon.error.internal"));
            else
                reactToPromotedItem.accept(online, item);
        }));
    }

    private void promote(Player reactor, PendingChat pending) {
        if (reactor.getUniqueId().equals(pending.authorUuid())) {
            pendingChatService.failPromotion(pending.displayCode(),
                    new IllegalStateException("Chat author cannot react to own message"));
            pendingChatService.put(pending);
            reactor.sendMessage(messageFactory.error("likebeacon.error.self"));
            return;
        }

        UUID reactorUuid = reactor.getUniqueId();
        String reactorName = reactor.getName();
        String itemId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        FeedItem item = FeedItem.chat(itemId, serverId, pending.displayCode(), pending.createdAt(),
                pending.authorUuid(), pending.bodyText(), pending.world(), pending.x(), pending.y(), pending.z());
        Reaction reaction = new Reaction(UUID.randomUUID().toString(), serverId, now, itemId,
                reactorUuid, pending.authorUuid(), "LIKE");

        writeExecutor.submit(() -> {
            databaseManager.executeInTransaction(connection -> {
                itemRepository.save(connection, item);
                reactionRepository.save(connection, reaction);
                itemStatsRepository.insertNew(connection, serverId, itemId, now);
                playerStatsRepository.incrementCount(
                        connection, PlayerStatType.RECEIVED, serverId,
                        pending.authorUuid(), pending.authorName(), now);
                playerStatsRepository.incrementCount(
                        connection, PlayerStatType.REACTED, serverId, reactorUuid, reactorName, now);
            });
            return null;
        }).whenComplete((ignored, failure) -> runOnMainThread(() -> {
            if (failure != null) {
                Throwable cause = AsyncFailures.unwrap(failure);
                pendingChatService.failPromotion(pending.displayCode(), cause);
                pendingChatService.put(pending);
                log.log(Level.SEVERE, "Failed to promote pending chat #" + pending.displayCode(), cause);
                Player online = Bukkit.getPlayer(reactorUuid);
                if (online != null)
                    online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                return;
            }

            pendingChatService.completePromotion(pending.displayCode(), item);
            notificationService.notifyChatPromotion(reactorUuid, reactorName, item, pending.authorName());
        }));
    }

    private void runOnMainThread(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
