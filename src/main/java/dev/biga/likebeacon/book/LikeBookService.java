package dev.biga.likebeacon.book;

import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.database.PlayerStatsRepository;
import dev.biga.likebeacon.model.ItemRankingEntry;
import dev.biga.likebeacon.model.PlayerStats;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.service.PlayerNameResolver;
import dev.biga.likebeacon.util.MessageFactory;
import dev.biga.likebeacon.util.PlayerTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Orchestrates async DB reads and book-UI opening for
 * {@code /like ranking} and {@code /like mine}.
 *
 * <p>
 * Pattern: obtain a {@link PlayerTranslator} for the viewer, fetch DB data on
 * an async thread, then switch back to the main thread to build components and
 * call {@link Player#openBook(ItemStack)}.
 * </p>
 */
public class LikeBookService {

    private static final Logger log = Logger.getLogger(LikeBookService.class.getName());

    /** Number of players shown in the received / sent ranking pages. */
    private static final int RANKING_LIMIT = 10;

    /**
     * Number of items fetched for the Popular Likes page.
     * The renderer caps the display at 5 due to the 2-line-per-entry format.
     */
    private static final int POPULAR_LIMIT = 10;

    /** Number of items shown on each mine received/sent page. */
    private static final int MINE_LIMIT = 6;

    /** Number of most-liked received items shown on the mine summary page. */
    private static final int MOST_LIKED_LIMIT = 3;

    /** Maximum number of items loaded for the feed. */
    private static final int FEED_MAX_ITEMS = 40;

    /** Number of feed entries shown per book page. */
    private static final int FEED_ITEMS_PER_PAGE = 4;

    private final PlayerStatsRepository playerStatsRepo;
    private final ItemStatsRepository itemStatsRepo;
    private final FeedItemRepository itemRepo;
    private final ReactionRepository reactionRepo;
    private final MessageFactory messageFactory;
    private final JavaPlugin plugin;
    private final String serverId;
    private final DatabaseReadExecutor readExecutor;
    private final PlayerNameResolver playerNameResolver;
    private final LikeRankingBookRenderer rankingRenderer;
    private final LikeMineBookRenderer mineRenderer;
    private final LikeFeedBookRenderer feedRenderer;

    /**
     * Constructs the service with all required dependencies.
     *
     * @param playerStatsRepo repository for per-player aggregation data
     * @param itemStatsRepo   repository for per-item aggregation data
     * @param itemRepo        repository for raw item records
     * @param reactionRepo    repository for like events (reaction lookups)
     * @param messageFactory  factory used to obtain per-player translators
     * @param plugin          the plugin instance (for scheduler access)
     * @param serverId        the server ID used to scope all queries
     */
    public LikeBookService(
            PlayerStatsRepository playerStatsRepo,
            ItemStatsRepository itemStatsRepo,
            FeedItemRepository itemRepo,
            ReactionRepository reactionRepo,
            DatabaseReadExecutor readExecutor,
            PlayerNameResolver playerNameResolver,
            MessageFactory messageFactory,
            JavaPlugin plugin,
            String serverId) {
        this.playerStatsRepo = playerStatsRepo;
        this.itemStatsRepo = itemStatsRepo;
        this.itemRepo = itemRepo;
        this.reactionRepo = reactionRepo;
        this.readExecutor = readExecutor;
        this.playerNameResolver = playerNameResolver;
        this.messageFactory = messageFactory;
        this.plugin = plugin;
        this.serverId = serverId;
        this.rankingRenderer = new LikeRankingBookRenderer();
        this.mineRenderer = new LikeMineBookRenderer();
        this.feedRenderer = new LikeFeedBookRenderer();
    }

    /**
     * Fetches ranking data asynchronously and opens the 3-page ranking book
     * on the main thread when done.
     *
     * @param player the player to open the book for
     */
    public void openRankingBook(Player player) {
        PlayerTranslator tr = messageFactory.translatorFor(player);
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        readExecutor.submit(conn -> {
            try {
                List<PlayerStats> received = playerStatsRepo.getTopReceivedPlayers(serverId, RANKING_LIMIT);
                List<PlayerStats> sent = playerStatsRepo.getTopSentPlayers(serverId, RANKING_LIMIT);
                List<ItemRankingEntry> popular = itemStatsRepo.getTopItems(serverId, POPULAR_LIMIT);
                List<String> popularIds = popular.stream()
                        .map(ItemRankingEntry::itemId)
                        .toList();
                Set<String> reacted = reactionRepo.reactedItemIds(popularIds, playerUuid);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<UUID, String> playerNames = resolveNames(Stream.concat(
                            popular.stream().map(ItemRankingEntry::initiatorUuid),
                            popular.stream().map(ItemRankingEntry::authorUuid)));
                    List<Component> pages = rankingRenderer.buildPages(
                            received, sent, popular, playerUuid, reacted, playerNames, tr);
                    openBook(player, tr.translate("likebeacon.book.ranking.title"), pages);
                });
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to fetch ranking data for " + playerName, e);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.sendMessage(Component.text(tr.translate("likebeacon.error.internal"))
                                .color(NamedTextColor.RED)));
            }
            return null;
        });
    }

    /**
     * Fetches the player's like data asynchronously and opens the 3-page mine
     * book on the main thread when done.
     *
     * @param player the player to open the book for
     */
    public void openMineBook(Player player) {
        PlayerTranslator tr = messageFactory.translatorFor(player);
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        readExecutor.submit(conn -> {
            try {
                Optional<PlayerStats> statsOpt = playerStatsRepo.getPlayerStats(serverId, playerUuid);
                List<ItemRankingEntry> mostLiked = itemStatsRepo
                        .getTopLikedItemsReceivedBy(serverId, playerUuid, MOST_LIKED_LIMIT);
                List<FeedItem> received = itemRepo.getRecentItemsReceivedBy(
                        serverId, playerUuid, MINE_LIMIT);
                List<FeedItem> sent = itemRepo.getRecentItemsInitiatedBy(
                        serverId, playerUuid, MINE_LIMIT);

                List<String> allIds = new ArrayList<>();
                received.stream().map(FeedItem::itemId).forEach(allIds::add);
                sent.stream().map(FeedItem::itemId).forEach(allIds::add);
                Map<String, Long> reactionCounts = allIds.isEmpty()
                        ? Map.of()
                        : itemStatsRepo.reactionCountByItemIds(allIds);

                PlayerStats stats = statsOpt.orElse(null);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<UUID, String> playerNames = resolveNames(Stream.of(
                            received.stream().map(FeedItem::initiatorUuid),
                            received.stream().map(FeedItem::authorUuid),
                            sent.stream().map(FeedItem::initiatorUuid),
                            sent.stream().map(FeedItem::authorUuid),
                            mostLiked.stream().map(ItemRankingEntry::initiatorUuid),
                            mostLiked.stream().map(ItemRankingEntry::authorUuid))
                            .flatMap(stream -> stream));
                    List<Component> pages = mineRenderer.buildPages(stats, mostLiked, received, sent, reactionCounts,
                            playerNames, playerUuid, tr);
                    openBook(player, tr.translate("likebeacon.book.mine.title"), pages);
                });
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to fetch mine data for " + playerName, e);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.sendMessage(Component.text(tr.translate("likebeacon.error.internal"))
                                .color(NamedTextColor.RED)));
            }
            return null;
        });
    }

    /**
     * Fetches the most recent like items asynchronously and opens a
     * multi-page feed book on the main thread when done.
     *
     * @param player the player to open the book for
     */
    public void openFeedBook(Player player) {
        PlayerTranslator tr = messageFactory.translatorFor(player);
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        readExecutor.submit(conn -> {
            try {
                List<FeedItem> items = itemRepo.findRecent(serverId, FEED_MAX_ITEMS);
                List<String> ids = items.stream()
                        .map(FeedItem::itemId)
                        .toList();
                Map<String, Long> reactionCounts = ids.isEmpty()
                        ? Map.of()
                        : itemStatsRepo.reactionCountByItemIds(ids);
                Set<String> reacted = ids.isEmpty()
                        ? Set.of()
                        : reactionRepo.reactedItemIds(ids, playerUuid);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<UUID, String> playerNames = resolveNames(Stream.concat(
                            items.stream().map(FeedItem::initiatorUuid),
                            items.stream().map(FeedItem::authorUuid)));
                    List<Component> pages = feedRenderer.buildPages(
                            items, reactionCounts, reacted, playerNames,
                            playerUuid, FEED_ITEMS_PER_PAGE, tr);
                    openBook(player, tr.translate("likebeacon.command.feed.title"), pages);
                });
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to fetch feed data for " + playerName, e);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.sendMessage(Component.text(tr.translate("likebeacon.error.internal"))
                                .color(NamedTextColor.RED)));
            }
            return null;
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Map<UUID, String> resolveNames(Stream<UUID> uuids) {
        Map<UUID, String> names = new HashMap<>();
        uuids.filter(Objects::nonNull)
                .distinct()
                .forEach(uuid -> names.put(uuid, playerNameResolver.resolve(uuid)));
        return Map.copyOf(names);
    }

    /**
     * Creates a written book item with the given pages and opens it for the player.
     * Must be called on the main thread.
     *
     * @param player the player to show the book to
     * @param title  the book title (shown in the book UI header)
     * @param pages  ordered list of page components
     */
    private void openBook(Player player, String title, List<Component> pages) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null)
            return;
        meta.setTitle(title);
        meta.setAuthor(plugin.getName());
        meta.pages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }
}
