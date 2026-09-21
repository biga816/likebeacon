package dev.biga.likebeacon.util;

import dev.biga.likebeacon.model.FeedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Factory for building chat message components using the Adventure API.
 * <p>
 * User-facing text is expressed as {@link Component#translatable(String)} keys,
 * which Adventure / Paper resolves to each player's client locale at send time
 * via {@link net.kyori.adventure.translation.GlobalTranslator}.
 * </p>
 */
public class MessageFactory {

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String prefix;

    /**
     * Constructs a MessageFactory.
     *
     * @param config plugin configuration; reads the prefix from
     *               {@code item.prefix}.
     *               Defaults to {@code "[LIKE]"} if the key is absent.
     */
    public MessageFactory(FileConfiguration config) {
        this.prefix = config.getString("item.prefix", "[LIKE]");
    }

    /**
     * Builds a item message for {@code /like log} with an absolute datetime
     * label inserted between the prefix and the sender name.
     * <p>
     * Format:
     * {@code [LIKE][yyyy-MM-dd HH:mm] sender → target: "reason"  [♡n]  (#code)}
     * </p>
     *
     * @param item           the item data
     * @param senderDisplay  pre-built component for the sender name slot
     * @param targetDisplay  pre-built component for the target name slot
     * @param reactionCount  total number of reactions, or {@code -1} to show no
     *                       count
     * @param alreadyReacted whether the viewing player has already reacted
     * @param clickable      whether the react button should have a click event
     * @return the assembled {@link Component}
     */
    public Component buildLogItemMessage(FeedItem item,
            Component senderDisplay, Component targetDisplay,
            int reactionCount, boolean alreadyReacted, boolean clickable) {
        String dateLabel = "[" + LOG_DATE_FORMAT.format(
                Instant.ofEpochMilli(item.createdAt())
                        .atZone(ZoneId.systemDefault()))
                + "]";

        Component message = Component.text(dateLabel + " ").color(NamedTextColor.AQUA)
                .append(buildItemBody(item, senderDisplay, targetDisplay));

        return message.append(buildReactSuffix(item.displayCode(), reactionCount, alreadyReacted, clickable));
    }

    /**
     * Builds the simplest item message: no reaction count, no react button.
     * <p>
     * Use
     * {@link #buildItemMessage(FeedItem, Component, Component, int, boolean, boolean, boolean)}
     * for full control over reaction count and button behavior.
     * </p>
     *
     * @param item          the item data
     * @param senderDisplay pre-built component for the sender name slot
     * @param targetDisplay pre-built component for the target name slot
     * @return the assembled {@link Component}
     */
    public Component buildItemMessage(FeedItem item, Component senderDisplay, Component targetDisplay) {
        return buildItemMessage(item, senderDisplay, targetDisplay, -1, false, false, false);
    }

    /**
     * Builds a server-wide item message with full control over sender/target
     * display components, react button visibility, and click interactivity.
     * <p>
     * When {@code clickable} is {@code false} and the player has not yet reacted,
     * the react button is shown as {@code ♡} without underline or click event
     * (used when the viewing player is the like target and cannot react).
     * </p>
     *
     * @param item            the item data
     * @param senderDisplay   pre-built component for the sender name slot
     * @param targetDisplay   pre-built component for the target name slot
     * @param reactionCount   total number of reactions, or {@code -1} to show no
     *                        count
     * @param alreadyReacted  whether the viewing player has already reacted
     * @param showReactButton whether to append the react button
     * @param clickable       whether the react button should have a click event and
     *                        underline decoration
     * @return the assembled {@link Component}
     */
    public Component buildItemMessage(FeedItem item, Component senderDisplay, Component targetDisplay,
            int reactionCount, boolean alreadyReacted, boolean showReactButton, boolean clickable) {
        String displayCode = item.displayCode();

        Component message = Component.text(prefix)
                .color(NamedTextColor.AQUA)
                .append(Component.text(" "))
                .append(buildItemBody(item, senderDisplay, targetDisplay));

        if (!showReactButton) {
            return message;
        }

        return message.append(buildReactSuffix(displayCode, reactionCount, alreadyReacted, clickable));
    }

    private Component buildItemBody(FeedItem item, Component senderDisplay, Component authorDisplay) {
        if ("CHAT".equals(item.itemType())) {
            return Component.translatable("likebeacon.feed.chat.format",
                    authorDisplay,
                    Component.text(item.bodyText()).color(NamedTextColor.WHITE));
        }

        return Component.translatable("likebeacon.feed.chat.format",
                senderDisplay.append(Component.text(" → ").color(NamedTextColor.RED))
                        .append(authorDisplay),
                Component.text(item.bodyText()).color(NamedTextColor.WHITE));
    }

    public Component buildChatLikeSuffix(String displayCode) {
        return buildReactSuffix(displayCode, -1, false, true);
    }

    private Component buildReactSuffix(String displayCode, int reactionCount, boolean alreadyReacted,
            boolean clickable) {
        String heart = alreadyReacted ? "♥" : "♡";
        String count = reactionCount < 0 ? "" : String.valueOf(reactionCount);
        Component reactButton = Component.text("[" + heart + count + "]").color(NamedTextColor.GRAY);
        Component codeLabel = Component.text("(#" + displayCode + ")").color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.ITALIC);

        if (alreadyReacted) {
            reactButton = reactButton.color(NamedTextColor.RED);
        } else if (clickable) {
            reactButton = reactButton
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/like #" + displayCode))
                    .hoverEvent(HoverEvent.showText(
                            Component.translatable("likebeacon.item.react.hover")
                                    .append(Component.text("\n#").color(NamedTextColor.GRAY))
                                    .append(Component.text(displayCode).color(NamedTextColor.GRAY))));
            codeLabel = codeLabel.color(NamedTextColor.GRAY);
        }

        return Component.text("  ").append(reactButton).append(Component.text("  ")).append(codeLabel);
    }

    /**
     * Builds a usage message component by combining the usage prefix with one or
     * more usage keys, separated by " | ".
     *
     * @param usageKeys the translation keys for each usage entry
     * @return a yellow {@link Component}
     */
    public Component usageInfo(String... usageKeys) {
        Component msg = Component.translatable("likebeacon.command.like.usage.prefix").color(NamedTextColor.YELLOW);
        for (int i = 0; i < usageKeys.length; i++) {
            msg = msg.append(Component.text(i == 0 ? " " : "  |  ").color(NamedTextColor.YELLOW))
                    .append(Component.translatable("likebeacon.command.like.usage." + usageKeys[i])
                            .color(NamedTextColor.YELLOW));
        }
        return msg;
    }

    /**
     * Builds an error message component from a translation key.
     *
     * @param key  the translation key (e.g. {@code "likebeacon.error.self"})
     * @param args optional translation arguments ({@code {0}}, {@code {1}}, …)
     * @return a red {@link Component}
     */
    public Component error(String key, ComponentLike... args) {
        return Component.translatable(key, args).color(NamedTextColor.RED);
    }

    /**
     * Builds an informational message component from a translation key.
     *
     * @param key  the translation key
     * @param args optional translation arguments
     * @return a yellow {@link Component}
     */
    public Component info(String key, ComponentLike... args) {
        return Component.translatable(key, args).color(NamedTextColor.YELLOW);
    }

    /**
     * Builds a success message component from a translation key.
     *
     * @param key  the translation key
     * @param args optional translation arguments
     * @return a green {@link Component}
     */
    public Component success(String key, ComponentLike... args) {
        return Component.translatable(key, args).color(NamedTextColor.GREEN);
    }

    /**
     * Returns a {@link PlayerTranslator} bound to the given player's locale.
     * Use this for contexts where Adventure cannot resolve translatable components
     * automatically (e.g. book NBT pages).
     *
     * @param player the player whose locale should be used
     * @return a locale-bound translator
     */
    public PlayerTranslator translatorFor(Player player) {
        return new PlayerTranslator(player.locale());
    }
}
