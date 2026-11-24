package net.starryisle.mistbingo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Frees per-player state when someone leaves.
 *
 * <p>Without this the service would keep every player who ever joined in memory;
 * on a long-running server that is an unbounded, if slow, leak.
 */
public final class PlayerListener implements Listener {

    private final MistBingoPlugin plugin;

    public PlayerListener(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.daily().unload(player);
    }
}
