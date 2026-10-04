package dev.biga.likebeacon.service;

import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.DailyLimitRepository;
import dev.biga.likebeacon.database.DatabaseManager;
import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.database.DatabaseWriteExecutor;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.database.PlayerStatsRepository;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.model.Reaction;
import dev.biga.likebeacon.model.PendingChat;
import dev.biga.likebeacon.util.DisplayCodeGenerator;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import net.kyori.adventure.audience.Audience;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Service responsible for the business logic of sending likes and reactions.
 * <p>
 * All DB writes are routed through {@link DatabaseWriteExecutor} to guarantee
 * serialized SQLite access. Bukkit/Paper API calls are confined to the server
 * main thread; no Bukkit objects are touched from the write executor thread.
 * </p>
 */
public class LikeService {

    private static final Logger log = Logger.getLogger(LikeService.class.getName());

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
    private final RecentService recentService;
    private final PendingChatService pendingChatService;
    private final MessageFactory messageFactory;
    private final LikeEffectService effectService;
    private final LikeNotificationAggregationService notificationAggregationService;
    private final PlayerNameResolver playerNameResolver;
    private final FileConfiguration config;
    private final Plugin plugin;
    private final String serverId;

    /**
     * Constructs a LikeService with all required dependencies.
     */
    public LikeService(
            FeedItemRepository itemRepository,
            ReactionRepository reactionRepository,
            DailyLimitRepository dailyLimitRepository,
            PlayerStatsRepository playerStatsRepository,
            ItemStatsRepository itemStatsRepository,
            DatabaseManager databaseManager,
            DatabaseReadExecutor readExecutor,
            DatabaseWriteExecutor writeExecutor,
            DisplayCodeGenerator displayCodeGenerator,
            CooldownService cooldownService,
            RecentService recentService,
            PendingChatService pendingChatService,
            MessageFactory messageFactory,
            LikeEffectService effectService,
            LikeNotificationAggregationService notificationAggregationService,
            PlayerNameResolver playerNameResolver,
            FileConfiguration config,
            Plugin plugin,
            String serverId) {
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
        this.recentService = recentService;
        this.pendingChatService = pendingChatService;
        this.messageFactory = messageFactory;
        this.effectService = effectService;
        this.notificationAggregationService = notificationAggregationService;
        this.playerNameResolver = playerNameResolver;
        this.config = config;
        this.plugin = plugin;
        this.serverId = serverId;
    }

    // ── Main operations ──────────────────────────────────────────────────────

    /**
     * Sends a like from the sender to the target player.
     * <p>
     * Validates inputs on the main thread, then checks and increments the daily
     * limit in the same atomic write transaction. On
     * completion the success/failure callback runs back on the server main thread.
     * </p>
     *
     * @param sender the player sending the like
     * @param target the target player
     * @param reason the reason text for the like
     */
    public void sendLike(Player sender, Player target, String reason) {
        // ── 1. Validate inputs ────────────────────────────────────────────────
        if (reason == null || reason.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.reason.empty"));
            return;
        }
        int maxLength = config.getInt("reason.maxLength", 48);
        if (reason.length() > maxLength) {
            sender.sendMessage(messageFactory.error("likebeacon.error.reason.too-long", Component.text(maxLength)));
            return;
        }
        if (reason.contains("\n") || reason.contains("\r")) {
            sender.sendMessage(messageFactory.error("likebeacon.error.reason.newline"));
            return;
        }

        // ── 2. Disallow self-like ─────────────────────────────────────────────
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(messageFactory.error("likebeacon.error.self"));
            return;
        }

        // ── 3. Check cooldown ─────────────────────────────────────────────────
        if (cooldownService.isOnCooldown(sender.getUniqueId(), target.getUniqueId())) {
            long remaining = cooldownService.getRemainingSeconds(sender.getUniqueId(), target.getUniqueId());
            sender.sendMessage(messageFactory.error("likebeacon.error.cooldown", Component.text(remaining)));
            return;
        }

