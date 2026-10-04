package dev.biga.likebeacon.database;

import dev.biga.likebeacon.model.PlayerStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@code player_stats} aggregation table.
 * <p>
 * Write methods accept an explicit {@link Connection} so they can participate
 * in a caller-managed transaction via
 * {@link DatabaseManager#executeInTransaction}. Read methods use the shared
 * connection from {@link DatabaseManager#getReadConnection()} and must be called
 * from {@link DatabaseReadExecutor}.
 * </p>
 */
public class PlayerStatsRepository {

    private final DatabaseManager databaseManager;

    /**
     * Constructs a PlayerStatsRepository.
     *
     * @param databaseManager the database connection manager
     */
    public PlayerStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ── Write methods (transactional, called from DatabaseWriteExecutor) ────────

    /** Increments one enum-limited statistic, creating the player row if needed. */
    public void incrementCount(Connection conn, PlayerStatType type, String serverId,
            UUID playerUuid, String playerName, long updatedAt) throws SQLException {
        String column = type.column();
        String sql = """
                INSERT INTO player_stats
                    (server_id, player_uuid, player_name, received_count, sent_count, reacted_count, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(server_id, player_uuid) DO UPDATE SET
                    %s          = %s + 1,
                    player_name = excluded.player_name,
                    updated_at  = excluded.updated_at
                """.formatted(column, column);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setInt(4, type.initialReceived());
            ps.setInt(5, type.initialSent());
            ps.setInt(6, type.initialReacted());
            ps.setLong(7, updatedAt);
            ps.executeUpdate();
        }
    }

    // ── Read methods (for /like mine and /like ranking) ──────────────────────

    /**
     * Returns the stats for a single player on the given server, or empty if no
     * record exists yet.
     *
     * @param serverId   the server ID to filter by
     * @param playerUuid the player's UUID
     * @return an Optional containing the stats, or empty
     * @throws SQLException if a database error occurs
     */
    public Optional<PlayerStats> getPlayerStats(String serverId, UUID playerUuid) throws SQLException {
        String sql = "SELECT * FROM player_stats WHERE server_id = ? AND player_uuid = ?";
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the top {@code limit} players for the given server ordered by
     * {@code received_count} descending.
     *
     * @param serverId the server ID to filter by
     * @param limit    maximum number of results
     * @return ordered list of player stats
     * @throws SQLException if a database error occurs
     */
    public List<PlayerStats> getTopReceivedPlayers(String serverId, int limit) throws SQLException {
        return queryTop(serverId, "received_count", limit);
    }

    /**
     * Returns the top {@code limit} players for the given server ordered by
     * {@code sent_count} descending.
     *
     * @param serverId the server ID to filter by
     * @param limit    maximum number of results
     * @return ordered list of player stats
     * @throws SQLException if a database error occurs
     */
    public List<PlayerStats> getTopSentPlayers(String serverId, int limit) throws SQLException {
        return queryTop(serverId, "sent_count", limit);
    }

    /**
     * Returns the top {@code limit} players for the given server ordered by
     * {@code reacted_count} descending.
     *
     * @param serverId the server ID to filter by
     * @param limit    maximum number of results
     * @return ordered list of player stats
     * @throws SQLException if a database error occurs
     */
    public List<PlayerStats> getTopReactedPlayers(String serverId, int limit) throws SQLException {
        return queryTop(serverId, "reacted_count", limit);
    }

    private List<PlayerStats> queryTop(String serverId, String column, int limit) throws SQLException {
        // column is a compile-time constant, not user input — safe to interpolate
        String sql = "SELECT * FROM player_stats WHERE server_id = ? ORDER BY " + column + " DESC LIMIT ?";
        Connection conn = databaseManager.getReadConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PlayerStats> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    private PlayerStats mapRow(ResultSet rs) throws SQLException {
        return new PlayerStats(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getLong("received_count"),
                rs.getLong("sent_count"),
                rs.getLong("reacted_count"),
                rs.getLong("updated_at"));
    }
}
