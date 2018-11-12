package net.equestriworlds.horse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

/**
 * Once a horse is mounted, its speed must be set to the lowest
 * possible gait.  What should happen once it is dismounted again?
 * Reset to the final speed?  Perhaps the lowest speed should always
 * be set, including the basic function in HorseData.
 * Significatn events:
 * - EntityMountEvent
 * - EntityDismountEvent
 *
 * Left clicking a stick while on the back of a horse will
 * increase its walking speed, which goes along with a displayed
 * title and audio cue.  Significant events:
 * - EntityDamageByEntityEvent
 * - PlayerInteractEvent
 */
@RequiredArgsConstructor
final class Gaits implements Listener {
    private final HorsePlugin plugin;

    @Getter
    enum Gait implements HumanReadable {
        HALT  (0.0,  "" + ChatColor.GRAY),
        WALK  (0.25, "" + ChatColor.YELLOW),
        TROT  (0.5,  "" + ChatColor.YELLOW + ChatColor.ITALIC),
        CANTER(0.75, "" + ChatColor.GOLD   + ChatColor.ITALIC),
        GALLOP(1.0,  "" + ChatColor.GOLD   + ChatColor.ITALIC + ChatColor.BOLD);
        public final String humanName;
        public final double factor;
        public final String prefix;
        Gait(double factor, String prefix) {
            this.humanName = HumanReadable.enumToHuman(this);
            this.factor = factor;
            this.prefix = prefix;
        }
        Gait next() {
            int o = this.ordinal();
            Gait[] v = this.values();
            return v[Math.min(v.length - 1, o + 1)];
        }
    }

    // --- Mount events

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getMount() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getMount();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        Player player = (Player)event.getEntity();
        gaitMetaOf(player).gait = Gait.HALT;
        entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
        player.sendTitle("", "" + Gait.HALT.prefix + Gait.HALT.humanName, 0, 20, 40);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDismounted() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)event.getDismounted();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        Player player = (Player)event.getEntity();
        removeGaitMeta(player);
        entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(spawned.effectiveSpeed());
    }

    // Whip events

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Player
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player)event.getDamager();
        // Item
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.STICK) return;
        // Mounted Horse
        if (!(player.getVehicle() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)player.getVehicle();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        // Use
        this.onUseCrop(player, spawned);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        // Item
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.STICK) return;
        // Mounted Horse
        if (!(player.getVehicle() instanceof AbstractHorse)) return;
        AbstractHorse entity = (AbstractHorse)player.getVehicle();
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) return;
        // Use
        this.onUseCrop(player, spawned);
        event.setCancelled(true);
    }

    /**
     * If a player who is not on cooldown uses a crop while riding a
     * horse which is not on the highest possible gait, the gait shall
     * increase, the horse speed set accordingly, and an audiovisual
     * cue be played.
     */
    private void onUseCrop(Player player, SpawnedHorse spawned) {
        GaitMeta meta = gaitMetaOf(player);
        long now = System.nanoTime();
        if (meta.gait == Gait.GALLOP) return;
        if (meta.cooldown > now) return;
        meta.cooldown = now + 1000000000; // 1 Second
        meta.gait = meta.gait.next();
        spawned.entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(spawned.effectiveSpeed() * meta.gait.factor);
        // Effects
        player.sendTitle("", "" + meta.gait.prefix + meta.gait.humanName, 0, 20, 40);
        player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5f, 1.2f);
    }

    // --- Metadata

    class GaitMeta {
        static final String KEY = "equestriworlds.horse.gait";
        Gait gait;
        long cooldown = 0;
    }

    /**
     * Get or create the GaitMeta of this player.
     */
    GaitMeta gaitMetaOf(Player player) {
        for (MetadataValue mv: player.getMetadata(GaitMeta.KEY)) {
            if (mv.getOwningPlugin() == this.plugin && mv.value() instanceof GaitMeta) {
                return (GaitMeta)mv.value();
            }
        }
        GaitMeta newMeta = new GaitMeta();
        newMeta.gait = Gait.HALT;
        player.setMetadata(GaitMeta.KEY, new FixedMetadataValue(this.plugin, newMeta));
        return newMeta;
    }

    void removeGaitMeta(Player player) {
        player.removeMetadata(GaitMeta.KEY, this.plugin);
    }
}
