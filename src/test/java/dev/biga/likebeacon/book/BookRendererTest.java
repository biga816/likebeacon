package dev.biga.likebeacon.book;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.biga.likebeacon.model.FeedItem;
import dev.biga.likebeacon.model.FeedItemType;
import dev.biga.likebeacon.model.ItemRankingEntry;
import dev.biga.likebeacon.util.PlayerTranslator;
import net.kyori.adventure.text.Component;

class BookRendererTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID INITIATOR = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final FeedItem CHAT_ITEM = FeedItem.chat(
            "chat", "test", "ABCD", 1L, AUTHOR, "Found a village", null, null, null, null);
    private static final FeedItem DIRECT_ITEM = FeedItem.direct(
            "direct", "test", "EFGH", 2L, AUTHOR, INITIATOR, "Nice build", null, null, null, null);
    private static final ItemRankingEntry CHAT_RANKING_ENTRY = new ItemRankingEntry(
            "chat", "ABCD", 1L, FeedItemType.CHAT, null, AUTHOR, "Found a village", 2L);
    private static final ItemRankingEntry DIRECT_RANKING_ENTRY = new ItemRankingEntry(
            "direct", "EFGH", 2L, FeedItemType.DIRECT, INITIATOR, AUTHOR, "Nice build", 1L);
    private static final Map<UUID, String> PLAYER_NAMES = Map.of(AUTHOR, "Bob", INITIATOR, "Alice");

    @Test
    void feedRendersChatItemWithImmutablePlayerNames() {
        List<Component> pages = new LikeFeedBookRenderer().buildPages(
                List.of(CHAT_ITEM, DIRECT_ITEM), Map.of("chat", 2L, "direct", 1L), Set.of(), PLAYER_NAMES,
                VIEWER, 4, translator());

        assertEquals(1, pages.size());
    }

    @Test
    void mineRendersChatItemsWithImmutablePlayerNames() {
        List<Component> pages = new LikeMineBookRenderer().buildPages(
                null, List.of(CHAT_RANKING_ENTRY, DIRECT_RANKING_ENTRY),
                List.of(CHAT_ITEM), List.of(DIRECT_ITEM),
                Map.of("chat", 2L, "direct", 1L), PLAYER_NAMES, VIEWER, translator());

        assertEquals(3, pages.size());
    }

    @Test
    void rankingRendersChatItemWithImmutablePlayerNames() {
        List<Component> pages = new LikeRankingBookRenderer().buildPages(
                List.of(), List.of(), List.of(CHAT_RANKING_ENTRY, DIRECT_RANKING_ENTRY), VIEWER,
                Set.of(), PLAYER_NAMES, translator());

        assertEquals(3, pages.size());
    }

    private static PlayerTranslator translator() {
        try {
            Constructor<PlayerTranslator> constructor = PlayerTranslator.class.getDeclaredConstructor(Locale.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Locale.US);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to construct test translator", e);
        }
    }
}
