package dev.biga.likebeacon.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.biga.likebeacon.model.PlayerStats;

class PlayerStatsRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private PlayerStatsRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.toFile());
        databaseManager.initialize();
        repository = new PlayerStatsRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void incrementsOnlySelectedStatisticAndUpdatesName() throws Exception {
        UUID playerUuid = UUID.randomUUID();

        increment(PlayerStatType.SENT, playerUuid, "before", 1L);
        increment(PlayerStatType.RECEIVED, playerUuid, "before", 2L);
        increment(PlayerStatType.REACTED, playerUuid, "before", 3L);
        increment(PlayerStatType.REACTED, playerUuid, "after", 4L);

        PlayerStats stats = repository.getPlayerStats("test", playerUuid).orElseThrow();
        assertEquals("after", stats.playerName());
        assertEquals(1L, stats.sentCount());
        assertEquals(1L, stats.receivedCount());
        assertEquals(2L, stats.reactedCount());
        assertEquals(4L, stats.updatedAt());
    }

    @Test
    void returnsSelectedStatisticAfterIncrement() throws Exception {
        UUID playerUuid = UUID.randomUUID();

        assertEquals(1L, increment(PlayerStatType.RECEIVED, playerUuid, "player", 1L));
        assertEquals(1L, increment(PlayerStatType.SENT, playerUuid, "player", 2L));
        assertEquals(2L, increment(PlayerStatType.RECEIVED, playerUuid, "player", 3L));
    }

    private long increment(PlayerStatType type, UUID playerUuid, String playerName, long updatedAt)
            throws Exception {
        return databaseManager.executeInTransactionWithResult(connection -> repository.incrementCount(
                connection, type, "test", playerUuid, playerName, updatedAt));
    }
}
