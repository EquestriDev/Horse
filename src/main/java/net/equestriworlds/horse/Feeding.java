package net.equestriworlds.horse;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

@RequiredArgsConstructor
final class Feeding implements Listener {
    private final HorsePlugin plugin;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
    }
}
