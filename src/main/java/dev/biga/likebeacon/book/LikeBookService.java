package dev.biga.likebeacon.book;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.PlayerStatsRepository;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.ItemRankingEntry;
import dev.biga.likebeacon.model.PlayerStats;
import dev.biga.likebeacon.service.PlayerNameResolver;
import dev.biga.likebeacon.util.MessageFactory;
import dev.biga.likebeacon.util.PlayerTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Loads book data asynchronously and opens LikeBeacon book views. */
public final class LikeBookService {

    private static final Logger log = Logger.getLogger(LikeBookService.class.getName());
    private static final int RANKING_LIMIT = 10;
    private static final int POPULAR_LIMIT = 10;
    private static final int MINE_LIMIT = 6;
    private static final int MOST_LIKED_LIMIT = 3;
    private static final int FEED_MAX_ITEMS = 40;
    private static final int FEED_ITEMS_PER_PAGE = 4;

    private final PlayerStatsRepository playerStatsRepo;
    private final ItemStatsRepository itemStatsRepo;
    private final FeedItemRepository itemRepo;
    private final ReactionRepository reactionRepo;
    private final DatabaseReadExecutor readExecutor;
    private final PlayerNameResolver playerNameResolver;
    private final MessageFactory messageFactory;
    private final JavaPlugin plugin;
    private final String serverId;
    private final LikeRankingBookRenderer rankingRenderer = new LikeRankingBookRenderer();
    private final LikeMineBookRenderer mineRenderer = new LikeMineBookRenderer();
    private final LikeFeedBookRenderer feedRenderer = new LikeFeedBookRenderer();

    public LikeBookService(PlayerStatsRepository playerStatsRepo, ItemStatsRepository itemStatsRepo,
            FeedItemRepository itemRepo, ReactionRepository reactionRepo,
            DatabaseReadExecutor readExecutor, PlayerNameResolver playerNameResolver,
            MessageFactory messageFactory, JavaPlugin plugin, String serverId) {
        this.playerStatsRepo = playerStatsRepo;
        this.itemStatsRepo = itemStatsRepo;
        this.itemRepo = itemRepo;
        this.reactionRepo = reactionRepo;
        this.readExecutor = readExecutor;
        this.playerNameResolver = playerNameResolver;
        this.messageFactory = messageFactory;
        this.plugin = plugin;
        this.serverId = serverId;
    }

    public void openRankingBook(Player player) {
        loadAndOpen(player, "ranking data", "likebeacon.book.ranking.title",
                this::loadRankingData,
                (data, viewerUuid, translator) -> {
                    Map<UUID, String> names = resolveNames(Stream.concat(
                            data.popular().stream().map(ItemRankingEntry::initiatorUuid),
                            data.popular().stream().map(ItemRankingEntry::authorUuid)));
                    return rankingRenderer.buildPages(
                            data.received(), data.sent(), data.popular(), viewerUuid,
                            data.reactedItemIds(), names, translator);
                });
    }

    public void openMineBook(Player player) {
        loadAndOpen(player, "mine data", "likebeacon.book.mine.title",
                this::loadMineData,
                (data, viewerUuid, translator) -> {
                    Map<UUID, String> names = resolveNames(Stream.of(
                            data.received().stream().map(FeedItem::initiatorUuid),
                            data.received().stream().map(FeedItem::authorUuid),
                            data.sent().stream().map(FeedItem::initiatorUuid),
                            data.sent().stream().map(FeedItem::authorUuid),
                            data.mostLiked().stream().map(ItemRankingEntry::initiatorUuid),
                            data.mostLiked().stream().map(ItemRankingEntry::authorUuid))
                            .flatMap(stream -> stream));
                    return mineRenderer.buildPages(
                            data.stats(), data.mostLiked(), data.received(), data.sent(),
                            data.reactionCounts(), names, viewerUuid, translator);
                });
    }

    public void openFeedBook(Player player) {
        loadAndOpen(player, "feed data", "likebeacon.command.feed.title",
                this::loadFeedData,
                (data, viewerUuid, translator) -> {
                    Map<UUID, String> names = resolveNames(Stream.concat(
                            data.items().stream().map(FeedItem::initiatorUuid),
                            data.items().stream().map(FeedItem::authorUuid)));
                    return feedRenderer.buildPages(
                            data.items(), data.reactionCounts(), data.reactedItemIds(), names,
                            viewerUuid, FEED_ITEMS_PER_PAGE, translator);
                });
    }

