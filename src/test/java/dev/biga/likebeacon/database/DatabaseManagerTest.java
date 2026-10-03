package dev.biga.likebeacon.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.toFile());
        databaseManager.initialize();
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void commitsSuccessfulTransaction() throws SQLException {
        databaseManager.executeInTransaction(conn -> {
            insertFeedItem(conn, "committed");
        });

        assertEquals(1, feedItemCount("committed"));
        assertTrue(databaseManager.getConnection().getAutoCommit());
    }

    @Test
    void rollsBackSqlException() throws SQLException {
        assertThrows(SQLException.class, () -> databaseManager.executeInTransaction(conn -> {
            insertFeedItem(conn, "sql-failure");
            throw new SQLException("expected");
        }));

        assertEquals(0, feedItemCount("sql-failure"));
        assertTrue(databaseManager.getConnection().getAutoCommit());
    }

    @Test
    void rollsBackRuntimeException() throws SQLException {
        assertThrows(IllegalStateException.class, () -> databaseManager.executeInTransaction(conn -> {
            insertFeedItem(conn, "runtime-failure");
            throw new IllegalStateException("expected");
        }));

        assertEquals(0, feedItemCount("runtime-failure"));
        assertTrue(databaseManager.getConnection().getAutoCommit());
    }

    @Test
    void preservesOriginalFailureWhenRollbackAlsoFails() {
        SQLException expected = new SQLException("original failure");

        SQLException thrown = assertThrows(SQLException.class,
                () -> databaseManager.executeInTransaction(conn -> {
                    conn.close();
                    throw expected;
                }));

        assertEquals(expected, thrown);
        assertTrue(thrown.getSuppressed().length >= 1);
    }

    private static void insertFeedItem(Connection conn, String itemId) throws SQLException {
        try (var statement = conn.prepareStatement("""
                INSERT INTO feed_items
                    (item_id, server_id, display_code, created_at, item_type, author_uuid, body_text)
                VALUES (?, 'test', 'ABCD', 1, 'CHAT', '00000000-0000-0000-0000-000000000001', 'body')
                """)) {
            statement.setString(1, itemId);
            statement.executeUpdate();
        }
    }

    private int feedItemCount(String itemId) throws SQLException {
        try (var statement = databaseManager.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM feed_items WHERE item_id = ?")) {
            statement.setString(1, itemId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
