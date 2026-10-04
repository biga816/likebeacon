package dev.biga.likebeacon;

import dev.biga.likebeacon.book.LikeBookService;
import dev.biga.likebeacon.command.LikeCommand;
import dev.biga.likebeacon.listener.ChatLikeListener;
import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.DailyLimitRepository;
import dev.biga.likebeacon.database.DatabaseManager;
import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.database.DatabaseWriteExecutor;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.database.PlayerStatsRepository;
import dev.biga.likebeacon.service.CooldownService;
import dev.biga.likebeacon.service.ChatLikeEligibilityService;
import dev.biga.likebeacon.service.ChatPromotionService;
import dev.biga.likebeacon.service.DirectLikeService;
import dev.biga.likebeacon.service.LikeEffectService;
import dev.biga.likebeacon.service.LikeNotificationService;
import dev.biga.likebeacon.service.LikeLogService;
import dev.biga.likebeacon.service.LikeService;
import dev.biga.likebeacon.service.RecentService;
import dev.biga.likebeacon.service.ReactionService;
import dev.biga.likebeacon.service.ReactionNotificationCoordinator;
import dev.biga.likebeacon.service.PendingChatService;
import dev.biga.likebeacon.service.PlayerNameResolver;
import dev.biga.likebeacon.util.I18nService;
import dev.biga.likebeacon.util.MessageFactory;
import dev.biga.likebeacon.util.DisplayCodeGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.concurrent.CompletionException;

/**
 * Entry point for the LikeBeacon plugin.
 * Initializes i18n, the database, wires dependencies, and registers commands in
 * onEnable(). Closes the database connection and unregisters translations in
 * onDisable().
 */
public class LikeBeaconPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private DatabaseWriteExecutor writeExecutor;
    private DatabaseReadExecutor readExecutor;
    private I18nService i18nService;
    private ReactionNotificationCoordinator reactionNotificationCoordinator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        // 1. Read server-id from config
        String serverId = getConfig().getString("server-id", "default");
        getLogger().info("Server ID: " + serverId);

        // 2. Initialize i18n translations
        i18nService = new I18nService();
        i18nService.initialize(getClass().getClassLoader());

        // 3. Create data folder and initialize database connection
        getDataFolder().mkdirs();
        try {
            databaseManager = new DatabaseManager(getDataFolder());
            databaseManager.initialize();
        } catch (ClassNotFoundException e) {
            getLogger().log(Level.SEVERE, "SQLite JDBC driver not found", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize the database", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Initialize the single-writer executor
        writeExecutor = new DatabaseWriteExecutor();
        readExecutor = new DatabaseReadExecutor(databaseManager);

        // 5. Initialize repositories
        FeedItemRepository itemRepo = new FeedItemRepository(databaseManager);
        ReactionRepository reactionRepo = new ReactionRepository(databaseManager);
        DailyLimitRepository dailyRepo = new DailyLimitRepository();
        PlayerStatsRepository playerStatsRepo = new PlayerStatsRepository(databaseManager);
        ItemStatsRepository itemStatsRepo = new ItemStatsRepository(databaseManager);

        // 6. Initialize services
        CooldownService cooldownService = new CooldownService(
                getConfig().getInt("limits.pairCooldownSeconds", 60));
        RecentService recentService = new RecentService(getConfig());
        try {
            readExecutor.submit(conn -> {
                recentService.loadFromDb(itemRepo, serverId);
                return null;
            }).join();
        } catch (CompletionException e) {
            getLogger().log(Level.SEVERE, "Failed to load recent items on startup", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DisplayCodeGenerator displayCodeGen = new DisplayCodeGenerator(itemRepo);
        MessageFactory messageFactory = new MessageFactory(getConfig());
        LikeEffectService effectService = new LikeEffectService(getConfig());
        PlayerNameResolver playerNameResolver = new PlayerNameResolver();
        boolean aggregationEnabled = getConfig().getBoolean(
                "notifications.reactionAggregation.enabled", true);
        long quietSeconds = Math.max(1L, getConfig().getLong(
                "notifications.reactionAggregation.quietSeconds", 3L));
        long maxWaitSeconds = Math.max(quietSeconds, getConfig().getLong(
                "notifications.reactionAggregation.maxWaitSeconds", 10L));
        reactionNotificationCoordinator = new ReactionNotificationCoordinator(
                this, aggregationEnabled, quietSeconds * 1_000L, maxWaitSeconds * 1_000L,
                messageFactory, effectService);
        reactionNotificationCoordinator.start();
        LikeNotificationService notificationService = new LikeNotificationService(
                recentService, messageFactory, effectService, reactionNotificationCoordinator);
        PendingChatService pendingChatService = new PendingChatService(
                getConfig().getInt("chat.pendingBufferSize", 30));

        DirectLikeService directLikeService = new DirectLikeService(
                itemRepo, reactionRepo, dailyRepo,
                playerStatsRepo, itemStatsRepo,
                databaseManager, readExecutor, writeExecutor,
                displayCodeGen, cooldownService, pendingChatService, notificationService,
                messageFactory, this, serverId,
                getConfig().getInt("reason.maxLength", 48),
                getConfig().getInt("limits.dailyDirectLikeLimit", 20));
        ChatPromotionService chatPromotionService = new ChatPromotionService(
                itemRepo, reactionRepo, itemStatsRepo, playerStatsRepo,
                databaseManager, writeExecutor, pendingChatService, notificationService,
                messageFactory, this, serverId);
        ReactionService reactionService = new ReactionService(
                itemRepo, reactionRepo, itemStatsRepo, playerStatsRepo,
                databaseManager, readExecutor, writeExecutor, recentService,
                chatPromotionService, notificationService, playerNameResolver,
                messageFactory, this, serverId);
        LikeService likeService = new LikeService(directLikeService, reactionService);

        // 7. Book UI service
        LikeBookService bookService = new LikeBookService(
                playerStatsRepo, itemStatsRepo, itemRepo, reactionRepo, readExecutor,
                playerNameResolver, messageFactory, this, serverId);
        LikeLogService logService = new LikeLogService(
                recentService, itemStatsRepo, reactionRepo, readExecutor,
                playerNameResolver, messageFactory, this);

        // 8. Register commands
        LikeCommand likeCommand = new LikeCommand(
                likeService, recentService, logService, messageFactory, bookService);
        getCommand("like").setExecutor(likeCommand);
        getCommand("like").setTabCompleter(likeCommand);

        if (getConfig().getBoolean("chat.enabled", false)) {
            ChatLikeEligibilityService eligibility = new ChatLikeEligibilityService(
                    true, getConfig().getInt("chat.minLength", 4));
            getServer().getPluginManager().registerEvents(new ChatLikeListener(
                    pendingChatService, displayCodeGen, readExecutor, messageFactory, eligibility, serverId,
                    getConfig().getInt("chat.maxStoredLength", 100)), this);
        }

        getLogger().info("LikeBeacon enabled!");
    }

    @Override
    public void onDisable() {
        if (reactionNotificationCoordinator != null) {
            reactionNotificationCoordinator.shutdown();
        }
        if (writeExecutor != null) {
            writeExecutor.shutdown();
        }
        if (readExecutor != null) {
            readExecutor.shutdown();
        }
        if (i18nService != null) {
            i18nService.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("LikeBeacon disabled!");
    }
}
