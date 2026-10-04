package dev.biga.likebeacon.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.OptionalLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.biga.likebeacon.database.DailyLimitRepository;
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
import dev.biga.likebeacon.util.DisplayCodeGenerator;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;

/** Executes the direct {@code /like <player> <reason>} use case. */
public final class DirectLikeService {

    private static final Logger log = Logger.getLogger(DirectLikeService.class.getName());

    private final FeedItemRepository itemRepository;
    private final ReactionRepository reactionRepository;
    private final DailyLimitRepository dailyLimitRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final ItemStatsRepository itemStatsRepository;
    private final DatabaseManager databaseManager;
    private final DatabaseReadExecutor readExecutor;
    private final DatabaseWriteExecutor writeExecutor;
    private final DisplayCodeGenerator displayCodeGenerator;
    private final CooldownService cooldownService;
    private final PendingChatService pendingChatService;
    private final LikeNotificationService notificationService;
    private final MessageFactory messageFactory;
    private final Plugin plugin;
    private final String serverId;
    private final int maxReasonLength;
    private final int dailyLimit;

    public DirectLikeService(FeedItemRepository itemRepository, ReactionRepository reactionRepository,
            DailyLimitRepository dailyLimitRepository, PlayerStatsRepository playerStatsRepository,
            ItemStatsRepository itemStatsRepository, DatabaseManager databaseManager,
            DatabaseReadExecutor readExecutor, DatabaseWriteExecutor writeExecutor,
            DisplayCodeGenerator displayCodeGenerator, CooldownService cooldownService,
            PendingChatService pendingChatService, LikeNotificationService notificationService,
            MessageFactory messageFactory, Plugin plugin, String serverId,
            int maxReasonLength, int dailyLimit) {
        this.itemRepository = itemRepository;
        this.reactionRepository = reactionRepository;
        this.dailyLimitRepository = dailyLimitRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.itemStatsRepository = itemStatsRepository;
        this.databaseManager = databaseManager;
        this.readExecutor = readExecutor;
        this.writeExecutor = writeExecutor;
        this.displayCodeGenerator = displayCodeGenerator;
        this.cooldownService = cooldownService;
        this.pendingChatService = pendingChatService;
        this.notificationService = notificationService;
        this.messageFactory = messageFactory;
        this.plugin = plugin;
        this.serverId = serverId;
        this.maxReasonLength = maxReasonLength;
        this.dailyLimit = dailyLimit;
    }

    public void sendLike(Player sender, Player target, String reason) {
        if (!validate(sender, target, reason))
            return;

        UUID senderUuid = sender.getUniqueId();
        String senderName = sender.getName();
        UUID targetUuid = target.getUniqueId();
        String targetName = target.getName();
        Location location = sender.getLocation();
        DirectLikeRequest request = new DirectLikeRequest(
                senderUuid, senderName, targetUuid, targetName, reason,
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                LocalDate.now(ZoneOffset.UTC).toString());

        readExecutor.submit(connection -> pendingChatService.reserveDisplayCode(
                connection, displayCodeGenerator, serverId))
                .whenComplete((displayCode, failure) -> runOnMainThread(() -> {
                    if (failure != null) {
                        log.log(Level.SEVERE, "Failed to generate unique displayCode", AsyncFailures.unwrap(failure));
                        sendInternalError(senderUuid);
                        return;
                    }
                    persist(request, displayCode);
                }));
    }

    private boolean validate(Player sender, Player target, String reason) {
        if (reason == null || reason.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.reason.empty"));
            return false;
        }
        if (reason.length() > maxReasonLength) {
            sender.sendMessage(messageFactory.error(
                    "likebeacon.error.reason.too-long", Component.text(maxReasonLength)));
            return false;
        }
        if (reason.contains("\n") || reason.contains("\r")) {
            sender.sendMessage(messageFactory.error("likebeacon.error.reason.newline"));
            return false;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(messageFactory.error("likebeacon.error.self"));
            return false;
        }
        OptionalLong remaining = cooldownService.remainingSeconds(
                sender.getUniqueId(), target.getUniqueId());
        if (remaining.isPresent()) {
            sender.sendMessage(messageFactory.error(
                    "likebeacon.error.cooldown", Component.text(remaining.getAsLong())));
            return false;
        }
        return true;
    }

    private void persist(DirectLikeRequest request, String displayCode) {
        String itemId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        FeedItem item = FeedItem.direct(itemId, serverId, displayCode, now,
                request.targetUuid(), request.senderUuid(), request.reason(),
                request.world(), request.x(), request.y(), request.z());
        Reaction initialReaction = new Reaction(UUID.randomUUID().toString(), serverId, now,
                itemId, request.senderUuid(), request.targetUuid(), "LIKE");

        writeExecutor.submit(() -> databaseManager.executeInTransactionWithResult(connection -> {
            if (!dailyLimitRepository.incrementIfBelowLimit(
                    connection, serverId, request.date(), request.senderUuid(), dailyLimit)) {
                return false;
            }
            itemRepository.save(connection, item);
            reactionRepository.save(connection, initialReaction);
            itemStatsRepository.insertNew(connection, serverId, itemId, now);
            playerStatsRepository.incrementCount(
                    connection, PlayerStatType.SENT, serverId,
                    request.senderUuid(), request.senderName(), now);
            playerStatsRepository.incrementCount(
                    connection, PlayerStatType.RECEIVED, serverId,
                    request.targetUuid(), request.targetName(), now);
            playerStatsRepository.incrementCount(
                    connection, PlayerStatType.REACTED, serverId,
                    request.senderUuid(), request.senderName(), now);
            return true;
        })).whenComplete((created, failure) -> runOnMainThread(() -> {
            pendingChatService.releaseDisplayCode(displayCode);
            if (failure != null) {
                log.log(Level.SEVERE, "Failed to persist like from " + request.senderUuid(),
                        AsyncFailures.unwrap(failure));
                sendInternalError(request.senderUuid());
                return;
            }
            if (!created) {
                Player sender = Bukkit.getPlayer(request.senderUuid());
                if (sender != null) {
                    sender.sendMessage(messageFactory.error(
                            "likebeacon.error.daily-limit", Component.text(dailyLimit)));
                }
                return;
            }

            cooldownService.setCooldown(request.senderUuid(), request.targetUuid());
            notificationService.notifyDirectLike(
                    request.senderUuid(), request.senderName(), request.targetUuid(), request.targetName(), item);
        }));
    }

    private void sendInternalError(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null)
            player.sendMessage(messageFactory.error("likebeacon.error.internal"));
    }

    private void runOnMainThread(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private record DirectLikeRequest(UUID senderUuid, String senderName, UUID targetUuid, String targetName,
            String reason, String world, int x, int y, int z, String date) {
    }
}
