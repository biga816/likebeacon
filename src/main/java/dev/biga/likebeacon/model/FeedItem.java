package dev.biga.likebeacon.model;

import java.util.UUID;
import java.util.Objects;

public record FeedItem(
                String itemId,
                String serverId,
                String displayCode,
                long createdAt,
                FeedItemType type,
                UUID authorUuid,
                UUID initiatorUuid,
                String bodyText,
                String world,
                Integer x,
                Integer y,
                Integer z) {

    public FeedItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(displayCode, "displayCode");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(authorUuid, "authorUuid");
        Objects.requireNonNull(bodyText, "bodyText");
        if (type == FeedItemType.DIRECT && initiatorUuid == null) {
            throw new IllegalArgumentException("DIRECT feed items require an initiator");
        }
        if (type == FeedItemType.CHAT && initiatorUuid != null) {
            throw new IllegalArgumentException("CHAT feed items must not have an initiator");
        }
    }

    public static FeedItem direct(String itemId, String serverId, String displayCode, long createdAt,
            UUID authorUuid, UUID initiatorUuid, String bodyText,
            String world, Integer x, Integer y, Integer z) {
        return new FeedItem(itemId, serverId, displayCode, createdAt, FeedItemType.DIRECT,
                authorUuid, initiatorUuid, bodyText, world, x, y, z);
    }

    public static FeedItem chat(String itemId, String serverId, String displayCode, long createdAt,
            UUID authorUuid, String bodyText, String world, Integer x, Integer y, Integer z) {
        return new FeedItem(itemId, serverId, displayCode, createdAt, FeedItemType.CHAT,
                authorUuid, null, bodyText, world, x, y, z);
    }
}
