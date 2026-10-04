package dev.biga.likebeacon.service.feed;

import dev.biga.likebeacon.database.FeedItemRepository;
import dev.biga.likebeacon.model.FeedItem;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service that manages an in-memory buffer of recent items
 * and tracks each player's last seen item ID.
 */
public class RecentService {
    private static final Logger log = Logger.getLogger(RecentService.class.getName());

    private final int bufferSize;
    // Stored in ascending order; getRecent() reverses to return newest first
    private final ArrayDeque<FeedItem> recentBuffer;
    // The item ID last seen (or eligible for reaction) per player
    private final Map<UUID, String> lastSeenItemId = new ConcurrentHashMap<>();

    /**
     * Constructs a RecentService.
     *
     * @param config plugin configuration; reads buffer size from
     *               {@code recent.bufferSize}.
     *               Defaults to 100 entries.
     */
    public RecentService(FileConfiguration config) {
        this.bufferSize = config.getInt("recent.bufferSize", 100);
        this.recentBuffer = new ArrayDeque<>(bufferSize);
        log.fine("RecentService initialized: bufferSize=" + bufferSize);
    }

    /**
     * Loads recent items from the database into the buffer on startup.
     * Data returned by the DB (created_at DESC) is reversed to ascending order
     * before storing.
     *
     * @param repo     the item repository
     * @param serverId the server ID used to scope the query
     * @throws SQLException if a database operation fails
     */
    public void loadFromDb(FeedItemRepository repo, String serverId) throws SQLException {
        List<FeedItem> recent = repo.findRecent(serverId, bufferSize);
        // findRecent returns DESC order; reverse to ascending for the buffer
        List<FeedItem> ascending = new ArrayList<>(recent);
        Collections.reverse(ascending);
        synchronized (this) {
            recentBuffer.clear();
            for (FeedItem item : ascending) {
                recentBuffer.addLast(item);
            }
        }
        log.fine("RecentService loaded " + ascending.size() + " items from DB");
    }

    /**
     * Adds a new item to the buffer.
     * If the buffer is full, the oldest entry is evicted.
     *
     * @param item the item to add
     */
    public synchronized void add(FeedItem item) {
        if (recentBuffer.size() >= bufferSize) {
            recentBuffer.pollFirst();
        }
        recentBuffer.addLast(item);
    }

    /**
     * Returns up to N recent items in created_at ASC order (oldest first).
     *
     * @param limit maximum number of entries to return
     * @return list of items, oldest first
     */
    public synchronized List<FeedItem> getRecent(int limit) {
        List<FeedItem> result = new ArrayList<>(limit);
        Iterator<FeedItem> it = recentBuffer.descendingIterator();
        for (int i = 0; i < limit && it.hasNext(); i++) {
            result.add(it.next());
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Updates the last seen item ID for the given player.
     *
     * @param playerUuid the player's UUID
     * @param itemId     the item ID last seen by the player
     */
    public void updateLastSeen(UUID playerUuid, String itemId) {
        lastSeenItemId.put(playerUuid, itemId);
    }

    /**
     * Returns the last seen item ID for the given player.
     *
     * @param playerUuid the player's UUID
     * @return an Optional containing the last seen item ID, or empty if not
     *         set
     */
    public Optional<String> getLastSeenItemId(UUID playerUuid) {
        return Optional.ofNullable(lastSeenItemId.get(playerUuid));
    }

    /**
     * Returns the item with the given ID from the in-memory buffer.
     *
     * @param itemId the item ID to look up
     * @return an Optional containing the matching item, or empty if not found
     */
    public synchronized Optional<FeedItem> findById(String itemId) {
        return recentBuffer.stream()
                .filter(b -> b.itemId().equals(itemId))
                .findFirst();
    }

    /**
     * Returns up to {@code limit} display codes from recent items, newest first.
     * Used for tab completion suggestions.
     *
     * @param limit maximum number of codes to return
     * @return list of display codes (without the {@code #} prefix)
     */
    public synchronized List<String> getRecentDisplayCodes(int limit) {
        List<String> codes = new ArrayList<>(limit);
        Iterator<FeedItem> it = recentBuffer.descendingIterator();
        for (int i = 0; i < limit && it.hasNext(); i++) {
            codes.add(it.next().displayCode());
        }
        return codes;
    }
}
