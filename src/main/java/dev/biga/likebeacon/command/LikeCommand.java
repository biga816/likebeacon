package dev.biga.likebeacon.command;

import dev.biga.likebeacon.book.LikeBookService;
import dev.biga.likebeacon.database.ItemStatsRepository;
import dev.biga.likebeacon.database.ReactionRepository;
import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.service.LikeService;
import dev.biga.likebeacon.service.RecentService;
import dev.biga.likebeacon.service.PlayerNameResolver;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified handler for the /like command.
 *
 * <ul>
 * <li>{@code /like <player> <reason...>} — send a like to a player</li>
 * <li>{@code /like #<displayCode>} — react to a item by display code</li>
 * <li>{@code /like log} — show the 5 most recent likes</li>
 * </ul>
 *
 * <p>
 * Argument routing:
 * </p>
 * <ol>
 * <li>If the first argument starts with {@code #}, it is treated as a display
 * code.</li>
 * <li>If the first argument is {@code log} (case-insensitive), the log is
 * shown.</li>
 * <li>Otherwise, the first argument is treated as a player name.</li>
 * </ol>
 */
public class LikeCommand implements CommandExecutor, TabCompleter {

    private static final Logger log = Logger.getLogger(LikeCommand.class.getName());

    private final LikeService likeService;
    private final RecentService recentService;
    private final ItemStatsRepository itemStatsRepository;
    private final ReactionRepository reactionRepository;
    private final DatabaseReadExecutor readExecutor;
    private final MessageFactory messageFactory;
    private final LikeBookService bookService;
    private final Plugin plugin;
    private final PlayerNameResolver playerNameResolver;

    public LikeCommand(LikeService likeService, RecentService recentService,
            ItemStatsRepository itemStatsRepository,
            ReactionRepository reactionRepository, DatabaseReadExecutor readExecutor,
            MessageFactory messageFactory,
            LikeBookService bookService, PlayerNameResolver playerNameResolver, Plugin plugin) {
        this.likeService = likeService;
        this.recentService = recentService;
        this.itemStatsRepository = itemStatsRepository;
        this.reactionRepository = reactionRepository;
        this.readExecutor = readExecutor;
        this.messageFactory = messageFactory;
        this.bookService = bookService;
        this.plugin = plugin;
        this.playerNameResolver = playerNameResolver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageFactory.error("likebeacon.error.console-only"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(messageFactory.usageInfo("like", "displaycode", "feed", "log", "ranking", "mine"));
            return true;
        }

        String first = args[0];

        // /like feed — book UI (recent likes feed)
        if (first.equalsIgnoreCase("feed")) {
            bookService.openFeedBook(player);
            return true;
        }

        // /like log — recent feed items in chat
        if (first.equalsIgnoreCase("log")) {
            handleLog(player);
            return true;
        }

        // /like ranking — book UI
        if (first.equalsIgnoreCase("ranking")) {
            bookService.openRankingBook(player);
            return true;
        }

        // /like mine — book UI
        if (first.equalsIgnoreCase("mine")) {
            bookService.openMineBook(player);
            return true;
        }

        // /like #<displayCode> — react by display code (strip the # prefix)
        if (first.startsWith("#")) {
            String displayCode = first.substring(1);
            if (displayCode.isEmpty()) {
                player.sendMessage(messageFactory.usageInfo("displaycode"));
                return true;
            }
            likeService.react(player, displayCode);
            return true;
        }

        // /like <player> <reason...>
        if (args.length < 2) {
            player.sendMessage(messageFactory.usageInfo("like"));
            return true;
        }

        Player target = Bukkit.getPlayer(first);
        if (target == null) {
            player.sendMessage(messageFactory.error("likebeacon.error.player-not-found", Component.text(first)));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        likeService.sendLike(player, target, reason);
        return true;
    }

    private void handleLog(Player player) {
        List<FeedItem> recent = recentService.getRecent(5);

        if (recent.isEmpty()) {
            player.sendMessage(messageFactory.info("likebeacon.command.log.empty"));
            return;
        }

        List<String> itemIds = recent.stream().map(FeedItem::itemId).toList();
        java.util.UUID playerUuid = player.getUniqueId();
        readExecutor.submit(conn -> new LogData(
                itemStatsRepository.reactionCountByItemIds(itemIds),
                reactionRepository.reactedItemIds(itemIds, playerUuid)))
                .whenComplete((data, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerUuid);
            if (online == null)
                return;
            if (failure != null) {
                log.log(Level.WARNING, "Failed to get reaction data for recent items", failure);
                online.sendMessage(messageFactory.error("likebeacon.error.internal"));
                return;
            }
            renderLog(online, recent, data.counts(), data.reactedIds());
        }));
    }

    private void renderLog(Player player, List<FeedItem> recent, Map<String, Long> countMap,
            Set<String> reactedIds) {
        player.sendMessage(messageFactory.info("likebeacon.command.log.title"));
        for (FeedItem item : recent) {
            boolean isOwnSend = player.getUniqueId().equals(item.initiatorUuid());
            Component senderDisplay = item.initiatorUuid() == null ? Component.empty()
                    : isOwnSend
                            ? Component.translatable("likebeacon.item.you").color(NamedTextColor.GREEN)
                            : Component.text(playerNameResolver.resolve(item.initiatorUuid()))
                                    .color(NamedTextColor.WHITE);
            boolean isOwnLike = item.authorUuid().equals(player.getUniqueId());
            Component targetDisplay = isOwnLike
                    ? Component.translatable("likebeacon.item.you").color(NamedTextColor.GREEN)
                    : Component.text(playerNameResolver.resolve(item.authorUuid())).color(NamedTextColor.WHITE);
            int count = countMap.getOrDefault(item.itemId(), 0L).intValue();
            boolean alreadyReacted = reactedIds.contains(item.itemId());
            Component msg = messageFactory.buildLogItemMessage(item, senderDisplay, targetDisplay, count,
                    alreadyReacted, !isOwnLike);
            player.sendMessage(msg);
        }

        recentService.updateLastSeen(player.getUniqueId(), recent.get(0).itemId());
    }

    private record LogData(Map<String, Long> counts, Set<String> reactedIds) {
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();

            // Subcommands
            if ("feed".startsWith(partial))
                suggestions.add("feed");
            if ("log".startsWith(partial))
                suggestions.add("log");
            if ("ranking".startsWith(partial))
                suggestions.add("ranking");
            if ("mine".startsWith(partial))
                suggestions.add("mine");

            // Advertise the display-code syntax without exposing unexplained codes in
            // the initial suggestion list. Once the user enters or selects "#", show
            // matching recent display codes.
            if (partial.isEmpty()) {
                suggestions.add("#");
            } else if (partial.startsWith("#")) {
                recentService.getRecentDisplayCodes(5).stream()
                        .map(code -> "#" + code)
                        .filter(s -> s.toLowerCase().startsWith(partial))
                        .forEach(suggestions::add);
            }

            // Online player names
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(sender))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .forEach(suggestions::add);

            return suggestions;
        }
        return List.of();
    }
}
