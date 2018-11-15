package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@RequiredArgsConstructor
final class Feeding implements Listener {
    private final HorsePlugin plugin;
    public static final int ONE_HOUR = 3600;

    @Getter
    enum Feed implements HumanReadable {
        // Better keep this unsorted
        WEIGHT_BOOST(0.35, new ItemStack(Material.NETHER_STALK)),
        SWEET_FEED  (0.30, new ItemStack(Material.MELON_SEEDS)),
        SPORT_GRAIN (0.20, new ItemStack(Material.SEEDS)),
        MINE_VITE   (0.17, new ItemStack(Material.BEETROOT_SEEDS), "Mine-Vite"),
        IMMUNE_BOOST(0.17, new ItemStack(Material.INK_SACK, 1, (short)8)), // Gray dye
        TROTTER     (0.16, new ItemStack(Material.PUMPKIN_SEEDS)),
        HAY_PELLET  (0.12, new ItemStack(Material.WHEAT)),
        FOAL_MIX    (0.12, new ItemStack(Material.GLOWSTONE_DUST)),
        APPLE       (0.11, new ItemStack(Material.APPLE)),                 // Vanilla apple
        CARROT      (0.10, new ItemStack(Material.CARROT_ITEM)),           // Vanilla carrot
        SUGAR_CUBE  (0.10, new ItemStack(Material.SUGAR)),
        ;

        public final String key;
        public final String humanName;
        public final double value;
        public final ItemStack item;

        Feed(double value, ItemStack item, String humanName) {
            this.key = name().toLowerCase();
            this.value = value;
            this.item = item;
            this.humanName = humanName;
        }

        Feed(double value, ItemStack item) {
            this.key = name().toLowerCase();
            this.humanName = HumanReadable.enumToHuman(this);
            this.value = value;
            this.item = item;
        }
    }

    ItemStack spawnFeed(Feed feed, int amount) {
        ItemStack item = feed.item.clone();
        item.setAmount(amount);
        switch (feed) {
        case APPLE: case CARROT: return item.clone();
        default: break;
        }
        item = this.plugin.getDirtyNBT().toCraftItemStack(item);
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(item, true);
        this.plugin.getDirtyNBT().setNBT(opt, HorsePlugin.ITEM_MARKER, feed.key);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + feed.humanName);
        ArrayList<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Horse Feed");
        for (String line: this.plugin.getConfig().getStringList("items." + feed.key + ".lore")) {
            lore.add(line);
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    Feed findFeed(ItemStack item) {
        switch (item.getType()) {
        case APPLE: return Feed.APPLE;
        case CARROT_ITEM: return Feed.CARROT;
        default: break;
        }
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(item, false);
        if (!opt.isPresent()) return null;
        opt = this.plugin.getDirtyNBT().getNBT(opt, HorsePlugin.ITEM_MARKER);
        if (!opt.isPresent()) return null;
        String key = (String)this.plugin.getDirtyNBT().fromNBT(opt);
        try {
            return Feed.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    /**
     * Feeding event.
     * We can assume that illegitimate clickes were already cancelled
     * by {@link HorseListener}.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Find horse
        if (!(event.getRightClicked() instanceof AbstractHorse)) return;
        final SpawnedHorse spawned = this.plugin.findSpawnedHorse((AbstractHorse)event.getRightClicked());
        if (spawned == null) return;
        // Check item
        final Player player = event.getPlayer();
        final ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        Feed feed = findFeed(item);
        if (feed == null) return;
        event.setCancelled(true);
        // Check horse
        if (spawned.data.getEatCooldown() > 0) {
            player.getWorld().playSound(spawned.getEntity().getEyeLocation(), Sound.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.5f, 1.0f);
            return;
        }
        if (spawned.data.getAge() == HorseAge.FOAL) return;
        if (feed == Feed.FOAL_MIX && spawned.data.getAge() != HorseAge.YEARLING) return;
        // Eat
        if (!player.isSneaking()) player.teleport(player.getLocation());
        spawned.data.setBody(spawned.data.getBody() + feed.value);
        spawned.data.setEatCooldown(ONE_HOUR);
        item.setAmount(item.getAmount() - 1);
        HorseEffects.feed(this.plugin, player, spawned, feed);
    }

    // Requires presence
    void passSecond(SpawnedHorse spawned) {
        // TODO: special case FOAL, YEARLING
        if (spawned.data.getEatCooldown() == 0) {
        // Grazing
            Block grazeBlock = findGrass(spawned.getEntity());
            if (grazeBlock != null) {
                spawned.data.setBody(spawned.data.getBody() + 0.07);
                spawned.data.setEatCooldown(3600);
                grazeBlock.setType(Material.DIRT);
                HorseEffects.grazeEffect(this.plugin, grazeBlock);
            }
        }
        // Every 3 hours, lose 0.1 body value, adding up to 0.8 per day.
        if (spawned.data.getBurnFatCooldown() == 0) {
            spawned.data.setBurnFatCooldown(ONE_HOUR * 3);
            spawned.data.setBody(spawned.data.getBody() - 0.10);
        }
    }

    private static int blockDistance(Block a, Block b) {
        return Math.abs(a.getX() - b.getX())
            + Math.abs(a.getY() - b.getY())
            + Math.abs(a.getZ() - b.getZ());
    }

    /**
     * Scan the perimeter for a grazable spot.  That is, find grass.
     */
    private Block findGrass(AbstractHorse entity) {
        Block center = entity.getLocation().getBlock().getRelative(0, -1, 0);
        if (center.getType() == Material.GRASS) return center;
        Block result = null;
        int distance = 0;
        final int r = 2;
        for (int z = -r; z <= r; z += 1) {
            for (int x = -r; x <= r; x += 1) {
                for (int y = -r; y <= r; y += 1) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getType() == Material.GRASS) {
                        int d = blockDistance(block, center);
                        if (result == null || d < distance) {
                            result = block;
                            distance = d;
                        }
                    }
                }
            }
        }
        return result;
    }
}
