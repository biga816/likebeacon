package dev.biga.likebeacon.model;

import java.util.Locale;

/** The two valid origins of a feed item. */
public enum FeedItemType {
    DIRECT,
    CHAT;

    public String databaseValue() {
        return name();
    }

    public static FeedItemType fromDatabaseValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Feed item type must not be null");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unknown feed item type: " + value, failure);
        }
    }
}
