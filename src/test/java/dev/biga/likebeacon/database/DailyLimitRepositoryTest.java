package dev.biga.likebeacon.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyLimitRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private DailyLimitRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.toFile());
        databaseManager.initialize();
        repository = new DailyLimitRepository();
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void incrementStopsAtLimit() throws Exception {
        UUID sender = UUID.randomUUID();

        assertTrue(increment(sender, 2));
        assertTrue(increment(sender, 2));
        assertFalse(increment(sender, 2));
    }

    @Test
    void incrementIsRolledBackWithEnclosingTransaction() throws Exception {
        UUID sender = UUID.randomUUID();

        assertThrows(SQLException.class, () -> databaseManager.executeInTransaction(conn -> {
            assertTrue(repository.incrementIfBelowLimit(conn, "test", "2026-10-04", sender, 1));
            throw new SQLException("later write failed");
        }));

        assertTrue(increment(sender, 1));
    }

    private boolean increment(UUID sender, int limit) throws SQLException {
        return databaseManager.executeInTransactionWithResult(conn ->
                repository.incrementIfBelowLimit(conn, "test", "2026-10-04", sender, limit));
    }
}
