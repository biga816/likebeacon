package dev.biga.likebeacon.book;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.util.PlayerTranslator;
import dev.biga.likebeacon.util.RelativeTimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a multi-page book for {@code /like feed}.
 *
 * <p>
 * Each page shows {@code itemsPerPage} like events in reverse-chronological
 * order. Per entry:
 * </p>
 * <ul>
 * <li>Line 1: relative time (e.g. "2m ago" / "2分前")</li>
 * <li>Line 2: {@code sender→target  [♡count]} — heart is clickable if the
 * viewer has not yet reacted and is not a participant</li>
 * <li>Line 3: {@code  "reason"} — truncated, hover shows full text</li>
 * </ul>
 *
 * <p>
 * Display codes are intentionally omitted from this view.
 * </p>
 */
public class LikeFeedBookRenderer {

        /**
         * Builds all pages for the feed book.
         *
         * @param items          recent items, newest first
         * @param reactionCounts map of itemId → reaction count
         * @param reactedItems   item IDs the viewer has already reacted to
         * @param playerNames    pre-resolved names keyed by participant UUID
         * @param viewerUuid     UUID of the player opening the book
         * @param itemsPerPage   number of entries per book page
         * @param tr             locale-bound translator for the viewing player
         * @return ordered list of page components
         */
        public List<Component> buildPages(
                        List<FeedItem> items,
                        Map<String, Long> reactionCounts,
                        Set<String> reactedItems,
                        Map<UUID, String> playerNames,
                        UUID viewerUuid,
                        int itemsPerPage,
                        PlayerTranslator tr) {

                List<Component> pages = new ArrayList<>();

                if (items.isEmpty()) {
                        TextComponent.Builder b = Component.text();
                        b.append(Component.text(tr.translate("likebeacon.command.feed.title"))
                                        .color(NamedTextColor.BLACK)
                                        .decorate(TextDecoration.BOLD));
                        b.append(Component.newline());
                        b.append(Component.newline());
                        b.append(Component.text(tr.translate("likebeacon.command.feed.empty"))
                                        .color(NamedTextColor.GRAY));
                        pages.add(b.build());
                        return pages;
                }

                for (int pageStart = 0; pageStart < items.size(); pageStart += itemsPerPage) {
                        int pageEnd = Math.min(pageStart + itemsPerPage, items.size());
                        List<FeedItem> pageItems = items.subList(pageStart, pageEnd);

                        TextComponent.Builder b = Component.text();

                        // Title only on the first page
                        if (pageStart == 0) {
                                b.append(Component.text(tr.translate("likebeacon.command.feed.title"))
                                                .color(NamedTextColor.BLACK)
                                                .decorate(TextDecoration.BOLD));
                                b.append(Component.newline());
                                b.append(Component.newline());
                        }

                        for (int i = 0; i < pageItems.size(); i++) {
                                FeedItem bc = pageItems.get(i);

                                String timeStr = RelativeTimeFormatter.format(bc.createdAt(), tr);
                                String reason = BookComponents.truncateReason(bc.bodyText());
                                long count = reactionCounts.getOrDefault(bc.itemId(), 0L);
                                String code = bc.displayCode();
                                boolean alreadyReacted = reactedItems.contains(bc.itemId());
                                boolean isViewer = viewerUuid.equals(bc.initiatorUuid())
                                                || viewerUuid.equals(bc.authorUuid());

                                // Line 1: relative time
                                b.append(Component.text("[" + timeStr + "]").color(NamedTextColor.DARK_GRAY)
                                                .decorate(TextDecoration.ITALIC));
                                b.append(Component.newline());

                                // Line 2: sender→target [♡count]
                                b.append(Component.text(" "));
                                b.append(BookComponents.buildItemParticipants(
                                                bc.type(), bc.initiatorUuid(), playerNames.get(bc.initiatorUuid()),
                                                bc.authorUuid(), playerNames.get(bc.authorUuid()), viewerUuid,
                                                BookComponents.ParticipantLayout.STANDARD));
                                b.append(BookComponents.buildClickableHeart(code, count, alreadyReacted, isViewer, tr));
                                b.append(Component.newline());

                                // Line 3: reason (truncated inline, full text on hover)
                                b.append(BookComponents.buildReasonLine(
                                                bc.bodyText(), reason, " ", bc.createdAt(), count));

                                // Blank separator between entries (not after the last one on the page)
                                if (i < pageItems.size() - 1) {
                                        b.append(Component.newline());
                                }
                        }

                        pages.add(b.build());
                }

                return pages;
        }
}
