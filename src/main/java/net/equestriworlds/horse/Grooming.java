package net.equestriworlds.horse;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.equestriworlds.horse.HorseData.GroomingData;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
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
final class Grooming implements Listener {
    private final HorsePlugin plugin;

    enum Activity {
        WASH       (GroomingData::getWash,  GroomingData::setWash,  1, 3), // special case, see below
        CLIP       (GroomingData::getClip,  GroomingData::setClip,  2, 1),
        BRUSH      (GroomingData::getBrush, GroomingData::setBrush, 1, 5),
        PICK_HOOVES(GroomingData::getHoof,  GroomingData::setHoof,  1, 4),
        COMB       (GroomingData::getComb,  GroomingData::setComb,  1, 6),
        SHED       (GroomingData::getShed,  GroomingData::setShed,  1, 3),
        COMB_HAIR  (GroomingData::getHair,  GroomingData::setHair,  1, 2),
        OIL_HOOVES (GroomingData::getOil,   GroomingData::setOil,   1, 4),
        SHEEN      (GroomingData::getSheen, GroomingData::setSheen, 1, 3);
        final Function<GroomingData, Integer> getter;
        final BiConsumer<GroomingData, Integer> setter;
        final int appearance;
        final int maximum;

        Activity(Function<GroomingData, Integer> getter,
                 BiConsumer<GroomingData, Integer> setter,
                 int appearance, int maximum) {
            this.getter = getter;
            this.setter = setter;
            this.appearance = appearance;
            this.maximum = maximum;
        }
    }

    @Getter
    enum Tool implements HumanReadable {
        WATER_BUCKET (null, Activity.WASH,        new ItemStack(Material.WATER_BUCKET)),
        SHAMPOO      (null, Activity.WASH,        new ItemStack(Material.INK_SACK, 1, (short)10)), // Lime dye
        SWEAT_SCRAPER(null, Activity.WASH,        new ItemStack(Material.BLAZE_POWDER)),
        CLIPPER      (null, Activity.CLIP,        new ItemStack(Material.SHEARS)),
        BRUSH        (null, Activity.BRUSH,       new ItemStack(Material.BLAZE_ROD)),
        HOOF_PICK    (null, Activity.PICK_HOOVES, new ItemStack(Material.QUARTZ)),
        COMB         (null, Activity.COMB,        new ItemStack(Material.FLINT)),
        SHEDDING_TOOL(null, Activity.SHED,        new ItemStack(Material.NETHER_BRICK_ITEM)),
        MANE_BRUSH   (null, Activity.COMB_HAIR,   new ItemStack(Material.INK_SACK, 1, (short)13)), // Magenta dye
        HOOF_OIL     (null, Activity.OIL_HOOVES,  new ItemStack(Material.INK_SACK)),
        SHOW_SHEEN   (null, Activity.SHEEN,       new ItemStack(Material.GOLD_NUGGET));

        public final String key;
        public final String humanName;
        public final Activity activity;
        public final ItemStack toolItem;

        Tool(final String humanName, final Activity activity, final ItemStack toolItem) {
            this.key = name().toLowerCase();
            this.humanName = humanName != null ? humanName : HumanReadable.enumToHuman(this);
            this.activity = activity;
            this.toolItem = toolItem;
        }
    }

    Tool findTool(ItemStack item) {
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(item, false);
        if (!opt.isPresent()) return null;
        opt = this.plugin.getDirtyNBT().getNBT(opt, HorsePlugin.ITEM_MARKER); // Make a constant for this
        if (!opt.isPresent()) return null;
        String tag = (String)this.plugin.getDirtyNBT().fromNBT(opt);
        try {
            return Tool.valueOf(tag.toUpperCase());
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
            return null;
        }
    }

