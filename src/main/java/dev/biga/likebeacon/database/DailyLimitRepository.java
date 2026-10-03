package dev.biga.likebeacon.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Repository for accessing the sender_daily table.
 * Tracks the number of direct likes sent per player per server per day.
 */
public class DailyLimitRepository {

    /**
     * Atomically increments today's direct-like count when it is below the limit.
     *
     * @param conn       the connection in the active write transaction
     * @param serverId   the server ID to scope the update
     * @param date       the target date in {@code yyyy-MM-dd} format
     * @param senderUuid the sender's UUID
     * @param limit      the maximum allowed count
     * @return {@code true} when the count was incremented; {@code false} at the limit
     * @throws SQLException if a database operation fails
     */
    public boolean incrementIfBelowLimit(Connection conn, String serverId, String date,
            UUID senderUuid, int limit) throws SQLException {
        if (limit <= 0)
            return false;
        String sql = """
                INSERT INTO sender_daily (date, server_id, player_uuid, action_type, count)
                VALUES (?, ?, ?, 'DIRECT', 1)
                ON CONFLICT(date, server_id, player_uuid, action_type) DO UPDATE SET
                    count = count + 1
                WHERE count < ?
                RETURNING count
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date);
            ps.setString(2, serverId);
            ps.setString(3, senderUuid.toString());
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
