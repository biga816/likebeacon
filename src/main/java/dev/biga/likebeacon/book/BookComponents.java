package dev.biga.likebeacon.book;

import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.util.PlayerTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Shared component-building helpers for book renderers.
 */
class BookComponents {

    private static final int MAX_REASON_WIDTH = 34;
    private static final int ASCII_WIDTH = 2;
    private static final int NON_ASCII_WIDTH = 3;
    private static final long MAX_DISPLAY_REACTION_COUNT = 99;

    enum ParticipantLayout {
        STANDARD(7, 15),
        COMPACT(6, 13);

        private final int directNameLength;
        private final int chatNameLength;

        ParticipantLayout(int directNameLength, int chatNameLength) {
            this.directNameLength = directNameLength;
            this.chatNameLength = chatNameLength;
        }
    }

    private BookComponents() {
    }

    /**
     * Truncates a player name to at most {@code max} characters, appending
     * {@code ".."} when truncated.
     */
    static String truncateName(String text, int max) {
        if (text == null)
            return "";
        if (text.length() <= max)
            return text;
        return text.substring(0, Math.max(0, max - 2)) + "..";
    }

    /**
     * Formats an individual item's reaction count for the width-constrained book
     * UI. Aggregate counts shown in summary views should remain unmodified.
     */
    static String formatReactionCount(long count) {
        return count > MAX_DISPLAY_REACTION_COUNT
                ? MAX_DISPLAY_REACTION_COUNT + "+"
                : Long.toString(count);
    }

    /** Returns the participant layout for a numbered individual-item row. */
    static ParticipantLayout participantLayoutForNumberedItem(long reactionCount) {
        return reactionCount > MAX_DISPLAY_REACTION_COUNT
                ? ParticipantLayout.COMPACT
                : ParticipantLayout.STANDARD;
    }

    /**
     * Truncates a reason to the book UI's display-width budget, appending
     * {@code ".."} when truncated. Width is measured in half-character units:
     * ASCII code points count as two units, while non-ASCII code points count as
     * three. Combining marks do not consume additional width.
     *
     * <p>
     * The iteration is code-point based so surrogate pairs are never split.
     * </p>
     */
    static String truncateReason(String text) {
        if (text == null || text.isEmpty())
            return "";

        int totalWidth = text.codePoints()
                .map(BookComponents::displayWidth)
                .sum();
        if (totalWidth <= MAX_REASON_WIDTH)
            return text;

        String suffix = "..";
        int suffixWidth = suffix.codePoints()
                .map(BookComponents::displayWidth)
                .sum();
        int contentWidthLimit = MAX_REASON_WIDTH - suffixWidth;
        StringBuilder result = new StringBuilder();
        int usedWidth = 0;

        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int width = displayWidth(codePoint);
            if (usedWidth + width > contentWidthLimit)
                break;

            result.appendCodePoint(codePoint);
            usedWidth += width;
            offset += Character.charCount(codePoint);
        }

        return result.append(suffix).toString();
    }

    private static int displayWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
            return 0;
        }
        return codePoint <= 0x7f ? ASCII_WIDTH : NON_ASCII_WIDTH;
    }

    /**
     * Returns {@link NamedTextColor#GREEN} if {@code uuid} matches
     * {@code viewerUuid}, otherwise {@link NamedTextColor#BLACK}.
     */
    static NamedTextColor nameColor(UUID uuid, UUID viewerUuid) {
        return uuid != null && uuid.equals(viewerUuid) ? NamedTextColor.GREEN : NamedTextColor.BLACK;
    }

    /**
     * Builds the participant portion of a feed item line.
     * Chat items show only the author; direct Likes show sender→target.
     * The returned component always includes one trailing space.
     */
    static Component buildItemParticipants(
            FeedItemType type,
            UUID initiatorUuid,
            String initiatorName,
            UUID authorUuid,
            String authorName,
            UUID viewerUuid,
            ParticipantLayout layout) {
        if (type == FeedItemType.CHAT) {
            String displayedAuthor = truncateName(authorName, layout.chatNameLength);
            return Component.text(displayedAuthor + " ").color(nameColor(authorUuid, viewerUuid));
        }

        UUID senderUuid = initiatorUuid != null ? initiatorUuid : authorUuid;
        String senderName = truncateName(initiatorName, layout.directNameLength);
        String targetName = truncateName(authorName, layout.directNameLength);
        return buildSenderArrowTarget(
                senderName, nameColor(senderUuid, viewerUuid),
                targetName, nameColor(authorUuid, viewerUuid));
    }

    /**
     * Builds a heart+count component that is clickable when the viewer
     * has not yet reacted and is not a participant.
     *
     * @param code           the 4-character display code (without {@code #})
     * @param count          current reaction count
     * @param alreadyReacted whether the viewer has already reacted
     * @param isViewer       whether the viewer is sender or target
     * @param tr             locale-bound translator (reserved for future tooltip
     *                       use)
     * @return the styled component
     */
    static Component buildClickableHeart(String code, long count, boolean alreadyReacted,
            boolean isViewer, PlayerTranslator tr) {
        String symbol = alreadyReacted ? "♥" : "♡";
        Component heart = Component.text("[" + symbol + formatReactionCount(count) + "]")
                .color(NamedTextColor.RED);
        if (!alreadyReacted && !isViewer) {
            heart = heart
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/like #" + code));
        }
        return Component.text("").color(NamedTextColor.RED).append(heart);
    }

    /**
     * Builds a {@code [indent]sender→target [♡count]} line.
     *
     * @param senderName  truncated sender name
     * @param senderColor color for the sender name
     * @param targetName  truncated target name
     * @param targetColor color for the target name
     * @return the assembled component (trailing space included; no newline)
     */
    static Component buildSenderArrowTarget(
            String senderName, NamedTextColor senderColor,
            String targetName, NamedTextColor targetColor) {
        return Component.text(senderName).color(senderColor)
                .append(Component.text("→").color(NamedTextColor.RED))
                .append(Component.text(targetName + " ").color(targetColor));
    }

    private static final DateTimeFormatter REASON_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Builds a reason line that shows truncated text inline and the creation
     * date, exact reaction count, and full text on hover.
     *
     * @param fullText      the full reason text shown on hover
     * @param truncated     the truncated text shown inline
     * @param indent        leading whitespace prefix (e.g. {@code "  "} or
     *                      {@code "   "})
     * @param createdAt     item creation timestamp in epoch milliseconds
     * @param reactionCount exact, untruncated reaction count
     * @return the styled component
     */
    static Component buildReasonLine(
            String fullText, String truncated, String indent, long createdAt, long reactionCount) {
        String dateLabel = "[" + REASON_DATE_FORMAT.format(
                Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault())) + "]";
        return Component.text(indent + "\"" + truncated + "\"")
                .color(NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(
                        Component.text(dateLabel).color(NamedTextColor.DARK_GRAY)
                                .append(Component.newline())
                                .append(Component.text("♥" + reactionCount).color(NamedTextColor.RED))
                                .append(Component.newline())
                                .append(Component.text(fullText != null ? fullText : "").color(NamedTextColor.GRAY))));
    }
}
