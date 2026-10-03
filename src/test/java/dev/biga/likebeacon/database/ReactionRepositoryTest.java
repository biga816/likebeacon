package dev.biga.likebeacon.database;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.Reaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private FeedItemRepository feedItemRepository;
    private ReactionRepository reactionRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.toFile());
        databaseManager.initialize();
        feedItemRepository = new FeedItemRepository(databaseManager);
        reactionRepository = new ReactionRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void rejectsDuplicateReactorForSameItem() throws Exception {
        UUID authorUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID reactorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        FeedItem item = new FeedItem(
                "duplicate-item", "test", "ABCD", 1L, "CHAT", authorUuid, null,
                "body", null, null, null, null);
        databaseManager.executeInTransaction(conn -> {
            feedItemRepository.save(conn, item);
            reactionRepository.save(conn, reaction("reaction-1", item.itemId(), reactorUuid, authorUuid));
        });

        assertThrows(SQLException.class, () -> databaseManager.executeInTransaction(conn ->
                reactionRepository.save(conn,
                        reaction("reaction-2", item.itemId(), reactorUuid, authorUuid))));
        assertTrue(reactionRepository.exists(item.itemId(), reactorUuid));
    }

    private static Reaction reaction(String reactionId, String itemId, UUID reactorUuid, UUID authorUuid) {
        return new Reaction(reactionId, "test", 1L, itemId, reactorUuid, authorUuid, "LIKE");
    }
}
