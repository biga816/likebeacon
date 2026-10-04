package dev.biga.likebeacon.database;

/** A compile-time-limited player statistic that can be incremented. */
public enum PlayerStatType {
    RECEIVED("received_count", 1, 0, 0),
    SENT("sent_count", 0, 1, 0),
    REACTED("reacted_count", 0, 0, 1);

    private final String column;
    private final int initialReceived;
    private final int initialSent;
    private final int initialReacted;

    PlayerStatType(String column, int initialReceived, int initialSent, int initialReacted) {
        this.column = column;
        this.initialReceived = initialReceived;
        this.initialSent = initialSent;
        this.initialReacted = initialReacted;
    }

    String column() {
        return column;
    }

    int initialReceived() {
        return initialReceived;
    }

    int initialSent() {
        return initialSent;
    }

    int initialReacted() {
        return initialReacted;
    }
}
