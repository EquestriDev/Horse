package net.equestriworlds.horse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
            int o = this.ordinal() + 1;
            Gait[] v = this.values();
            if (o >= v.length) return null;
            return v[o];
        }

        Gait prev() {
            int o = this.ordinal() - 1;
            if (o < 0) return null;
            Gait[] v = this.values();
            return v[o];
        }
    }

    enum Click {
        LEFT, RIGHT;
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
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> player.sendActionBar("Left or right click with a stick to change speed."), 20);
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
        if (!spawned.isCrosstied()) {
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(spawned.data.getSpeed());
        }
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
        this.onUseCrop(player, spawned, Click.LEFT);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Click click;
        switch (event.getAction()) {
        case LEFT_CLICK_AIR: case LEFT_CLICK_BLOCK: click = Click.LEFT; break;
        case RIGHT_CLICK_AIR: case RIGHT_CLICK_BLOCK: click = Click.RIGHT; break;
        default: return;
        }
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
        this.onUseCrop(player, spawned, click);
        event.setCancelled(true);
    }

    /**
     * If a player who is not on cooldown uses a crop while riding a
     * horse which is not on the highest possible gait, the gait shall
     * increase, the horse speed set accordingly, and an audiovisual
     * cue be played.
     */
    private void onUseCrop(Player player, SpawnedHorse spawned, Click click) {
        if (spawned.isCrosstied()) return;
        GaitMeta meta = gaitMetaOf(player);
        long now = System.nanoTime();
        if (meta.cooldown > now) return;
        Gait newGait = click == Click.LEFT ? meta.gait.next() : meta.gait.prev();
        if (newGait == null) return;
        meta.cooldown = now + 1000000000; // 1 Second
        meta.gait = newGait;
        spawned.entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(spawned.data.getSpeed() * meta.gait.factor);
        // Effects
        player.sendTitle("", "" + meta.gait.prefix + meta.gait.humanName, 0, 20, 40);
        if (click == Click.LEFT) {
            player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5f, 1.2f);
            Location l = player.getEyeLocation();
            l = l.add(l.getDirection().multiply(2.0));
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 1, 0.0, 0.0, 0.0, 0.0);
        } else {
            player.getWorld().playSound(spawned.getEntity().getEyeLocation(), Sound.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 0.9f);
            player.getWorld().playSound(spawned.getEntity().getLocation(), Sound.ENTITY_HORSE_LAND, SoundCategory.NEUTRAL, 0.5f, 0.8f);
            Location l = spawned.getEntity().getLocation();
            l = l.add(l.getDirection().multiply(2.0)).add(0, 0.5, 0);
            player.getWorld().spawnParticle(Particle.SMOKE_LARGE, l, 8, 0.4, 0.0, 0.4, 0.1);
        }
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
