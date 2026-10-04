package dev.biga.likebeacon.service.chat;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.PendingChat;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        PendingChatService.Owner owner = assertInstanceOf(
                PendingChatService.Owner.class, service.claim("ABCD").orElseThrow());
        PendingChatService.Joined joined = assertInstanceOf(
                PendingChatService.Joined.class, service.claim("ABCD").orElseThrow());

        assertSame(pending, owner.pending());
        service.completePromotion("ABCD", promoted);
        assertSame(promoted, owner.completion().join());
        assertSame(promoted, joined.completion().join());

        PendingChatService.Joined completed = assertInstanceOf(
                PendingChatService.Joined.class, service.claim("ABCD").orElseThrow());
        assertSame(promoted, completed.completion().join());
    }

    @Test
    void failedPromotionCompletesAllJoinedClaimsExceptionally() {
        PendingChatService service = new PendingChatService(3);
        service.put(pending("EFGH"));
        PendingChatService.Owner owner = assertInstanceOf(
                PendingChatService.Owner.class, service.claim("EFGH").orElseThrow());
        PendingChatService.Joined joined = assertInstanceOf(
                PendingChatService.Joined.class, service.claim("EFGH").orElseThrow());
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
        PendingChatService.Owner second = assertInstanceOf(
                PendingChatService.Owner.class, service.claim("BBBB").orElseThrow());
        PendingChatService.Owner third = assertInstanceOf(
                PendingChatService.Owner.class, service.claim("CCCC").orElseThrow());
        assertEquals("BBBB", second.pending().displayCode());
        assertEquals("CCCC", third.pending().displayCode());
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
        return FeedItem.chat(
                "item-" + code,
                "test",
                code,
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "body",
                null,
                null,
                null,
                null);
    }
}
