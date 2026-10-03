package dev.biga.likebeacon.database;

import dev.biga.likebeacon.model.FeedItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemStatsRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private FeedItemRepository feedItemRepository;
    private ItemStatsRepository itemStatsRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.toFile());
        databaseManager.initialize();
        feedItemRepository = new FeedItemRepository(databaseManager);
        itemStatsRepository = new ItemStatsRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void incrementReturnsPersistedReactionCount() throws Exception {
        FeedItem item = item("item-stats");
        databaseManager.executeInTransaction(conn -> {
            feedItemRepository.save(conn, item);
            itemStatsRepository.insertNew(conn, "test", item.itemId(), 1L);
        });

        long second = databaseManager.executeInTransactionWithResult(conn ->
                itemStatsRepository.incrementReactionCount(conn, "test", item.itemId(), 2L));
        long third = databaseManager.executeInTransactionWithResult(conn ->
                itemStatsRepository.incrementReactionCount(conn, "test", item.itemId(), 3L));

        assertEquals(2L, second);
        assertEquals(3L, third);
        assertEquals(3L, itemStatsRepository.getStats(item.itemId()).orElseThrow().reactionCount());
    }

    private static FeedItem item(String itemId) {
        return new FeedItem(
                itemId,
                "test",
                "ABCD",
                1L,
                "CHAT",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                null,
                "body",
                null,
                null,
                null,
                null);
    }
}
