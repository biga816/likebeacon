package dev.biga.likebeacon.service.milestone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MilestoneConfigTest {

    @Test
    void sortsAndDeduplicatesThresholds() {
        List<String> warnings = new ArrayList<>();

        MilestoneConfig config = MilestoneConfig.of(
                true, 30L, List.of(30, 3, 10, 3), 10L, 24L, List.of(10), warnings::add);

        assertEquals(List.of(3L, 10L, 30L), config.itemThresholds());
        assertEquals(0, warnings.size());
    }

    @Test
    void ignoresInvalidThresholdsWithWarning() {
        List<String> warnings = new ArrayList<>();

        MilestoneConfig config = MilestoneConfig.of(
                true, 30L, List.of(0, -5, 1, 2, "x", 2.5, "7"), 10L, 24L, List.of(10), warnings::add);

        assertEquals(List.of(2L, 7L), config.itemThresholds());
        assertEquals(5, warnings.size());
    }

    @Test
    void normalizesPlayerThresholdsLikeItemThresholds() {
        List<String> warnings = new ArrayList<>();

        MilestoneConfig config = MilestoneConfig.of(
                true, 30L, List.of(3), 10L, 24L, List.of(500, 10, 1, 50, 10), warnings::add);

        assertEquals(List.of(10L, 50L, 500L), config.playerThresholds());
        assertEquals(1, warnings.size());
    }

    @Test
    void clampsNegativeValuesToZeroAndConvertsUnits() {
        List<String> warnings = new ArrayList<>();

        MilestoneConfig negative = MilestoneConfig.of(
                true, -1L, List.of(3), -1L, -1L, List.of(10), warnings::add);
        MilestoneConfig positive = MilestoneConfig.of(
                true, 30L, List.of(3), 10L, 24L, List.of(10), warnings::add);

        assertEquals(0L, negative.minIntervalMillis());
        assertEquals(0L, negative.directMinThreshold());
        assertEquals(0L, negative.maxItemAgeMillis());
        assertEquals(3, warnings.size());
        assertEquals(30_000L, positive.minIntervalMillis());
        assertEquals(24L * 3_600_000L, positive.maxItemAgeMillis());
    }
}
