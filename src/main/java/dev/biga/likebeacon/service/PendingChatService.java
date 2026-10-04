package dev.biga.likebeacon.service;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.PendingChat;
import dev.biga.likebeacon.util.DisplayCodeGenerator;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.function.Function;

/** Thread-safe, memory-only buffer for chat messages awaiting promotion. */
public class PendingChatService {

    private final int bufferSize;
    private final ArrayDeque<String> order = new ArrayDeque<>();
    private final Map<String, PendingChat> pendingByCode = new HashMap<>();
    private final Map<String, CompletableFuture<FeedItem>> inFlightByCode = new HashMap<>();
    private final Set<String> externalReservations = new HashSet<>();
    private final Map<String, FeedItem> completedByCode = new HashMap<>();
    private final ArrayDeque<String> completedOrder = new ArrayDeque<>();

    public PendingChatService(int bufferSize) {
        this.bufferSize = Math.max(1, bufferSize);
    }

    public synchronized void put(PendingChat pending) {
        pendingByCode.put(pending.displayCode(), pending);
        order.remove(pending.displayCode());
        order.addLast(pending.displayCode());
        while (order.size() > bufferSize) {
            pendingByCode.remove(order.removeFirst());
        }
    }

    /** Generates and reserves a code atomically with insertion into the buffer. */
    public synchronized PendingChat putGenerated(Connection connection, DisplayCodeGenerator generator, String serverId,
            Function<String, PendingChat> factory) throws SQLException {
        String code = generator.generateUnique(connection, serverId, reservedCodes());
        PendingChat pending = factory.apply(code);
        put(pending);
        return pending;
    }

    public synchronized String reserveDisplayCode(Connection connection, DisplayCodeGenerator generator, String serverId)
            throws SQLException {
        String code = generator.generateUnique(connection, serverId, reservedCodes());
        externalReservations.add(code);
        return code;
    }

    public synchronized void releaseDisplayCode(String displayCode) {
        externalReservations.remove(displayCode);
    }

    /** Atomically claims a pending message or joins an existing promotion. */
    public synchronized Optional<ClaimResult> claim(String displayCode) {
        CompletableFuture<FeedItem> existing = inFlightByCode.get(displayCode);
        if (existing != null) {
            return Optional.of(new Joined(existing));
        }
        FeedItem completed = completedByCode.get(displayCode);
        if (completed != null) {
            return Optional.of(new Joined(CompletableFuture.completedFuture(completed)));
        }
        PendingChat pending = pendingByCode.remove(displayCode);
        if (pending == null) {
            return Optional.empty();
        }
        order.remove(displayCode);
        CompletableFuture<FeedItem> completion = new CompletableFuture<>();
        inFlightByCode.put(displayCode, completion);
        return Optional.of(new Owner(pending, completion));
    }

    public synchronized void completePromotion(String displayCode, FeedItem item) {
        CompletableFuture<FeedItem> completion = inFlightByCode.get(displayCode);
        if (completion != null)
            completion.complete(item);
        inFlightByCode.remove(displayCode);
        completedByCode.put(displayCode, item);
        completedOrder.addLast(displayCode);
        while (completedOrder.size() > bufferSize) {
            completedByCode.remove(completedOrder.removeFirst());
        }
    }

    public synchronized void failPromotion(String displayCode, Throwable failure) {
        CompletableFuture<FeedItem> completion = inFlightByCode.remove(displayCode);
        if (completion != null)
            completion.completeExceptionally(failure);
    }

    /** Codes that must not be allocated to another pending chat. */
    public synchronized Set<String> reservedCodes() {
        Set<String> result = new HashSet<>(pendingByCode.keySet());
        result.addAll(inFlightByCode.keySet());
        result.addAll(externalReservations);
        return Set.copyOf(result);
    }

    public sealed interface ClaimResult permits Owner, Joined {
        CompletableFuture<FeedItem> completion();
    }

    public record Owner(PendingChat pending, CompletableFuture<FeedItem> completion) implements ClaimResult {
    }

    public record Joined(CompletableFuture<FeedItem> completion) implements ClaimResult {
    }
}
