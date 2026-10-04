package dev.biga.likebeacon.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.biga.likebeacon.model.FeedItem;

class FeedItemRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private FeedItemRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.toFile());
        databaseManager.initialize();
        repository = new FeedItemRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void queriesRecentItemsByRecipientOrInitiator() throws Exception {
        UUID recipient = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherRecipient = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID initiator = UUID.fromString("00000000-0000-0000-0000-000000000003");
        databaseManager.executeInTransaction(connection -> {
            repository.save(connection, FeedItem.direct(
                    "direct-new", "test", "AAAA", 3L, recipient, initiator,
                    "new", null, null, null, null));
            repository.save(connection, FeedItem.chat(
                    "chat", "test", "BBBB", 2L, recipient,
                    "chat", null, null, null, null));
            repository.save(connection, FeedItem.direct(
                    "direct-old", "test", "CCCC", 1L, otherRecipient, initiator,
                    "old", null, null, null, null));
            repository.save(connection, FeedItem.chat(
                    "other-server", "other", "DDDD", 4L, recipient,
                    "other", null, null, null, null));
        });

        List<String> received = repository.getRecentItemsReceivedBy("test", recipient, 10).stream()
                .map(FeedItem::itemId)
                .toList();
        List<String> initiated = repository.getRecentItemsInitiatedBy("test", initiator, 10).stream()
                .map(FeedItem::itemId)
                .toList();

        assertEquals(List.of("direct-new", "chat"), received);
        assertEquals(List.of("direct-new", "direct-old"), initiated);
    }
}
