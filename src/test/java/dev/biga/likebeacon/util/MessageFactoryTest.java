package dev.biga.likebeacon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

class MessageFactoryTest {

    private static final I18nService I18N = new I18nService();

    @BeforeAll
    static void initializeTranslations() {
        I18N.initialize(MessageFactoryTest.class.getClassLoader());
    }

    @AfterAll
    static void closeTranslations() {
        I18N.close();
    }

    @Test
    void japaneseNotificationUsesNonBreakingSpacesThroughoutRenderedMessage() {
        Component message = Component.text("[LIKE] ").append(Component.translatable(
                "likebeacon.reaction.chat.received",
                Component.text("Steve"),
                Component.text("Found a diamond!"),
                Component.text("1")));

        Component result = MessageFactory.applyJapaneseWrapping(message, Locale.JAPAN);

        assertEquals(
                "[LIKE]\u00A0Steveがあなたの発言「Found\u00A0a\u00A0diamond!」にいいねしました。（合計1件）",
                plainText(result));
    }

    @Test
    void nonJapaneseNotificationKeepsNormalSpacesAndDeferredTranslation() {
        Component message = Component.translatable(
                "likebeacon.reaction.chat.received",
                Component.text("Steve"),
                Component.text("Found a diamond!"),
                Component.text("1"));

        Component result = MessageFactory.applyJapaneseWrapping(message, Locale.US);

        assertSame(message, result);
    }

    private static String plainText(Component component) {
        StringBuilder result = new StringBuilder();
        appendPlainText(component, result);
        return result.toString();
    }

    private static void appendPlainText(Component component, StringBuilder result) {
        if (component instanceof TextComponent text) {
            result.append(text.content());
        }
        component.children().forEach(child -> appendPlainText(child, result));
    }
}