    // TODO: Item descriptions and multiple uses.
    ItemStack spawnTool(Tool tool) {
        if (tool == Tool.WATER_BUCKET) return Tool.WATER_BUCKET.toolItem.clone();
        ItemStack result = this.plugin.getDirtyNBT().toCraftItemStack(tool.toolItem.clone());
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(result, true);
        this.plugin.getDirtyNBT().setNBT(opt, HorsePlugin.ITEM_MARKER, tool.key);
        if (tool.activity != Activity.WASH) result.setAmount(tool.activity.maximum);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + tool.humanName);
        result.setItemMeta(meta);
        return result;
    }

    /**
     * Should be cancelled already if permissions are lacking.  See HorseListener.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse)) return;
        SpawnedHorse spawned = this.plugin.findSpawnedHorse((AbstractHorse)event.getRightClicked());
        if (spawned == null) return;
        Player player = event.getPlayer();
        // Find tool
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        Tool tool;
        if (item.getType() == Material.WATER_BUCKET) {
            tool = Tool.WATER_BUCKET;
        } else {
            tool = findTool(item);
        }
        if (tool == null) return;
        // Reset orientation
        event.setCancelled(true);
        if (!player.isSneaking()) player.teleport(player.getLocation());
        // Activity
        GroomingData groomingData = spawned.data.getGrooming();
        long now = System.currentTimeMillis();
        if (groomingData == null) {
            groomingData = new GroomingData();
            groomingData.expiration = now * 60L * 60L * 12L; // Stay for 12 hours.
            spawned.data.setGrooming(groomingData);
        }
        if (now < groomingData.cooldown) return;
        boolean success;
        final int value;
        if (tool.activity == Activity.WASH) {
            value = groomingData.wash;
            if (tool == Tool.WATER_BUCKET && value == 0) {
                item.setType(Material.BUCKET);
                groomingData.wash = 1;
                success = true;
            } else if (tool == Tool.SHAMPOO && value == 1) {
                groomingData.wash = 2;
                success = true;
            } else if (tool == Tool.SWEAT_SCRAPER && value == 2) {
                groomingData.wash = 3;
                groomingData.appearance += Activity.WASH.appearance;
                success = true;
            } else {
                success = false;
            }
        } else {
            if (groomingData.wash < 3) {
                player.sendActionBar(ChatColor.RED + "Clean " + spawned.data.getName() + " first.");
                return;
            }
            value = tool.activity.getter.apply(groomingData);
            if (value >= tool.activity.maximum) {
                success = false;
            } else {
                tool.activity.setter.accept(groomingData, value + 1);
                groomingData.appearance += tool.activity.appearance;
                success = true;
            }
        }
        // Consume item and save horse.
        if (success) {
            if (tool != Tool.WATER_BUCKET) item.setAmount(item.getAmount() - 1);
            groomingData.cooldown = now + 500L;
            this.plugin.getDatabase().updateHorse(spawned.data);
            String title = successNotification(player, spawned, tool, value);
            player.sendActionBar(title);
            successEffect(player, spawned, tool, value);
        } else {
            String title = failNotification(player, spawned, tool, value);
            player.sendActionBar(ChatColor.RED + title);
        }
    }

    String successNotification(Player player, SpawnedHorse spawned, Tool tool, int value) {
        switch (tool) {
        case WATER_BUCKET: return ChatColor.BLUE + "You soak " + spawned.data.getName() + ".";
        case SHAMPOO: return ChatColor.GREEN + "" + spawned.data.getName() + " is now shampooed.";
        case SWEAT_SCRAPER: return ChatColor.GOLD + "You use the sweat scraper on " + spawned.data.getName() + ".";
        case CLIPPER: return ChatColor.DARK_GRAY + "You use the clipper on " + spawned.data.getName() + ".";
        case BRUSH:
            switch (value) {
            case 0: return ChatColor.BLACK + "You brush " + spawned.data.getName() + " once.";
            case 1: return ChatColor.DARK_BLUE + "You brush " + spawned.data.getName() + " twice.";
            case 2: return ChatColor.DARK_AQUA + "You brush " + spawned.data.getName() + " three times.";
            case 3: return ChatColor.AQUA + "You brush " + spawned.data.getName() + " four times.";
            case 4: default: return "" + ChatColor.BLUE + ChatColor.BOLD + "You finished brushing " + spawned.data.getName() + ".";
            }
        case HOOF_PICK:
            switch (value) {
            case 0: return ChatColor.BLACK + "You pick the front left hoof of " + spawned.data.getName() + ".";
            case 1: return ChatColor.DARK_GRAY + "You pick the front right hoof of " + spawned.data.getName() + ".";
            case 2: return ChatColor.GRAY + "You pick the back right hoof of " + spawned.data.getName() + ".";
            case 3: default: return "" + ChatColor.WHITE + ChatColor.BOLD + "You picked all the hooves of " + spawned.data.getName() + ".";
            }
        case COMB:
            switch (value) {
            case 0: return ChatColor.BLACK + "You comb " + spawned.data.getName() + " once.";
            case 1: return ChatColor.DARK_GRAY + "You comb " + spawned.data.getName() + " twice.";
            case 2: return ChatColor.DARK_GRAY + "You comb " + spawned.data.getName() + " three times.";
            case 3: return ChatColor.GRAY + "You comb " + spawned.data.getName() + " four times.";
            case 4: return ChatColor.GRAY + "You comb " + spawned.data.getName() + " five times.";
            case 5: default: return "" + ChatColor.WHITE + ChatColor.BOLD + "You finished combing " + spawned.data.getName() + ".";
            }
        case SHEDDING_TOOL:
            switch (value) {
            case 0: return ChatColor.RED + "You shed " + spawned.data.getName() + " once.";
            case 1: return ChatColor.GOLD + "You shed " + spawned.data.getName() + " twice.";
            case 2: default: return "" + ChatColor.YELLOW + ChatColor.BOLD + "You finished shedding " + spawned.data.getName() + ".";
            }
        case MANE_BRUSH:
            switch (value) {
            case 0: return ChatColor.DARK_PURPLE + "You brush the mane of " + spawned.data.getName() + ".";
            case 1: default: return ChatColor.LIGHT_PURPLE + "You brush the tail of " + spawned.data.getName() + ".";
            }
        case HOOF_OIL:
            switch (value) {
            case 0: return ChatColor.BLACK + "You oil the front left hoof of " + spawned.data.getName() + ".";
            case 1: return ChatColor.BLACK + "You oil the front right hoof of " + spawned.data.getName() + ".";
            case 2: return ChatColor.BLACK + "You oil the back right hoof of " + spawned.data.getName() + ".";
            case 3: default: return "" + ChatColor.BLACK + ChatColor.BOLD + "You oiled all the hooves of " + spawned.data.getName() + ".";
            }
        case SHOW_SHEEN:
            switch (value) {
            case 0: return ChatColor.YELLOW + "You apply the show sheen on " + spawned.data.getName() + ".";
            case 1: return ChatColor.GOLD + "You rub the show sheen on " + spawned.data.getName() + ".";
            case 2: default: return "" + ChatColor.GOLD + ChatColor.BOLD + spawned.data.getName() + " is now all shiny.";
            }
        default: throw new IllegalStateException("Unsupported tool: " + tool);
        }
    }

    void successEffect(Player player, SpawnedHorse spawned, Tool tool, int value) {
        final Location l = spawned.getEntity().getLocation();
        final Location e = spawned.getEntity().getEyeLocation();
        final World w = l.getWorld();
        final float v = 1.0f;
        final double r = 0.65;
        if (value + 1 == tool.activity.maximum) {
            player.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.25f, 2.0f);
        }
        switch (tool) {
        case WATER_BUCKET: {
            w.playSound(l, Sound.ITEM_BUCKET_FILL, SoundCategory.MASTER, v, 1.25f);
            w.spawnParticle(Particle.WATER_SPLASH, e, 64, r, r, r, 0.0);
            break;
        }
        case SHAMPOO: {
            w.playSound(l, Sound.ITEM_BOTTLE_FILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CLOUD, e, 32, r, r, r, 0.0);
            break;
        }
        case SWEAT_SCRAPER: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.BLOCK_DUST, e, 24, r, r, r, 0.0, Material.WOOL.getNewData((byte)8)); // light gray wool
            break;
        }
        case CLIPPER: {
            w.playSound(l, Sound.ENTITY_SHEEP_SHEAR, SoundCategory.MASTER, v, 2.0f);
            w.spawnParticle(Particle.BLOCK_DUST, e, 24, r, r, r, 0.0, Material.WOOL.getNewData((byte)7)); // dark gray wool
            break;
        }
        case BRUSH: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case HOOF_PICK: {
            w.playSound(l, Sound.ENTITY_HORSE_GALLOP, SoundCategory.MASTER, v, 2.0f);
            w.spawnParticle(Particle.SMOKE_NORMAL, l, 8, r, 0.0, r, 0.0);
            break;
        }
        case COMB: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case SHEDDING_TOOL: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case MANE_BRUSH: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case HOOF_OIL: {
            w.playSound(l, Sound.ITEM_BOTTLE_FILL, SoundCategory.MASTER, v, 2.0f);
            w.spawnParticle(Particle.SMOKE_NORMAL, l, 8, r, 0.0, r, 0.0);
            break;
        }
        case SHOW_SHEEN: {
            w.playSound(l, Sound.ITEM_BOTTLE_FILL, SoundCategory.MASTER, v, 2.0f);
            w.spawnParticle(Particle.END_ROD, e, 16, r, r, r, 0.25);
            break;
        }
        default: throw new IllegalStateException("Unsupported tool: " + tool);
        }
    }

    String failNotification(Player player, SpawnedHorse spawned, Tool tool, int value) {
        switch (tool) {
        case WATER_BUCKET: return "Already soaked.";
        case SHAMPOO: return "Already shampooed.";
        case SWEAT_SCRAPER: return "Already scraped.";
        case CLIPPER: return "Already clipped.";
        case BRUSH: return "Already brushed.";
        case HOOF_PICK: return "Already picked hooves.";
        case COMB: return "Already combed.";
        case SHEDDING_TOOL: return "Already shed.";
        case MANE_BRUSH: return "Already brushed mane.";
        case HOOF_OIL: return "Already oiled hooves.";
        case SHOW_SHEEN: return "Already applied show sheen.";
        default: throw new IllegalStateException("Unsupported tool: " + tool);
        }
    }
}
