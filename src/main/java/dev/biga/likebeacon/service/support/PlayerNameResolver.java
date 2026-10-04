package dev.biga.likebeacon.service.support;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Resolves current or last-known player names on the server main thread. */
public final class PlayerNameResolver {

    public String resolve(UUID uuid) {
        if (uuid == null)
            return "";
        Player online = Bukkit.getPlayer(uuid);
        if (online != null)
            return online.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
