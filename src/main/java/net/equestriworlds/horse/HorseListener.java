package net.equestriworlds.horse;

import lombok.RequiredArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.InventoryHolder;
import org.spigotmc.event.entity.EntityDismountEvent;

@RequiredArgsConstructor
final class HorseListener implements Listener {
    private final HorsePlugin plugin;

    /**
     * Remove any horse entities marked as HorseData from the
     * chunk as they are considered illegal and most likely the result
     * of a previous crash or other error. Consider printing a message
     * to console for debug purposes.
     *
     * Check if any horses were supposed to be in
     * the chunk and if so, spawn them in.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onChunkLoad(ChunkLoadEvent event) {
        final Chunk chunk = event.getChunk();
        // Remove stray HorseData.
        for (Entity e: chunk.getEntities()) {
            if (e.getScoreboardTags().contains(HorsePlugin.SCOREBOARD_MARKER)) {
                e.remove();
            }
        }
        // Spawn in HorseData where they left.
        for (HorseData data: this.plugin.horsesInChunk(chunk)) {
            if (data.getLocation() == null) continue;
            SpawnedHorse spawnedHorse = this.plugin.findSpawnedHorse(data);
            if (spawnedHorse == null || !spawnedHorse.isPresent()) {
                this.plugin.spawnHorse(data, data.getLocation().bukkitLocation());
            }
        }
    }

    /**
     * See if the chunk contains equestri horse entities, remove them,
     * and note their location for future respawning when the chunk
     * loads again.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onChunkUnload(ChunkUnloadEvent event) {
        final Chunk chunk = event.getChunk();
        for (Entity e: chunk.getEntities()) {
            if (!(e instanceof AbstractHorse)) continue;
            AbstractHorse entity = (AbstractHorse)e;
            SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
            if (spawned != null) {
                spawned.data.storeLocation(entity.getLocation());
                this.plugin.getDatabase().updateHorse(spawned.data);
                entity.remove();
            } else if (entity.getScoreboardTags().contains(HorsePlugin.SCOREBOARD_MARKER)) {
                entity.remove();
            }
        }
    }

    /**
     * When a horse's inventory is closed, there is a chance that it
     * was modified. Use this opportunity to record the inventory into
     * the HorseData record.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)holder;
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        spawned.data.storeLocation(entity.getLocation());
        // TODO: inventory
        this.plugin.getDatabase().updateHorse(spawned.data);
    }

    /**
     * When a horse is unmounted, it is likely to have moved. Record
     * the location.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getDismounted();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        spawned.data.storeLocation(entity.getLocation());
        this.plugin.getDatabase().updateHorse(spawned.data);
    }

    /**
     * Use this event to check if a horse entity is a valid equestri
     * horse. Remove it if it is invalid due to some prior crash or
     * error.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getEntity();
        checkHorseEntityForValidity(entity);
    }

    /**
     * Same as above, but for right clicks.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse)) return;
        AbstractHorse entity  = (AbstractHorse)event.getRightClicked();
        checkHorseEntityForValidity(entity);
    }

    /**
     * Remove a given entity if it is not supposed to be there,
     * i.e. represents a dangling custom horse entity.
     *
     * This is the case whenever:
     * - A horse entity has the scoreboard marker.
     * - AND said horse is not represented by a StoredHorse instance via HorsePlugin.
     */
    void checkHorseEntityForValidity(AbstractHorse entity) {
        if (!entity.getScoreboardTags().contains(HorsePlugin.SCOREBOARD_MARKER)) return;
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null || !spawned.represents(entity)) entity.remove();
    }

    /**
     * When a player quits while riding an equestri horse, we want to
     * dismount and store its location so it doesn't spawn with the
     * player next time they join again.
     *
     * Consider allowing this but setting the location to null if the
     * player is the horse's owner.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() == null || !(player.getVehicle() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)player.getVehicle();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        spawned.data.storeLocation(entity.getLocation());
        entity.eject();
        this.plugin.getDatabase().updateHorse(spawned.data);
    }
}
