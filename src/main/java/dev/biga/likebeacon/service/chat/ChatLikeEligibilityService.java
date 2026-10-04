package dev.biga.likebeacon.service.chat;

/** Isolates chat eligibility policy for future channel-plugin integrations. */
public class ChatLikeEligibilityService {
    private final boolean enabled;
    private final int minLength;

    public ChatLikeEligibilityService(boolean enabled, int minLength) {
        this.enabled = enabled;
        this.minLength = Math.max(0, minLength);
    }

    public boolean isEligible(String bodyText) {
        return enabled && bodyText != null && bodyText.strip().length() >= minLength;
    }
}