    private RankingBookData loadRankingData(UUID viewerUuid) throws SQLException {
        List<PlayerStats> received = playerStatsRepo.getTopReceivedPlayers(serverId, RANKING_LIMIT);
        List<PlayerStats> sent = playerStatsRepo.getTopSentPlayers(serverId, RANKING_LIMIT);
        List<ItemRankingEntry> popular = itemStatsRepo.getTopItems(serverId, POPULAR_LIMIT);
        Set<String> reacted = reactionRepo.reactedItemIds(
                popular.stream().map(ItemRankingEntry::itemId).toList(), viewerUuid);
        return new RankingBookData(received, sent, popular, reacted);
    }

    private MineBookData loadMineData(UUID viewerUuid) throws SQLException {
        PlayerStats stats = playerStatsRepo.getPlayerStats(serverId, viewerUuid).orElse(null);
        List<ItemRankingEntry> mostLiked = itemStatsRepo.getTopLikedItemsReceivedBy(
                serverId, viewerUuid, MOST_LIKED_LIMIT);
        List<FeedItem> received = itemRepo.getRecentItemsReceivedBy(serverId, viewerUuid, MINE_LIMIT);
        List<FeedItem> sent = itemRepo.getRecentItemsInitiatedBy(serverId, viewerUuid, MINE_LIMIT);
        List<String> itemIds = new ArrayList<>();
        received.stream().map(FeedItem::itemId).forEach(itemIds::add);
        sent.stream().map(FeedItem::itemId).forEach(itemIds::add);
        Map<String, Long> counts = itemIds.isEmpty()
                ? Map.of()
                : itemStatsRepo.reactionCountByItemIds(itemIds);
        return new MineBookData(stats, mostLiked, received, sent, counts);
    }

    private FeedBookData loadFeedData(UUID viewerUuid) throws SQLException {
        List<FeedItem> items = itemRepo.findRecent(serverId, FEED_MAX_ITEMS);
        List<String> itemIds = items.stream().map(FeedItem::itemId).toList();
        Map<String, Long> counts = itemIds.isEmpty()
                ? Map.of()
                : itemStatsRepo.reactionCountByItemIds(itemIds);
        Set<String> reacted = itemIds.isEmpty()
                ? Set.of()
                : reactionRepo.reactedItemIds(itemIds, viewerUuid);
        return new FeedBookData(items, counts, reacted);
    }

    private <T> void loadAndOpen(Player player, String dataLabel, String titleKey,
            BookDataLoader<T> loader, BookPageRenderer<T> renderer) {
        PlayerTranslator translator = messageFactory.translatorFor(player);
        UUID viewerUuid = player.getUniqueId();
        String playerName = player.getName();
        readExecutor.submit(connection -> loader.load(viewerUuid))
                .whenComplete((data, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        log.log(Level.WARNING, "Failed to fetch " + dataLabel + " for " + playerName, failure);
                        player.sendMessage(Component.text(translator.translate("likebeacon.error.internal"))
                                .color(NamedTextColor.RED));
                        return;
                    }
                    openBook(player, translator.translate(titleKey),
                            renderer.render(data, viewerUuid, translator));
                }));
    }

    private Map<UUID, String> resolveNames(Stream<UUID> uuids) {
        Map<UUID, String> names = new HashMap<>();
        uuids.filter(Objects::nonNull)
                .distinct()
                .forEach(uuid -> names.put(uuid, playerNameResolver.resolve(uuid)));
        return Map.copyOf(names);
    }

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

    @FunctionalInterface
    private interface BookDataLoader<T> {
        T load(UUID viewerUuid) throws Exception;
    }

    @FunctionalInterface
    private interface BookPageRenderer<T> {
        List<Component> render(T data, UUID viewerUuid, PlayerTranslator translator);
    }

    private record RankingBookData(List<PlayerStats> received, List<PlayerStats> sent,
            List<ItemRankingEntry> popular, Set<String> reactedItemIds) {
    }

    private record MineBookData(PlayerStats stats, List<ItemRankingEntry> mostLiked,
            List<FeedItem> received, List<FeedItem> sent, Map<String, Long> reactionCounts) {
    }

    private record FeedBookData(List<FeedItem> items, Map<String, Long> reactionCounts,
            Set<String> reactedItemIds) {
    }
}
