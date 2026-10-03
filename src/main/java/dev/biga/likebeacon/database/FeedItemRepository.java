package dev.biga.likebeacon.database;

import dev.biga.likebeacon.model.FeedItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing the feed_items table.
 */
public class FeedItemRepository {

    private final DatabaseManager databaseManager;

    /**
     * Constructs a FeedItemRepository.
     *
     * @param databaseManager the database connection manager
     */
    public FeedItemRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a item to the database.
     *
     * @param item the item to save
     * @throws SQLException if a database operation fails
     */
    public void save(Connection conn, FeedItem item) throws SQLException {
        String sql = """
                INSERT INTO feed_items
                    (item_id, server_id, display_code, created_at, item_type, author_uuid,
                     initiator_uuid, body_text, world, x, y, z)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.itemId());
            ps.setString(2, item.serverId());
            ps.setString(3, item.displayCode());
            ps.setLong(4, item.createdAt());
            ps.setString(5, item.itemType());
            ps.setString(6, item.authorUuid().toString());
            setNullableUuid(ps, 7, item.initiatorUuid());
            ps.setString(8, item.bodyText());
            setNullableString(ps, 9, item.world());
            setNullableInteger(ps, 10, item.x());
            setNullableInteger(ps, 11, item.y());
            setNullableInteger(ps, 12, item.z());
            ps.executeUpdate();
        }
    }

    /**
     * Finds the most recent item matching the given serverId and displayCode.
     *
     * @param serverId    the server ID to scope the lookup
     * @param displayCode the display code to search for
     * @return an Optional containing the most recent matching item, or empty
     *         if not found
     * @throws SQLException if a database operation fails
     */
    public Optional<FeedItem> findLatestByDisplayCode(String serverId, String displayCode) throws SQLException {
        String sql = """
                SELECT * FROM feed_items
                WHERE server_id = ?
                  AND display_code = ?
                ORDER BY created_at DESC
                LIMIT 1
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, displayCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Retrieves recent items for the given server, ordered by creation time
     * descending.
     *
     * @param serverId the server ID to filter by
     * @param limit    maximum number of results to return
     * @return list of items ordered by created_at DESC
     * @throws SQLException if a database operation fails
     */
    public List<FeedItem> findRecent(String serverId, int limit) throws SQLException {
        String sql = """
                SELECT * FROM feed_items
                WHERE server_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<FeedItem> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    /**
     * Checks whether a item with the given displayCode exists within the most
     * recent {@code recentWindow} items for the given server.
     *
     * @param serverId     the server ID to scope the check
     * @param displayCode  the display code to check
     * @param recentWindow number of most recent items to search within
     * @return true if a matching item exists in the recent window
     * @throws SQLException if a database operation fails
     */
    public boolean existsInRecentByDisplayCode(Connection conn, String serverId, String displayCode, int recentWindow)
            throws SQLException {
        String sql = """
                SELECT 1 FROM feed_items
                WHERE server_id = ?
                  AND display_code = ?
                  AND item_id IN (
                    SELECT item_id FROM feed_items
                    WHERE server_id = ?
                    ORDER BY created_at DESC LIMIT ?
                )
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, displayCode);
            ps.setString(3, serverId);
            ps.setInt(4, recentWindow);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── Mine-related read methods ─────────────────────────────────────────────

    /**
     * Returns the most recent items in which the given player was the
     * recipient, scoped to the given server.
     *
     * @param serverId   the server ID to filter by
     * @param playerUuid the recipient's UUID
     * @param limit      maximum number of results
     * @return list of items ordered by created_at DESC
     * @throws SQLException if a database error occurs
     */
    public List<FeedItem> getRecentItemsReceivedBy(String serverId, UUID playerUuid, int limit)
            throws SQLException {
        String sql = """
                SELECT * FROM feed_items
                WHERE server_id = ?
                  AND author_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, playerUuid.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<FeedItem> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    /**
     * Returns the most recent items sent by the given player, scoped to the
     * given server.
     *
     * @param serverId   the server ID to filter by
     * @param playerUuid the sender's UUID
     * @param limit      maximum number of results
     * @return list of items ordered by created_at DESC
     * @throws SQLException if a database error occurs
     */
    public List<FeedItem> getRecentItemsInitiatedBy(String serverId, UUID playerUuid, int limit)
            throws SQLException {
        String sql = """
                SELECT * FROM feed_items
                WHERE server_id = ?
                  AND initiator_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, playerUuid.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<FeedItem> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    /**
     * Maps the current row of a ResultSet to a FeedItem.
     *
     * @param rs the ResultSet to map from
     * @return the mapped FeedItem
     * @throws SQLException if reading the ResultSet fails
     */
    private FeedItem mapRow(ResultSet rs) throws SQLException {
        return new FeedItem(
                rs.getString("item_id"),
                rs.getString("server_id"),
                rs.getString("display_code"),
                rs.getLong("created_at"),
                rs.getString("item_type"),
                UUID.fromString(rs.getString("author_uuid")),
                parseNullableUuid(rs.getString("initiator_uuid")),
                rs.getString("body_text"),
                rs.getString("world"),
                (Integer) rs.getObject("x"),
                (Integer) rs.getObject("y"),
                (Integer) rs.getObject("z"));
    }

    private static void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null)
            ps.setNull(index, Types.VARCHAR);
        else
            ps.setString(index, value.toString());
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null)
            ps.setNull(index, Types.VARCHAR);
        else
            ps.setString(index, value);
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null)
            ps.setNull(index, Types.INTEGER);
        else
            ps.setInt(index, value);
    }

    private static UUID parseNullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
