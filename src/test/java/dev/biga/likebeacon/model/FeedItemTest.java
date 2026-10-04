package dev.biga.likebeacon.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FeedItemTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INITIATOR = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void factoriesCreateOnlyValidTypeSpecificStates() {
        FeedItem direct = FeedItem.direct(
                "direct", "test", "ABCD", 1L, AUTHOR, INITIATOR, "reason", null, null, null, null);
        FeedItem chat = FeedItem.chat(
                "chat", "test", "EFGH", 2L, AUTHOR, "message", null, null, null, null);

        assertEquals(FeedItemType.DIRECT, direct.type());
        assertEquals(INITIATOR, direct.initiatorUuid());
        assertEquals(FeedItemType.CHAT, chat.type());
        assertNull(chat.initiatorUuid());
    }

    @Test
    void directItemRequiresInitiator() {
        assertThrows(IllegalArgumentException.class, () -> FeedItem.direct(
                "direct", "test", "ABCD", 1L, AUTHOR, null, "reason", null, null, null, null));
    }

    @Test
    void chatItemRejectsInitiator() {
        assertThrows(IllegalArgumentException.class, () -> new FeedItem(
                "chat", "test", "ABCD", 1L, FeedItemType.CHAT,
                AUTHOR, INITIATOR, "message", null, null, null, null));
    }

    @Test
    void requiredBodyMustNotBeNull() {
        assertThrows(NullPointerException.class, () -> FeedItem.chat(
                "chat", "test", "ABCD", 1L, AUTHOR, null, null, null, null, null));
    }

    @Test
    void databaseTypeConversionRejectsUnknownValues() {
        assertEquals(FeedItemType.CHAT, FeedItemType.fromDatabaseValue("CHAT"));
        assertThrows(IllegalArgumentException.class,
                () -> FeedItemType.fromDatabaseValue("UNKNOWN"));
    }
}
