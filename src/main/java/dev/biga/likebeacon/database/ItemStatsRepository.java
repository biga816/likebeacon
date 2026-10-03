package dev.biga.likebeacon.database;

import dev.biga.likebeacon.model.ItemRankingEntry;
import dev.biga.likebeacon.model.ItemStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository for the {@code item_stats} aggregation table.
 * <p>
 * Write methods accept an explicit {@link Connection} to participate in a
 * caller-managed transaction. Read methods use the shared connection from
 * {@link DatabaseManager#getReadConnection()}.
 * </p>
 */
public class ItemStatsRepository {

    private final DatabaseManager databaseManager;

    /**
     * Constructs a ItemStatsRepository.
     *
     * @param databaseManager the database connection manager
     */
    public ItemStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ── Write methods (transactional, called from DatabaseWriteExecutor) ────────

    /**
     * Inserts a new stats row with {@code reaction_count = 1}.
     * Called once when a direct like item is created; the sender's own
     * initial like is counted as the first reaction.
     *
     * @param conn      the connection in the active transaction
     * @param serverId  the server ID for scoping the record
     * @param itemId    the new item's ID
     * @param updatedAt current timestamp in epoch milliseconds
     * @throws SQLException if a database error occurs
     */
    public void insertNew(Connection conn, String serverId, String itemId, long updatedAt) throws SQLException {
        String sql = """
                INSERT INTO item_stats (item_id, server_id, reaction_count, updated_at)
                VALUES (?, ?, 1, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, serverId);
            ps.setLong(3, updatedAt);
            ps.executeUpdate();
        }
    }

    /**
     * Increments {@code reaction_count} for an existing item, or inserts a
     * new row with count 1 if none exists (upsert), and returns the resulting
     * count from the same statement.
     * Called when a player reacts to an existing item.
     *
     * @param conn      the connection in the active transaction
     * @param serverId  the server ID for scoping the record
     * @param itemId    the item ID to update
     * @param updatedAt current timestamp in epoch milliseconds
     * @return the reaction count after this increment
     * @throws SQLException if a database error occurs
     */
    public long incrementReactionCount(Connection conn, String serverId, String itemId, long updatedAt)
            throws SQLException {
        String sql = """
                INSERT INTO item_stats (item_id, server_id, reaction_count, updated_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT(item_id) DO UPDATE SET
                    reaction_count = reaction_count + 1,
                    updated_at     = excluded.updated_at
                RETURNING reaction_count
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, serverId);
            ps.setLong(3, updatedAt);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Incrementing item stats returned no reaction count for " + itemId);
                }
                return rs.getLong("reaction_count");
            }
        }
    }

    // ── Read methods (for /like ranking) ─────────────────────────────────────

    /**
     * Returns the top {@code limit} items by {@code reaction_count} for the
     * given server, joined with their item details for display purposes.
     *
     * @param serverId the server ID to filter by
     * @param limit    maximum number of results
     * @return list of ranking entries ordered by reaction_count DESC
     * @throws SQLException if a database error occurs
     */
    public List<ItemRankingEntry> getTopItems(String serverId, int limit) throws SQLException {
        String sql = """
                SELECT b.item_id, b.display_code, b.created_at, b.item_type,
                       b.initiator_uuid, b.author_uuid, b.body_text,
                       s.reaction_count
                FROM item_stats s
                JOIN feed_items b ON b.item_id = s.item_id
                WHERE s.server_id = ?
                ORDER BY s.reaction_count DESC
                LIMIT ?
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ItemRankingEntry> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new ItemRankingEntry(
                            rs.getString("item_id"),
                            rs.getString("display_code"),
                            rs.getLong("created_at"),
                            rs.getString("item_type"),
                            parseNullableUuid(rs.getString("initiator_uuid")),
                            UUID.fromString(rs.getString("author_uuid")),
                            rs.getString("body_text"),
                            rs.getLong("reaction_count")));
                }
                return results;
            }
        }
    }

    /**
     * Returns the reaction count for each of the given item IDs in a single
     * query, reading from the pre-computed {@code item_stats} table.
     * Item IDs with no stats row are not included in the returned map.
     * Used by {@code /like log} to avoid per-row COUNT queries on reactions.
     *
     * @param itemIds the list of item IDs to look up
     * @return a map of itemId → reaction_count
     * @throws SQLException if a database error occurs
     */
    public Map<String, Long> reactionCountByItemIds(List<String> itemIds) throws SQLException {
        if (itemIds.isEmpty())
            return Map.of();
        String placeholders = itemIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT item_id, reaction_count FROM item_stats WHERE item_id IN ("
                + placeholders + ")";
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < itemIds.size(); i++) {
                ps.setString(i + 1, itemIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> result = new HashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("item_id"), rs.getLong("reaction_count"));
                }
                return result;
            }
        }
    }

    /**
     * Returns the items received by the given player with the highest
     * {@code reaction_count} (ties broken by most recent), scoped to the given
     * server, up to {@code limit} entries.
     *
     * @param serverId   the server ID to filter by
     * @param playerUuid the recipient's UUID
     * @param limit      maximum number of results
     * @return list of ranking entries ordered by reaction_count DESC, created_at
     *         DESC
     * @throws SQLException if a database error occurs
     */
    public List<ItemRankingEntry> getTopLikedItemsReceivedBy(String serverId, UUID playerUuid, int limit)
            throws SQLException {
        String sql = """
                SELECT b.item_id, b.display_code, b.created_at, b.item_type,
                       b.initiator_uuid, b.author_uuid, b.body_text,
                       s.reaction_count
                FROM feed_items b
                JOIN item_stats s ON s.item_id = b.item_id
                WHERE b.server_id = ?
                  AND b.author_uuid = ?
                ORDER BY s.reaction_count DESC, b.created_at DESC
                LIMIT ?
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, playerUuid.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ItemRankingEntry> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new ItemRankingEntry(
                            rs.getString("item_id"),
                            rs.getString("display_code"),
                            rs.getLong("created_at"),
                            rs.getString("item_type"),
                            parseNullableUuid(rs.getString("initiator_uuid")),
                            UUID.fromString(rs.getString("author_uuid")),
                            rs.getString("body_text"),
                            rs.getLong("reaction_count")));
                }
                return results;
            }
        }
    }

    /**
     * Returns the stats for a single item, or empty if no record exists.
     *
     * @param itemId the item ID
     * @return an Optional containing the stats, or empty
     * @throws SQLException if a database error occurs
     */
    public Optional<ItemStats> getStats(String itemId) throws SQLException {
        String sql = "SELECT * FROM item_stats WHERE item_id = ?";
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ItemStats(
                            rs.getString("item_id"),
                            rs.getLong("reaction_count"),
                            rs.getLong("updated_at")));
                }
                return Optional.empty();
            }
        }
    }

    private static UUID parseNullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
