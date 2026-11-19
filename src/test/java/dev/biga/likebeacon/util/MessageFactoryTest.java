package dev.biga.likebeacon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.translation.GlobalTranslator;

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

    @Test
    void itemMilestoneTranslationsRenderInBothLocales() {
        Component message = Component.translatable(
                "likebeacon.milestone.item.chat.broadcast",
                Component.text("Steve"),
                Component.text("Found a diamond!"),
                Component.text("10"));

        assertEquals("★ Steve's message \"Found a diamond!\" reached 10 likes!",
                plainText(GlobalTranslator.render(message, Locale.US)));
        assertEquals("★ Steve の発言「Found a diamond!」が 10件のいいねを集めました！",
                plainText(GlobalTranslator.render(message, Locale.JAPAN)));
    }

    @Test
    void playerMilestoneTranslationsRenderInBothLocales() {
        Component message = Component.translatable(
                "likebeacon.milestone.player.broadcast",
                Component.text("Steve"),
                Component.text("50"));

        assertEquals("★ Steve has received 50 likes in total!",
                plainText(GlobalTranslator.render(message, Locale.US)));
        assertEquals("★ Steve が累計 50件のいいねを受け取りました！",
                plainText(GlobalTranslator.render(message, Locale.JAPAN)));
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
