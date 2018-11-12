package net.equestriworlds.horse;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

/**
 * This class listens for various horse related events on behalf of
 * features or generic too simple to warrant their own manager class.
 */
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
        // Remove stray entities.
        for (Entity e: chunk.getEntities()) {
            if (e.getScoreboardTags().contains(HorsePlugin.SCOREBOARD_MARKER)) {
                e.remove();
            }
        }
        final int cx = chunk.getX();
        final int cz = chunk.getZ();
        // Spawn in horse entity where they were last recorded.
        for (HorseData data: this.plugin.getHorses()) {
            HorseData.HorseLocation horseLocation = data.getLocation();
            if (horseLocation == null || horseLocation.cx != cx || horseLocation.cz != cz) continue;
            SpawnedHorse spawnedHorse = this.plugin.findSpawnedHorse(data);
            if (spawnedHorse != null && spawnedHorse.isPresent()) continue;
            this.plugin.spawnHorse(data, horseLocation.bukkitLocation());
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
            if (!e.getScoreboardTags().contains(HorsePlugin.SCOREBOARD_MARKER)) continue;
            if (e instanceof AbstractHorse) {
                AbstractHorse entity = (AbstractHorse)e;
                SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
                if (spawned != null) {
                    spawned.data.storeLocation(entity.getLocation());
                    spawned.data.storeInventory(this.plugin, entity);
                    this.plugin.getDatabase().updateHorse(spawned.data);
                }
            }
            e.remove();
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
        spawned.data.storeInventory(this.plugin, entity);
        this.plugin.getDatabase().updateHorse(spawned.data);
    }

    /**
     * On horse mount, store location and stop following.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getMount() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getMount();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        spawned.data.storeLocation(entity.getLocation());
        this.plugin.getDatabase().updateHorse(spawned.data);
        spawned.setFollowing(null);
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
     * error.  horse.  Remove the entity if it is invalid due to some
     * prior crash or other error.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getEntity();
        SpawnedHorse spawned = checkHorseEntityForValidity(entity);
        if (spawned != null) event.setCancelled(true);
    }

    /**
     * Same as above, but for right clicks.
     * Furthermore, check permission.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse)) return;
        AbstractHorse entity  = (AbstractHorse)event.getRightClicked();
        SpawnedHorse spawned = checkHorseEntityForValidity(entity);
        if (spawned == null) return;
        Player player = event.getPlayer();
        if (!spawned.data.canAccess(player)) {
            event.setCancelled(true);
            // Feedback
            player.playSound(player.getEyeLocation(), Sound.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.5f, 1.0f);
            player.spawnParticle(Particle.VILLAGER_ANGRY, entity.getEyeLocation(), 1, 0.25, 0.25, 0.25, 0.0);
            // Fix orientation
            final Location location = player.getLocation();
            Bukkit.getScheduler().runTask(this.plugin, () -> player.teleport(location));
            return;
        }
        // Crossties
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        Crosstie crosstie = spawned.getCrosstie();
        if (crosstie == null
            && item != null && item.getType() == Material.LEASH
            && entity.isLeashed() && entity.getLeashHolder() instanceof LeashHitch) {
            if (crosstie == null) {
                // New crosstie
                // Replace current leash with armor stand.
                LeashHitch hitch = (LeashHitch)entity.getLeashHolder();
                entity.setLeashHolder(player);
                Bat bat = this.plugin.spawnCrosstieBat(entity.getLocation());
                bat.setLeashHolder(hitch);
                // Crosstie object
                crosstie = new Crosstie(spawned);
                crosstie.setHitchA(hitch);
                crosstie.setHolder(player);
                crosstie.setLeashedBat(bat);
                spawned.setupCrosstie(crosstie);
                // Cancel event and turn player
                event.setCancelled(true);
                Location playerLocation = player.getLocation();
                player.teleport(playerLocation);
            }
        }
    }

    @EventHandler
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getEntity();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        Player player = event.getPlayer();
        if (!spawned.data.canAccess(player)) {
            event.setCancelled(true);
            return;
        }
        Crosstie crosstie = spawned.getCrosstie();
        if (crosstie != null && crosstie.isValid()) {
            if (event.getLeashHolder() instanceof LeashHitch) {
                Bat leashedBat = crosstie.getLeashedBat();
                if (leashedBat != null && leashedBat.isLeashed() && leashedBat.getLeashHolder().equals(event.getLeashHolder())) {
                    // At this point, data should not yet contain a
                    // crosstie instance.
                    spawned.removeCrosstie();
                    return;
                }
                // Success
                crosstie.setHolder(null);
                crosstie.setHitchB((LeashHitch)event.getLeashHolder());
                if (crosstie.check()) spawned.data.setCrosstie(crosstie.serialize());
            }
        }
    }

    /**
     * Remove a given entity if it is not supposed to be there,
     * i.e. represents a dangling custom horse entity.
     *
     * This is the case whenever:
     * - A horse entity has the scoreboard marker.
     * - AND said horse is not represented by a StoredHorse instance via HorsePlugin.
     */
    SpawnedHorse checkHorseEntityForValidity(AbstractHorse entity) {
        if (!entity.getScoreboardTags().contains(HorsePlugin.SCOREBOARD_MARKER)) return null;
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null || !spawned.represents(entity)) entity.remove();
        return spawned;
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

    // --- GUI

    @EventHandler
    public void onGUIOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof HorseGUI) {
            ((HorseGUI)event.getInventory().getHolder()).onOpen(event);
        }
    }

    @EventHandler
    public void onGUIClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof HorseGUI) {
            ((HorseGUI)event.getInventory().getHolder()).onClose(event);
        }
    }

    @EventHandler
    public void onGUIClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof HorseGUI) {
            ((HorseGUI)event.getInventory().getHolder()).onClick(event);
        }
    }

    @EventHandler
    public void onGUIDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HorseGUI) {
            ((HorseGUI)event.getInventory().getHolder()).onDrag(event);
        }
    }
}
