package dev.biga.likebeacon.model;

import java.util.UUID;

/**
 * DTO for a ranking result that joins item details with its reaction
 * count.
 * Used for the future {@code /like ranking} command.
 *
 * @param itemId        the item's unique identifier
 * @param displayCode   the 4-character display code (e.g. {@code "A7K2"})
 * @param createdAt     item creation timestamp in epoch milliseconds
 * @param type          the feed item type
 * @param initiatorUuid UUID of the DIRECT initiator, or null for CHAT
 * @param authorUuid    UUID of the feed item author
 * @param bodyText      the feed item body
 * @param reactionCount total number of reactions including the initial like
 */
public record ItemRankingEntry(
    String itemId,
    String displayCode,
    long createdAt,
    FeedItemType type,
    UUID initiatorUuid,
    UUID authorUuid,
    String bodyText,
    long reactionCount) {
}
