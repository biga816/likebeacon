package dev.biga.likebeacon.database;

import dev.biga.likebeacon.model.Reaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository for accessing the reactions table.
 */
public class ReactionRepository {

    private final DatabaseManager databaseManager;

    /**
     * Constructs an ReactionRepository.
     *
     * @param databaseManager the database connection manager
     */
    public ReactionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a like event to the database.
     *
     * @param reaction the reaction to save
     * @throws SQLException if a database operation fails
     */
    public void save(Connection conn, Reaction reaction) throws SQLException {
        String sql = """
                INSERT INTO reactions
                    (reaction_id, server_id, created_at, item_id, reactor_uuid, author_uuid, reaction_type)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reaction.reactionId());
            ps.setString(2, reaction.serverId());
            ps.setLong(3, reaction.createdAt());
            ps.setString(4, reaction.itemId());
            ps.setString(5, reaction.reactorUuid().toString());
            ps.setString(6, reaction.authorUuid().toString());
            ps.setString(7, reaction.reactionType());
            ps.executeUpdate();
        }
    }

    /**
     * Checks whether an event exists for the given item ID and sender UUID.
     * Used to prevent duplicate likes on the same item.
     *
     * @param itemId     the item ID to check
     * @param senderUuid the sender's UUID to check
     * @return true if such an event already exists
     * @throws SQLException if a database operation fails
     */
    public boolean exists(String itemId, UUID senderUuid) throws SQLException {
        String sql = "SELECT 1 FROM reactions WHERE item_id = ? AND reactor_uuid = ? LIMIT 1";
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, senderUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Returns recent items that the given player has reacted to, scoped to
     * the given server, ordered by event creation time descending.
     *
     * @param serverId   the server ID to filter by
     * @param senderUuid the reactor's UUID
     * @param limit      maximum number of results
     * @return list of event records ordered by created_at DESC
     * @throws SQLException if a database error occurs
     */
    public List<Reaction> getRecentReactionsBy(String serverId, UUID senderUuid, int limit)
            throws SQLException {
        String sql = """
                SELECT * FROM reactions
                WHERE server_id = ?
                  AND reactor_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, senderUuid.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Reaction> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new Reaction(
                            rs.getString("reaction_id"),
                            rs.getString("server_id"),
                            rs.getLong("created_at"),
                            rs.getString("item_id"),
                            UUID.fromString(rs.getString("reactor_uuid")),
                            UUID.fromString(rs.getString("author_uuid")),
                            rs.getString("reaction_type")));
                }
                return results;
            }
        }
    }

    /**
     * Returns the set of item IDs (from the given list) that the specified
     * sender has already reacted to.
     *
     * @param itemIds    the list of item IDs to check
     * @param senderUuid the sender's UUID
     * @return a set of item IDs the sender has reacted to
     * @throws SQLException if a database operation fails
     */
    public Set<String> reactedItemIds(List<String> itemIds, UUID senderUuid) throws SQLException {
        if (itemIds.isEmpty())
            return Set.of();
        String placeholders = itemIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT item_id FROM reactions WHERE item_id IN (" + placeholders
                + ") AND reactor_uuid = ?";
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < itemIds.size(); i++) {
                ps.setString(i + 1, itemIds.get(i));
            }
            ps.setString(itemIds.size() + 1, senderUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> result = new HashSet<>();
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
                return result;
            }
        }
    }
}
