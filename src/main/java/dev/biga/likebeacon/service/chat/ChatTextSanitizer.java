package dev.biga.likebeacon.service.chat;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.Objects;

/** Normalizes and bounds chat text before it enters the Like feed. */
public final class ChatTextSanitizer {

    public static final int MAX_STORED_LENGTH = 100;

    private final int storedLength;

    public ChatTextSanitizer(int configuredLength) {
        this.storedLength = Math.clamp(configuredLength, 1, MAX_STORED_LENGTH);
    }

    /**
     * Replaces line-breaking control characters with spaces and truncates by
     * extended grapheme cluster so user-perceived characters such as joined emoji
     * and combining sequences remain intact.
     */
    public String sanitize(String text) {
        Objects.requireNonNull(text, "text");

        StringBuilder normalized = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> normalized.appendCodePoint(
                isLineBreakingControl(codePoint) ? ' ' : codePoint));

        String normalizedText = normalized.toString();
        BreakIterator boundaries = BreakIterator.getCharacterInstance(Locale.ROOT);
        boundaries.setText(normalizedText);

        int contentEnd = boundaries.first();
        for (int count = 0; count < storedLength; count++) {
            int next = boundaries.next();
            if (next == BreakIterator.DONE) {
                return normalizedText;
            }
            if (count < storedLength - 1) {
                contentEnd = next;
            }
        }

        if (boundaries.next() == BreakIterator.DONE) {
            return normalizedText;
        }
        return normalizedText.substring(0, contentEnd) + "…";
    }

    int storedLength() {
        return storedLength;
    }

    private static boolean isLineBreakingControl(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
