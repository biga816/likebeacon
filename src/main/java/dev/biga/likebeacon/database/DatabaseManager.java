package dev.biga.likebeacon.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Manages the SQLite database connection and schema.
 * <p>
 * Holds a long-lived connection that is initialized on plugin startup and
 * closed on shutdown. All write access must go through
 * {@link DatabaseWriteExecutor}; this class provides a
 * {@link #executeInTransaction} helper for transactional write tasks.
 * </p>
 */
public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private final String jdbcUrl;
    private Connection connection;

    /**
     * Constructs a DatabaseManager.
     *
     * @param dataFolder the plugin's data folder; likebeacon.db will be created
     *                   inside
     *                   it
     * @throws ClassNotFoundException if the SQLite JDBC driver is not found
     */
    public DatabaseManager(File dataFolder) throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        this.jdbcUrl = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + "/likebeacon.db";
    }

    /**
     * Establishes the database connection and initializes the schema.
     * Applies required PRAGMAs and creates all tables and indexes.
     *
     * @throws SQLException if a database operation fails
     */
    public void initialize() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        LOGGER.info("SQLite database connected: " + jdbcUrl);

        try (Statement stmt = connection.createStatement()) {

            // ── PRAGMAs ────────────────────────────────────────────────────────
            // WAL mode: allows concurrent reads while a single writer is active.
            stmt.execute("PRAGMA journal_mode=WAL");
            // Busy timeout: secondary safeguard in case of unexpected contention.
            stmt.execute("PRAGMA busy_timeout=5000");
            // Enforce foreign-key constraints.
            stmt.execute("PRAGMA foreign_keys=ON");

            // ── Core tables ────────────────────────────────────────────────────

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS feed_items (
                        item_id        TEXT PRIMARY KEY,
                        server_id      TEXT NOT NULL,
                        display_code   TEXT NOT NULL,
                        created_at     INTEGER NOT NULL,
                        item_type      TEXT NOT NULL,
                        author_uuid    TEXT NOT NULL,
                        initiator_uuid TEXT,
                        body_text      TEXT NOT NULL,
                        world          TEXT,
                        x              INTEGER,
                        y              INTEGER,
                        z              INTEGER
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS reactions (
                        reaction_id     TEXT PRIMARY KEY,
                        server_id    TEXT NOT NULL,
                        created_at   INTEGER NOT NULL,
                        item_id TEXT NOT NULL,
                        reactor_uuid  TEXT NOT NULL,
                        author_uuid   TEXT NOT NULL,
                        reaction_type TEXT NOT NULL DEFAULT 'LIKE',
                        FOREIGN KEY(item_id) REFERENCES feed_items(item_id),
                        UNIQUE(item_id, reactor_uuid)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sender_daily (
                        date              TEXT NOT NULL,
                        server_id         TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        action_type TEXT NOT NULL,
                        count       INTEGER NOT NULL,
                        PRIMARY KEY(date, server_id, player_uuid, action_type)
                    )
                    """);

            // ── Aggregation tables ─────────────────────────────────────────────

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        server_id      TEXT NOT NULL,
                        player_uuid    TEXT NOT NULL,
                        player_name    TEXT NOT NULL,
                        received_count INTEGER NOT NULL DEFAULT 0,
                        sent_count     INTEGER NOT NULL DEFAULT 0,
                        reacted_count  INTEGER NOT NULL DEFAULT 0,
                        updated_at     INTEGER NOT NULL,
                        PRIMARY KEY(server_id, player_uuid)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS item_stats (
                        item_id   TEXT PRIMARY KEY,
                        server_id      TEXT NOT NULL,
                        reaction_count INTEGER NOT NULL DEFAULT 0,
                        updated_at     INTEGER NOT NULL,
                        FOREIGN KEY(item_id)
                            REFERENCES feed_items(item_id)
                            ON DELETE CASCADE
                    )
                    """);

            // ── Indexes on core tables ─────────────────────────────────────────

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_feed_items_server_created_at
                    ON feed_items(server_id, created_at DESC)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_feed_items_server_display_code_created_at
                    ON feed_items(server_id, display_code, created_at DESC)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_feed_items_server_author
                    ON feed_items(server_id, author_uuid, created_at DESC)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_reactions_server_item_reactor
                    ON reactions(server_id, item_id, reactor_uuid)
                    """);

            // ── Indexes on aggregation tables ──────────────────────────────────

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_stats_server_received
                    ON player_stats(server_id, received_count DESC)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_stats_server_sent
                    ON player_stats(server_id, sent_count DESC)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_stats_server_reacted
                    ON player_stats(server_id, reacted_count DESC)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_item_stats_server_reaction
                    ON item_stats(server_id, reaction_count DESC)
                    """);
        }

        LOGGER.info("Database schema initialized successfully.");
    }

    /**
     * Returns the current database connection.
     * <p>
     * Write operations must be invoked from within a task submitted to
     * {@link DatabaseWriteExecutor} to guarantee serialization.
     * </p>
     *
     * @return the SQLite connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Executes a transactional write task on the current connection.
     * <p>
     * Sets {@code autoCommit=false}, runs the task, then commits. If the task
     * throws, the transaction is rolled back and the exception is re-thrown.
     * This method must be called from within a task submitted to
     * {@link DatabaseWriteExecutor}.
     * </p>
     *
     * @param task the transactional task to execute
     * @throws SQLException if a database error occurs or the task throws
     */
    public void executeInTransaction(TransactionTask task) throws SQLException {
        executeInTransactionWithResult(conn -> {
            task.execute(conn);
            return null;
        });
    }

    /**
     * Executes a transactional write task and returns the value produced by it.
     *
     * @param task the transactional task to execute
     * @param <T>  the result type
     * @return the value produced after all writes in the task have succeeded
     * @throws SQLException if a database error occurs or the task throws
     */
    public <T> T executeInTransactionWithResult(TransactionFunction<T> task) throws SQLException {
        connection.setAutoCommit(false);
        Throwable failure = null;
        try {
            T result = task.execute(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException | Error e) {
            failure = e;
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException restoreFailure) {
                if (failure != null) {
                    failure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }

    /**
     * Closes the database connection.
     * Must be called when the plugin is disabled.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("Database connection closed.");
            } catch (SQLException e) {
                LOGGER.warning("Failed to close database connection: " + e.getMessage());
            }
        }
    }

    /** Functional interface for a transactional SQL task. */
    @FunctionalInterface
    public interface TransactionTask {
        void execute(Connection conn) throws SQLException;
    }

    /** Transactional SQL task that produces a result. */
    @FunctionalInterface
    public interface TransactionFunction<T> {
        T execute(Connection conn) throws SQLException;
    }
}
