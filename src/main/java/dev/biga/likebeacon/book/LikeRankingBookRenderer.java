package dev.biga.likebeacon.book;

import dev.biga.likebeacon.model.ItemRankingEntry;
import dev.biga.likebeacon.model.PlayerStats;
import dev.biga.likebeacon.util.PlayerTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Builds the 3-page book for {@code /like ranking}.
 *
 * <ul>
 * <li>Page 1: Top Received — players ranked by received count</li>
 * <li>Page 2: Top Givers — players ranked by sent count</li>
 * <li>Page 3: Popular Likes — items ranked by reaction count</li>
 * </ul>
 */
public class LikeRankingBookRenderer {

        /** Max player name characters shown on rank list lines. */
        private static final int MAX_NAME_LEN = 10;

        /** Max entries shown on the Popular Likes page (2-line format). */
        private static final int MAX_POPULAR_ENTRIES = 6;

        /**
         * Builds all pages for the ranking book.
         *
         * @param received   players ranked by received count
         * @param sent       players ranked by sent count
         * @param popular    items ranked by reaction count
         * @param playerNames pre-resolved names keyed by participant UUID
         * @param translator locale-bound translator for the viewing player
         * @return list of page components (3 pages)
         */
        public List<Component> buildPages(
                        List<PlayerStats> received,
                        List<PlayerStats> sent,
                        List<ItemRankingEntry> popular,
                        UUID viewerUuid,
                        Set<String> reactedItemIds,
                        Map<UUID, String> playerNames,
                        PlayerTranslator translator) {
                List<Component> pages = new ArrayList<>();
                pages.add(buildReceivedPage(received, translator));
                pages.add(buildSentPage(sent, translator));
                pages.add(buildPopularPage(popular, viewerUuid, reactedItemIds, playerNames, translator));
                return pages;
        }

        // ── Page builders ─────────────────────────────────────────────────────────

        private Component buildReceivedPage(List<PlayerStats> list, PlayerTranslator tr) {
                TextComponent.Builder b = Component.text();
                b.append(Component.text(tr.translate("likebeacon.book.ranking.title"))
                                .color(NamedTextColor.BLACK)
                                .decorate(TextDecoration.BOLD));
                b.append(Component.newline());
                b.append(Component.newline());
                b.append(Component.text("⏷" + tr.translate("likebeacon.book.ranking.received"))
                                .color(NamedTextColor.DARK_GRAY)
                                .decorate(TextDecoration.BOLD));
                appendPlayerStatsList(b, list, PlayerStats::receivedCount, tr);
                return b.build();
        }

        private Component buildSentPage(List<PlayerStats> list, PlayerTranslator tr) {
                TextComponent.Builder b = Component.text();
                b.append(Component.text("⏷" + tr.translate("likebeacon.book.ranking.sent"))
                                .color(NamedTextColor.DARK_GRAY)
                                .decorate(TextDecoration.BOLD));
                appendPlayerStatsList(b, list, PlayerStats::sentCount, tr);
                return b.build();
        }

        private Component buildPopularPage(List<ItemRankingEntry> list, UUID viewerUuid,
                        Set<String> reactedItemIds, Map<UUID, String> playerNames,
                        PlayerTranslator tr) {
                TextComponent.Builder b = Component.text();
                b.append(Component.text("⏷" + tr.translate("likebeacon.book.ranking.popular"))
                                .color(NamedTextColor.DARK_GRAY)
                                .decorate(TextDecoration.BOLD));

                if (list.isEmpty()) {
                        b.append(Component.newline());
                        b.append(Component.newline());
                        b.append(Component.text(tr.translate("likebeacon.book.ranking.empty"))
                                        .color(NamedTextColor.GRAY));
                } else {
                        int limit = Math.min(list.size(), MAX_POPULAR_ENTRIES);
                        for (int i = 0; i < limit; i++) {
                                ItemRankingEntry entry = list.get(i);
                                String reason = BookComponents.truncateReason(entry.bodyText());
                                String code = entry.displayCode();
                                boolean alreadyReacted = reactedItemIds.contains(entry.itemId());
                                boolean isViewer = viewerUuid.equals(entry.initiatorUuid())
                                                || viewerUuid.equals(entry.authorUuid());

                                b.append(Component.newline());
                                b.append(Component.text((i + 1) + ". ")
                                                .color(NamedTextColor.DARK_GRAY));
                                b.append(BookComponents.buildItemParticipants(
                                                entry.type(), entry.initiatorUuid(),
                                                playerNames.get(entry.initiatorUuid()), entry.authorUuid(),
                                                playerNames.get(entry.authorUuid()),
                                                viewerUuid, BookComponents.participantLayoutForNumberedItem(
                                                                entry.reactionCount())));
                                b.append(BookComponents.buildClickableHeart(code, entry.reactionCount(), alreadyReacted,
                                                isViewer, tr));
                                b.append(Component.newline());
                                b.append(BookComponents.buildReasonLine(entry.bodyText(), reason, "   ",
                                                entry.createdAt(), entry.reactionCount()));
                        }
                }
                return b.build();
        }

        // ── Shared helpers ────────────────────────────────────────────────────────

        private void appendPlayerStatsList(TextComponent.Builder b, List<PlayerStats> list,
                        ToLongFunction<PlayerStats> countExtractor, PlayerTranslator tr) {
                if (list.isEmpty()) {
                        b.append(Component.newline());
                        b.append(Component.newline());
                        b.append(Component.text(tr.translate("likebeacon.book.ranking.empty"))
                                        .color(NamedTextColor.GRAY));
                } else {
                        for (int i = 0; i < list.size(); i++) {
                                PlayerStats s = list.get(i);
                                b.append(Component.newline());
                                b.append(Component.text((i + 1) + ". ").color(NamedTextColor.DARK_GRAY));
                                b.append(Component.text(BookComponents.truncateName(s.playerName(), MAX_NAME_LEN))
                                                .color(NamedTextColor.BLACK));
                                b.append(Component.text("  ♥" + countExtractor.applyAsLong(s))
                                                .color(NamedTextColor.RED));
                        }
                }
        }
}
