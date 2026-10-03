package dev.biga.likebeacon.service;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.PendingChat;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingChatServiceTest {

    @Test
    void secondClaimJoinsOwnerPromotionAndReceivesCompletedItem() {
        PendingChatService service = new PendingChatService(3);
        PendingChat pending = pending("ABCD");
        FeedItem promoted = promotedItem("ABCD");
        service.put(pending);

        PendingChatService.Claim owner = service.claim("ABCD").orElseThrow();
        PendingChatService.Claim joined = service.claim("ABCD").orElseThrow();

        assertTrue(owner.owner());
        assertSame(pending, owner.pending());
        assertFalse(joined.owner());
        service.completePromotion("ABCD", promoted);
        assertSame(promoted, owner.completion().join());
        assertSame(promoted, joined.completion().join());

        PendingChatService.Claim completed = service.claim("ABCD").orElseThrow();
        assertFalse(completed.owner());
        assertSame(promoted, completed.completion().join());
    }

    @Test
    void failedPromotionCompletesAllJoinedClaimsExceptionally() {
        PendingChatService service = new PendingChatService(3);
        service.put(pending("EFGH"));
        PendingChatService.Claim owner = service.claim("EFGH").orElseThrow();
        PendingChatService.Claim joined = service.claim("EFGH").orElseThrow();
        IllegalStateException failure = new IllegalStateException("expected");

        service.failPromotion("EFGH", failure);

        CompletionException ownerFailure = assertThrows(
                CompletionException.class, () -> owner.completion().join());
        CompletionException joinedFailure = assertThrows(
                CompletionException.class, () -> joined.completion().join());
        assertSame(failure, ownerFailure.getCause());
        assertSame(failure, joinedFailure.getCause());
        assertTrue(service.claim("EFGH").isEmpty());
    }

    @Test
    void evictsOldestPendingChatAtCapacity() {
        PendingChatService service = new PendingChatService(2);
        service.put(pending("AAAA"));
        service.put(pending("BBBB"));
        service.put(pending("CCCC"));

        assertTrue(service.claim("AAAA").isEmpty());
        assertEquals("BBBB", service.claim("BBBB").orElseThrow().pending().displayCode());
        assertEquals("CCCC", service.claim("CCCC").orElseThrow().pending().displayCode());
    }

    private static PendingChat pending(String code) {
        return new PendingChat(
                code,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "author",
                "body",
                null,
                null,
                null,
                null,
                1L);
    }

    private static FeedItem promotedItem(String code) {
        return new FeedItem(
                "item-" + code,
                "test",
                code,
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
