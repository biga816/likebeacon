package dev.biga.likebeacon.util;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;
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
    private final boolean showDisplayCode;

    /**
     * Constructs a MessageFactory.
     *
     * @param config plugin configuration; reads the prefix from
     *               {@code item.prefix} and display-code visibility from
     *               {@code item.showDisplayCode}.
     */
    public MessageFactory(FileConfiguration config) {
        this.prefix = config.getString("item.prefix", "");
        this.showDisplayCode = config.getBoolean("item.showDisplayCode", false);
    }

    /**
     * Builds an item message for {@code /like log} with an absolute datetime
     * label before the item body.
     * <p>
     * Format:
     * {@code [yyyy-MM-dd HH:mm] <sender> reason → target  [♡n]  (#code)}
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

        return message.append(buildReactSuffix(
                item.displayCode(), reactionCount, alreadyReacted, clickable, true));
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

        Component message = withPrefix(buildItemBody(item, senderDisplay, targetDisplay));

        if (!showReactButton) {
            return message;
        }

        return message.append(buildReactSuffix(
                displayCode, reactionCount, alreadyReacted, clickable, showDisplayCode));
    }

    private Component buildItemBody(FeedItem item, Component senderDisplay, Component authorDisplay) {
        if (item.type() == FeedItemType.CHAT) {
            return Component.translatable("likebeacon.feed.chat.format",
                    authorDisplay,
                    Component.text(item.bodyText()).color(NamedTextColor.WHITE))
                    .color(NamedTextColor.WHITE);
        }

        return Component.translatable("likebeacon.feed.direct.format",
                senderDisplay,
                Component.text(item.bodyText()).color(NamedTextColor.WHITE),
                Component.text("→").color(NamedTextColor.RED),
                authorDisplay)
                .color(NamedTextColor.WHITE);
    }

    public Component buildChatLikeSuffix(String displayCode) {
        return buildReactSuffix(displayCode, -1, false, true, showDisplayCode);
    }

    private Component buildReactSuffix(String displayCode, int reactionCount, boolean alreadyReacted,
            boolean clickable, boolean displayCodeVisible) {
        String heart = alreadyReacted ? "♥" : "♡";
        String count = reactionCount < 0 ? "" : String.valueOf(reactionCount);
        Component reactButton = Component.text("[" + heart + count + "]").color(NamedTextColor.GRAY);

        if (alreadyReacted) {
            reactButton = reactButton.color(NamedTextColor.RED);
        } else if (clickable) {
            reactButton = reactButton
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/like #" + displayCode));
            Component hoverText = Component.translatable("likebeacon.item.react.hover");
            if (displayCodeVisible) {
                hoverText = hoverText
                        .append(Component.text("\n#").color(NamedTextColor.GRAY))
                        .append(Component.text(displayCode).color(NamedTextColor.GRAY));
            }
            reactButton = reactButton.hoverEvent(HoverEvent.showText(hoverText));
        }

        Component suffix = Component.text("  ").append(reactButton);
        if (!displayCodeVisible) {
            return suffix;
        }

        NamedTextColor codeColor = clickable && !alreadyReacted
                ? NamedTextColor.GRAY
                : NamedTextColor.DARK_GRAY;
        Component codeLabel = Component.text("(#" + displayCode + ")").color(codeColor)
                .decorate(TextDecoration.ITALIC);
        return suffix.append(Component.text("  ")).append(codeLabel);
    }

    /**
     * Builds a display-code label for non-log feedback messages. The label is
     * empty when {@code item.showDisplayCode} is disabled.
     *
     * @param displayCode the display code without its {@code #} prefix
     * @return a styled label, or an empty component when hidden by configuration
     */
    public Component displayCodeLabel(String displayCode) {
        if (!showDisplayCode) {
            return Component.empty();
        }
        return Component.text("(#" + displayCode + ")").color(NamedTextColor.GRAY);
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
     * Builds a received-Like notification with the configured item prefix when
     * one is present.
     *
     * @param key  the translation key
     * @param args optional translation arguments
     * @return a notification with a green message body and an optional aqua prefix
     */
    public Component notification(String key, ComponentLike... args) {
        return withPrefix(Component.translatable(key, args).color(NamedTextColor.GREEN));
    }

    private Component withPrefix(Component body) {
        if (prefix == null || prefix.isBlank()) {
            return body;
        }
        return Component.text(prefix).color(NamedTextColor.AQUA)
                .append(Component.text(" "))
                .append(body);
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
