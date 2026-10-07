package dev.biga.likebeacon.service.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatTextSanitizerTest {

    @Test
    void leavesTextAtConfiguredLimitUnchanged() {
        ChatTextSanitizer sanitizer = new ChatTextSanitizer(100);
        String text = "a".repeat(100);

        assertEquals(text, sanitizer.sanitize(text));
    }

    @Test
    void truncatesToConfiguredCharacterLengthIncludingEllipsis() {
        ChatTextSanitizer sanitizer = new ChatTextSanitizer(100);

        String result = sanitizer.sanitize("a".repeat(100) + "b");

        assertEquals("a".repeat(99) + "…", result);
        assertEquals(100, result.codePointCount(0, result.length()));
    }

    @Test
    void doesNotSplitSupplementaryCodePointAtBoundary() {
        ChatTextSanitizer sanitizer = new ChatTextSanitizer(100);

        String result = sanitizer.sanitize("a".repeat(98) + "😀bc");

        assertEquals("a".repeat(98) + "😀…", result);
        assertTrue(hasOnlyPairedSurrogates(result));
    }

    @Test
    void doesNotSplitJoinedEmojiAtBoundary() {
        ChatTextSanitizer sanitizer = new ChatTextSanitizer(100);
        String family = "👨‍👩‍👧‍👦";

        String result = sanitizer.sanitize("a".repeat(98) + family + "bc");

        assertEquals("a".repeat(98) + family + "…", result);
    }

    @Test
    void doesNotSplitCombiningSequenceAtBoundary() {
        ChatTextSanitizer sanitizer = new ChatTextSanitizer(100);
        String combinedCharacter = "e\u0301";

        String result = sanitizer.sanitize("a".repeat(98) + combinedCharacter + "bc");

        assertEquals("a".repeat(98) + combinedCharacter + "…", result);
    }

    @Test
    void replacesLineBreakingControlsWithSpaces() {
        ChatTextSanitizer sanitizer = new ChatTextSanitizer(100);

        assertEquals("a b c d e f", sanitizer.sanitize("a\nb\rc\td\0e\u2028f"));
    }

    @Test
    void clampsConfiguredLengthToSafeRange() {
        assertEquals(1, new ChatTextSanitizer(0).storedLength());
        assertEquals(100, new ChatTextSanitizer(1_000).storedLength());
        assertEquals("…", new ChatTextSanitizer(0).sanitize("too long"));
    }

    private static boolean hasOnlyPairedSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }
}
