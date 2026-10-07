package dev.biga.likebeacon.listener;

import dev.biga.likebeacon.model.PendingChat;
import dev.biga.likebeacon.database.DatabaseReadExecutor;
import dev.biga.likebeacon.service.chat.ChatLikeEligibilityService;
import dev.biga.likebeacon.service.chat.ChatTextSanitizer;
import dev.biga.likebeacon.service.chat.PendingChatService;
import dev.biga.likebeacon.util.DisplayCodeGenerator;
import dev.biga.likebeacon.util.MessageFactory;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletionException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Adds a per-viewer reaction control to eligible public chat messages. */
public class ChatLikeListener implements Listener {
    private static final Logger log = Logger.getLogger(ChatLikeListener.class.getName());
    private final PendingChatService pendingChatService;
    private final DisplayCodeGenerator displayCodeGenerator;
    private final MessageFactory messageFactory;
    private final ChatLikeEligibilityService eligibilityService;
    private final String serverId;
    private final ChatTextSanitizer textSanitizer;
    private final DatabaseReadExecutor readExecutor;

    public ChatLikeListener(PendingChatService pendingChatService, DisplayCodeGenerator displayCodeGenerator,
            DatabaseReadExecutor readExecutor, MessageFactory messageFactory,
            ChatLikeEligibilityService eligibilityService,
            String serverId, int maxStoredLength) {
        this.pendingChatService = pendingChatService;
        this.displayCodeGenerator = displayCodeGenerator;
        this.readExecutor = readExecutor;
        this.messageFactory = messageFactory;
        this.eligibilityService = eligibilityService;
        this.serverId = serverId;
        this.textSanitizer = new ChatTextSanitizer(maxStoredLength);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(event.message());
        String storedText = textSanitizer.sanitize(plainText);
        if (!eligibilityService.isEligible(storedText))
            return;

        UUID authorUuid = event.getPlayer().getUniqueId();
        String authorName = event.getPlayer().getName();
        long createdAt = System.currentTimeMillis();
        PendingChat pending;
        try {
            pending = readExecutor.submit(conn -> pendingChatService.putGenerated(
                    conn, displayCodeGenerator, serverId,
                    displayCode -> new PendingChat(displayCode, authorUuid,
                            authorName, storedText, null, null, null, null, createdAt)))
                    .join();
        } catch (CompletionException e) {
            log.log(Level.WARNING, "Failed to allocate a display code for chat", e);
            return;
        }

        Component suffix = messageFactory.buildChatLikeSuffix(pending.displayCode());
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            try {
                Component rendered = previous.render(source, sourceDisplayName, message, viewer);
                return viewer.equals(source) ? rendered : rendered.append(suffix);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Existing chat renderer failed; omitting chat like control", e);
                try {
                    return ChatRenderer.defaultRenderer().render(source, sourceDisplayName, message, viewer);
                } catch (RuntimeException fallbackFailure) {
                    return message;
                }
            }
        });
    }

}
