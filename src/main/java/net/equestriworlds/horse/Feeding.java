package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
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

/**
 * Manage horse feeding, body value, and hydration.
 */
@RequiredArgsConstructor
public final class Feeding implements Listener {
    public static final String COOLDOWN_EAT = "eat";
    public static final String COOLDOWN_BURN_FAT = "burn_fat";
    public static final String COOLDOWN_DRINK = "drink";
    public static final String COOLDOWN_DEHYDRATE = "dehydrate";
    private final HorsePlugin plugin;

    @Data
    public final class Persistence {
        private int eatCooldown = Util.ONE_HOUR;
        private int drinkCooldown = Util.ONE_HOUR;
        private int burnFatCooldown = Util.ONE_HOUR;
        private int dehydrateCooldown = Util.ONE_HOUR;
    }

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
        for (String line: this.plugin.getItemsConfig().getStringList(feed.key + ".lore")) {
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
        if (spawned.extra.getCooldown(COOLDOWN_EAT) > 0) {
            player.getWorld().playSound(spawned.getEntity().getEyeLocation(), Sound.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.5f, 1.0f);
            return;
        }
        // Foals cannot be fed; yearlings only eat foal mix.
        if (spawned.data.getAge() == HorseAge.FOAL) return;
        if (feed == Feed.FOAL_MIX && spawned.data.getAge() != HorseAge.YEARLING) return;
        // Eat
        if (!player.isSneaking()) player.teleport(player.getLocation());
        spawned.data.setBody(spawned.data.getBody() + feed.value);
        spawned.extra.setCooldown(COOLDOWN_EAT, Util.ONE_HOUR);
        item.setAmount(item.getAmount() - 1);
        HorseEffects.feed(this.plugin, player, spawned, feed);
    }

    double dailyBodyReduction(SpawnedHorse spawned) {
        switch (spawned.data.getAge()) {
        case FOAL:
        case YEARLING:
            return 0.60;
        case ADOLESCENT:
            return 0.80;
        case ADULT:
        default:
            switch (spawned.data.getBreedingStage()) {
            case PREGNANT:
            case NURTURE:
                return 0.90;
            default:
                return 0.80;
            }
        }
    }

    boolean isMotherNearby(SpawnedHorse baby) {
        HorseData mother = baby.data.getMother(this.plugin);
        if (mother == null) return false;
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(mother);
        final Location motherLocation;
        if (spawned == null || !spawned.isPresent()) {
            if (spawned.data.getLocation() == null) return false;
            motherLocation = spawned.data.getLocation().bukkitLocation();
            if (motherLocation == null) return false;
        } else {
            motherLocation = spawned.getEntity().getLocation();
        }
        Location babyLocation = baby.getEntity().getLocation();
        if (!motherLocation.getWorld().equals(babyLocation.getWorld())) return false;
        return babyLocation.distanceSquared(motherLocation) <= 36.0; // 6 * 6
    }

