package dev.biga.likebeacon.service.milestone;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Normalized milestone settings read from {@code notifications.milestones}. */
public record MilestoneConfig(
        boolean enabled,
        long minIntervalMillis,
        List<Long> itemThresholds,
        long directMinThreshold,
        long maxItemAgeMillis,
        List<Long> playerThresholds) {

    private static final long MIN_THRESHOLD = 2L;

    public MilestoneConfig {
        itemThresholds = List.copyOf(itemThresholds);
        playerThresholds = List.copyOf(playerThresholds);
    }

    /**
     * Builds a configuration from raw values, dropping invalid entries.
     *
     * @param rawItemThresholds   values of {@code item.thresholds}; non-integers and
     *                            values below 2 are ignored
     * @param rawPlayerThresholds values of {@code player.thresholds}, validated the
     *                            same way
     * @param warn                receives one message per ignored or clamped value
     */
    public static MilestoneConfig of(boolean enabled, long minIntervalSeconds,
            List<?> rawItemThresholds, long directMinThreshold, long maxItemAgeHours,
            List<?> rawPlayerThresholds, Consumer<String> warn) {
        return new MilestoneConfig(
                enabled,
                nonNegative("minIntervalSeconds", minIntervalSeconds, warn) * 1_000L,
                normalizeThresholds("item.thresholds", rawItemThresholds, warn),
                nonNegative("item.directMinThreshold", directMinThreshold, warn),
                nonNegative("item.maxItemAgeHours", maxItemAgeHours, warn) * 3_600_000L,
                normalizeThresholds("player.thresholds", rawPlayerThresholds, warn));
    }

    static List<Long> normalizeThresholds(String name, List<?> raw, Consumer<String> warn) {
        TreeSet<Long> thresholds = new TreeSet<>();
        for (Object value : raw) {
            Long threshold = toLong(value);
            if (threshold == null || threshold < MIN_THRESHOLD) {
                warn.accept("Ignoring invalid milestone value in " + name + ": " + value
                        + " (must be an integer of at least " + MIN_THRESHOLD + ")");
                continue;
            }
            thresholds.add(threshold);
        }
        return new ArrayList<>(thresholds);
    }

    private static Long toLong(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return ((Number) value).longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static long nonNegative(String name, long value, Consumer<String> warn) {
        if (value >= 0L)
            return value;
        warn.accept("Milestone setting " + name + " must not be negative; using 0");
        return 0L;
    }
}
