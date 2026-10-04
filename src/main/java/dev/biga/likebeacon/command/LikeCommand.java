package dev.biga.likebeacon.command;

import dev.biga.likebeacon.book.LikeBookService;
import dev.biga.likebeacon.service.feed.LikeLogService;
import dev.biga.likebeacon.service.feed.RecentService;
import dev.biga.likebeacon.service.like.LikeService;
import dev.biga.likebeacon.util.MessageFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private final LikeService likeService;
    private final RecentService recentService;
    private final LikeLogService logService;
    private final MessageFactory messageFactory;
    private final LikeBookService bookService;

    public LikeCommand(LikeService likeService, RecentService recentService, LikeLogService logService,
            MessageFactory messageFactory, LikeBookService bookService) {
        this.likeService = likeService;
        this.recentService = recentService;
        this.logService = logService;
        this.messageFactory = messageFactory;
        this.bookService = bookService;
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
            logService.show(player);
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