        // ── 4. Capture Bukkit values before leaving the main thread ──────────
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        int dailyLimit = config.getInt("limits.dailyDirectLikeLimit", 20);
        UUID senderUuid = sender.getUniqueId();
        String senderName = sender.getName();
        UUID authorUuid = target.getUniqueId();
        String targetName = target.getName();
        Location senderLocation = sender.getLocation();
        String world = senderLocation.getWorld().getName();
        int x = senderLocation.getBlockX();
        int y = senderLocation.getBlockY();
        int z = senderLocation.getBlockZ();

        // ── 5. Allocate a collision-free code on the DB reader ───────────────
        readExecutor.submit(conn -> pendingChatService.reserveDisplayCode(
                conn, displayCodeGenerator, serverId))
                .whenComplete((displayCode, allocationFailure) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (allocationFailure != null) {
                                log.log(Level.SEVERE, "Failed to generate unique displayCode", unwrap(allocationFailure));
                                Player online = Bukkit.getPlayer(senderUuid);
                                if (online != null)
                                    online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                                return;
                            }
                            persistDirectLike(senderUuid, senderName, authorUuid, targetName, reason,
                                    world, x, y, z, today, dailyLimit, displayCode);
                        }));
    }

    private void persistDirectLike(UUID senderUuid, String senderName, UUID authorUuid, String targetName,
            String reason, String world, int x, int y, int z, String today, int dailyLimit,
            String displayCode) {
        String itemId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        FeedItem item = FeedItem.direct(itemId, serverId, displayCode, now,
                authorUuid, senderUuid, reason, world, x, y, z);
        Reaction initialReaction = new Reaction(UUID.randomUUID().toString(), serverId, now,
                itemId, senderUuid, authorUuid, "LIKE");

        writeExecutor.submit(() -> databaseManager.executeInTransactionWithResult(conn -> {
            if (!dailyLimitRepository.incrementIfBelowLimit(
                    conn, serverId, today, senderUuid, dailyLimit))
                return false;
            itemRepository.save(conn, item);
            reactionRepository.save(conn, initialReaction);
            itemStatsRepository.insertNew(conn, serverId, itemId, now);
            playerStatsRepository.upsertSentCount(conn, serverId, senderUuid, senderName, now);
            playerStatsRepository.upsertReceivedCount(conn, serverId, authorUuid, targetName, now);
            playerStatsRepository.upsertReactedCount(conn, serverId, senderUuid, senderName, now);
            return true;
        })).whenComplete((created, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingChatService.releaseDisplayCode(displayCode);
            if (ex != null) {
                log.log(Level.SEVERE, "Failed to persist like from " + senderUuid, unwrap(ex));
                Player senderOnline = Bukkit.getPlayer(senderUuid);
                if (senderOnline != null) {
                    senderOnline.sendMessage(messageFactory.error("likebeacon.error.internal"));
                }
                return;
            }
            if (!created) {
                Player senderOnline = Bukkit.getPlayer(senderUuid);
                if (senderOnline != null) {
                    senderOnline.sendMessage(messageFactory.error(
                            "likebeacon.error.daily-limit", Component.text(dailyLimit)));
                }
                return;
            }

            // Set in-memory state now that the DB write succeeded
            cooldownService.setCooldown(senderUuid, authorUuid);
            recentService.add(item);
            Bukkit.getOnlinePlayers().forEach(
                    p -> recentService.updateLastSeen(p.getUniqueId(), itemId));

            // Send success notification to sender (re-resolve in case they logged back in)
            Player senderOnline = Bukkit.getPlayer(senderUuid);
            if (senderOnline != null) {
                senderOnline.sendMessage(messageFactory.success(
                        "likebeacon.direct.sent",
                        Component.text(targetName).color(NamedTextColor.WHITE),
                        Component.text(reason).color(NamedTextColor.WHITE)));
            }

            // Item to all players except sender and target, plus console
            Component senderDisplay = Component.text(senderName).color(NamedTextColor.WHITE);
            Component targetDisplay = Component.text(targetName).color(NamedTextColor.WHITE);
            Audience others = Audience.audience(
                    Stream.concat(
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> !p.getUniqueId().equals(authorUuid)
                                            && !p.getUniqueId().equals(senderUuid)),
                            Stream.of(Bukkit.getConsoleSender()))
                            .collect(java.util.stream.Collectors.toList()));
            others.sendMessage(messageFactory.buildItemMessage(
                    item, senderDisplay, targetDisplay, -1, false, true, true));

            // Send a dedicated received-Like notification to the target
            Player targetOnline = Bukkit.getPlayer(authorUuid);
            if (targetOnline != null) {
                targetOnline.sendMessage(messageFactory.notification(
                        "likebeacon.direct.received",
                        senderDisplay,
                        Component.text(reason).color(NamedTextColor.WHITE)));
                effectService.showReceivedLikeEffect(targetOnline);
            }
        }));
    }

    /**
     * Sends a reaction to the item identified by the given displayCode.
     *
     * @param sender      the player sending the reaction
     * @param displayCode the 4-character display code (without {@code #} prefix)
     */
    public void react(Player sender, String displayCode) {
        UUID senderUuid = sender.getUniqueId();
        readExecutor.submit(conn -> itemRepository.findLatestByDisplayCode(serverId, displayCode))
                .whenComplete((optItem, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(senderUuid);
                    if (online == null)
                        return;
                    if (ex != null) {
                        log.log(Level.SEVERE, "Failed to find item by displayCode: " + displayCode, unwrap(ex));
                        online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                    } else if (optItem.isEmpty()) {
                        reactToPending(online, displayCode);
                    } else {
                        reactToItem(online, optItem.get());
                    }
                }));
    }

    private void reactToPending(Player sender, String displayCode) {
        var claim = pendingChatService.claim(displayCode);
        if (claim.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.chat-expired"));
            return;
        }

        PendingChatService.Claim pendingClaim = claim.get();
        if (!pendingClaim.owner()) {
            UUID senderUuid = sender.getUniqueId();
            pendingClaim.completion()
                    .whenComplete((item, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Player online = Bukkit.getPlayer(senderUuid);
                        if (online == null)
                            return;
                        if (ex != null)
                            online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                        else
                            reactToItem(online, item);
                    }));
            return;
        }

        reactToPendingChat(sender, pendingClaim.pending());
    }

    /** Promotes a claimed chat and creates its first reaction atomically. */
    private void reactToPendingChat(Player reactor, PendingChat pending) {
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
            databaseManager.executeInTransaction(conn -> {
                itemRepository.save(conn, item);
                reactionRepository.save(conn, reaction);
                itemStatsRepository.insertNew(conn, serverId, itemId, now);
                playerStatsRepository.upsertReceivedCount(
                        conn, serverId, pending.authorUuid(), pending.authorName(), now);
                playerStatsRepository.upsertReactedCount(conn, serverId, reactorUuid, reactorName, now);
            });
            return null;
        }).whenComplete((ignored, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(reactorUuid);
            if (ex != null) {
                pendingChatService.failPromotion(pending.displayCode(), unwrap(ex));
                pendingChatService.put(pending);
                log.log(Level.SEVERE, "Failed to promote pending chat #" + pending.displayCode(), unwrap(ex));
                if (online != null)
                    online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                return;
            }

            recentService.add(item);
            Bukkit.getOnlinePlayers().forEach(p -> recentService.updateLastSeen(p.getUniqueId(), itemId));
            pendingChatService.completePromotion(pending.displayCode(), item);
            notifyReactionSuccess(
                    reactorUuid, reactorName, item, pending.authorName(), null, 1, false);
        }));
    }

    /**
     * Sends a reaction to the item the player last saw.
     *
     * @param sender the player sending the reaction
     */
    public void react(Player sender) {
        var optItemId = recentService.getLastSeenItemId(sender.getUniqueId());
        if (optItemId.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.no-recent"));
            return;
        }

        String itemId = optItemId.get();
        var optItem = recentService.findById(itemId);
        if (optItem.isEmpty()) {
            sender.sendMessage(messageFactory.error("likebeacon.error.no-recent"));
            return;
        }

        reactToItem(sender, optItem.get());
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Core reaction logic for a resolved item.
     * <p>
     * Duplicate and self-react checks happen on the main thread. The write
     * transaction is submitted to {@link DatabaseWriteExecutor}; success/failure
     * messages are sent back on the main thread.
     * </p>
     */
    private void reactToItem(Player sender, FeedItem item) {
        // 1. Disallow self-react
        if (sender.getUniqueId().equals(item.authorUuid())) {
            sender.sendMessage(messageFactory.error("likebeacon.error.self"));
            return;
        }

        String displayCode = item.displayCode();
        Component displayCodeComponent = messageFactory.displayCodeLabel(displayCode);

        // 2. Capture values before leaving the main thread. Duplicate reactions
        // are rejected atomically by the database UNIQUE constraint.
        UUID senderUuid = sender.getUniqueId();
        String senderName = sender.getName();
        // Resolve target name on main thread (may call Bukkit API)
        String targetName = playerNameResolver.resolve(item.authorUuid());
        String initiatorName = item.initiatorUuid() == null
                ? null
                : playerNameResolver.resolve(item.initiatorUuid());

        String reactionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Reaction reaction = new Reaction(
                reactionId, serverId, now, item.itemId(), senderUuid, item.authorUuid(), "LIKE");

        // 3. Submit atomic write transaction
        writeExecutor.submit(() -> databaseManager.executeInTransactionWithResult(conn -> {
            reactionRepository.save(conn, reaction);
            long reactionCount = itemStatsRepository.incrementReactionCount(
                    conn, serverId, item.itemId(), now);
            playerStatsRepository.upsertReactedCount(conn, serverId, senderUuid, senderName, now);
            playerStatsRepository.upsertReceivedCount(
                    conn, serverId, item.authorUuid(), targetName, now);
            return reactionCount;
        })).whenComplete((reactionCount, ex) ->
        // 4. Callback on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player senderOnline = Bukkit.getPlayer(senderUuid);
            if (ex != null) {
                Throwable cause = unwrap(ex);
                if (isConstraintViolation(cause)) {
                    // UNIQUE constraint: concurrent duplicate reaction
                    if (senderOnline != null) {
                        senderOnline.sendMessage(messageFactory.error(
                                "likebeacon.error.already-reacted", displayCodeComponent));
                    }
                } else {
                    log.log(Level.SEVERE,
                            "Failed to persist reaction on itemId: "
                                    + item.itemId(),
                            cause);
                    if (senderOnline != null) {
                        senderOnline.sendMessage(messageFactory.error("likebeacon.error.internal"));
                    }
                }
                return;
            }

            notifyReactionSuccess(
                    senderUuid, senderName, item, targetName, initiatorName, reactionCount, true);
        }));
    }

    /**
     * Sends contextual success notifications after a reaction has been committed.
     */
    private void notifyReactionSuccess(UUID reactorUuid, String reactorName, FeedItem item,
            String targetName, String initiatorName, long reactionCount, boolean aggregateRecipient) {
        Player reactorOnline = Bukkit.getPlayer(reactorUuid);

        Component targetDisplay = Component.text(targetName).color(NamedTextColor.WHITE);
        Component bodyDisplay = Component.text(item.bodyText()).color(NamedTextColor.WHITE);

        if (item.type() == FeedItemType.CHAT) {
            if (reactorOnline != null) {
                reactorOnline.sendMessage(messageFactory.success(
                        "likebeacon.reaction.chat.sent",
                        targetDisplay,
                        bodyDisplay,
                        messageFactory.displayCodeLabel(item.displayCode())));
            }
        } else {
            if (reactorOnline != null) {
                reactorOnline.sendMessage(messageFactory.success(
                        "likebeacon.reaction.direct.sent",
                        targetDisplay,
                        bodyDisplay,
                        messageFactory.displayCodeLabel(item.displayCode())));
            }
        }

        recentService.updateLastSeen(reactorUuid, item.itemId());
        if (aggregateRecipient) {
            notificationAggregationService.enqueue(
                    reactorName, item, initiatorName, reactionCount);
        } else {
            notificationAggregationService.notifyImmediately(
                    reactorName, item, initiatorName, reactionCount);
        }
    }

    /** Unwraps a {@link CompletionException} to its root cause. */
    private static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static boolean isConstraintViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlException.getErrorCode() == 19
                        || (sqlState != null && sqlState.startsWith("23"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
