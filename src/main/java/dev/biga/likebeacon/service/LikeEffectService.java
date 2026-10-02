package dev.biga.likebeacon.service;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays particle effects when a player receives a Like notification.
 * All methods must be called on the server main thread.
 */
public class LikeEffectService {

    private static final Logger log = Logger.getLogger(LikeEffectService.class.getName());

    private static final double SPREAD = 0.3;
    private static final double Y_OFFSET = 2.5;

    private final FileConfiguration config;

    public LikeEffectService(FileConfiguration config) {
        this.config = config;
    }

    /** Shows the shared recipient effect once per delivered notification. */
    public void showReceivedLikeEffect(Player target) {
        if (!isEnabled())
            return;
        showEffect(target, Particle.HEART, 3);
        showEffect(target, Particle.FIREWORK, 10);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void showEffect(Player player, Particle particle, int count) {
        if (!player.isOnline())
            return;
        try {
            Location loc = player.getLocation().add(0, Y_OFFSET, 0);
            player.getWorld().spawnParticle(particle, loc, count, SPREAD, SPREAD, SPREAD, 0.0);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to show particle effect for " + player.getName(), e);
        }
    }

    private boolean isEnabled() {
        return config.getBoolean("effects.enabled", true);
    }
}