    // Requires presence
    void passSecond(SpawnedHorse spawned, long now) {
        if (spawned.extra.getCooldown(COOLDOWN_EAT) == 0) {
            // Grazing
            switch (spawned.data.getAge()) {
            case FOAL: { // Foals cannot be fed or graze but suckle their mother.
                if (!isMotherNearby(spawned)) break;
                // Suckle 0.1 per 4 hours, adding up to 0.6 per day.
                spawned.extra.setCooldown(COOLDOWN_EAT, Util.ONE_HOUR * 4);
                spawned.data.setBody(spawned.data.getBody() + 0.6);
                // Audiovisual feedback
                HorseEffects.suckleEffect(this.plugin, spawned);
                break;
            }
            case YEARLING: // Yearlings need to be fed but cannot graze.
                break;
            default: {
                if (spawned.canFreeroam()) {
                    Block grazeBlock = findGrass(spawned.getEntity());
                    if (grazeBlock != null) {
                        spawned.data.setBody(spawned.data.getBody() + 0.07);
                        spawned.extra.setCooldown(COOLDOWN_EAT, Util.ONE_HOUR);
                        grazeBlock.setType(Material.DIRT);
                        HorseEffects.grazeEffect(this.plugin, grazeBlock);
                    }
                }
            }
            }
        }
        // Burn fat = lose body value.
        if (spawned.extra.getCooldown(COOLDOWN_BURN_FAT) == 0) {
            switch (spawned.data.getAge()) {
            case FOAL:
            case YEARLING:
                // 0.1 * 6 = 0.6 (every 4 hours);
                spawned.data.setBody(spawned.data.getBody() - 0.1);
                spawned.extra.setCooldown(COOLDOWN_BURN_FAT, Util.ONE_HOUR * 4);
                break;
            default:
                switch (spawned.data.getBreedingStage()) {
                case PREGNANT:
                case NURTURE:
                    // 0.15 * 6 = 0.9 (every 4 hours)
                    spawned.data.setBody(spawned.data.getBody() - 0.15);
                    spawned.extra.setCooldown(COOLDOWN_BURN_FAT, Util.ONE_HOUR * 4);
                    break;
                default:
                    // 0.1 * 8 = 0.8 (every 3 hours)
                    spawned.data.setBody(spawned.data.getBody() - 0.1);
                    spawned.extra.setCooldown(COOLDOWN_BURN_FAT, Util.ONE_HOUR * 3);
                }
            }
            this.plugin.saveHorse(spawned.data);
        }
        // Hydration
        if (spawned.extra.getCooldown(COOLDOWN_DRINK) == 0) {
            if (spawned.data.getHydration() >= HydrationLevel.MAX_VALUE) {
                // If we're already fully hydrated, wait another our.
                spawned.extra.setCooldown(COOLDOWN_DRINK, Util.ONE_HOUR);
            } else if (!spawned.canFreeroam()) {
                // We can only drink if we're free.
                spawned.extra.setCooldown(COOLDOWN_DRINK, Util.ONE_MINUTE);
            } else {
                // We are thirsty and can move freely.  Try to drink.
                Block waterBlock = findWater(spawned.getEntity());
                if (waterBlock == null) {
                    spawned.extra.setCooldown(COOLDOWN_DRINK, Util.ONE_MINUTE);
                } else {
                    int fill = (int)waterBlock.getData();
                    waterBlock.setData((byte)(fill - 1));
                    double hydration = spawned.data.getHydration();
                    hydration = Math.min(HydrationLevel.MAX_VALUE, hydration + 1.0);
                    spawned.data.setHydration(hydration);
                    if (hydration < HydrationLevel.MAX_VALUE) {
                        spawned.extra.setCooldown(COOLDOWN_DRINK, Util.ONE_HOUR);
                    } else {
                        // We are fully restocked.  Reset the
                        // dehydration cooldown now.
                        spawned.extra.setCooldown(COOLDOWN_DRINK, Util.ONE_HOUR);
                        spawned.extra.setCooldown(COOLDOWN_DEHYDRATE, Util.ONE_HOUR * 24);
                    }
                    HorseEffects.drinkEffect(this.plugin, spawned, waterBlock);
                }
            }
            this.plugin.saveHorse(spawned.data);
        }
        if (spawned.extra.getCooldown(COOLDOWN_DEHYDRATE) == 0) {
            double hydration = spawned.data.getHydration();
            spawned.data.setHydration(Math.max(0.0, hydration - 0.5));
            spawned.extra.setCooldown(COOLDOWN_DEHYDRATE, Util.ONE_DAY);
            this.plugin.saveHorse(spawned.data);
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

    private Block findWater(AbstractHorse entity) {
        Block center = entity.getLocation().getBlock();
        Block result = null;
        int distance = 0;
        final int r = 6;
        for (int z = -r; z <= r; z += 1) {
            for (int x = -r; x <= r; x += 1) {
                for (int y = -r; y <= r; y += 1) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getType() == Material.CAULDRON && (int)block.getData() > 0) {
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
