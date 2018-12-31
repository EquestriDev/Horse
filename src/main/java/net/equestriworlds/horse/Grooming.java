package net.equestriworlds.horse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
public final class Grooming implements Listener {
    private final HorsePlugin plugin;
    public static final String EXTRA_DATA_KEY = "grooming";

    @Data
    static final class Persistence {
        public static final String EXTRA_KEY = "grooming";
        long expiration;
        int wash, clip, brush, hoof, comb, shed, hair, face, oil, sheen;
        transient long cooldown;
    }

    enum Activity {
        WASH       (Persistence::getWash,  Persistence::setWash,  1, 3), // special case, see below
        CLIP       (Persistence::getClip,  Persistence::setClip,  2, 1),
        BRUSH      (Persistence::getBrush, Persistence::setBrush, 1, 5),
        PICK_HOOVES(Persistence::getHoof,  Persistence::setHoof,  1, 4),
        COMB       (Persistence::getComb,  Persistence::setComb,  1, 6),
        SHED       (Persistence::getShed,  Persistence::setShed,  1, 3),
        COMB_HAIR  (Persistence::getHair,  Persistence::setHair,  1, 2),
        BRUSH_FACE (Persistence::getFace,  Persistence::setFace,  1, 2),
        OIL_HOOVES (Persistence::getOil,   Persistence::setOil,   1, 4),
        SHEEN      (Persistence::getSheen, Persistence::setSheen, 1, 3);
        final Function<Persistence, Integer> getter;
        final BiConsumer<Persistence, Integer> setter;
        final int appearance;
        final int maximum;

        Activity(Function<Persistence, Integer> getter,
                 BiConsumer<Persistence, Integer> setter,
                 int appearance, int maximum) {
            this.getter = getter;
            this.setter = setter;
            this.appearance = appearance;
            this.maximum = maximum;
        }
    }

    @Getter
    enum Tool implements HumanReadable {
        WATER_BUCKET      (Activity.WASH,        1,  new ItemStack(Material.WATER_BUCKET)),
        SHAMPOO           (Activity.WASH,        15, new ItemStack(Material.INK_SACK, 1, (short)10)), // Lime dye
        SWEAT_SCRAPER     (Activity.WASH,        45, new ItemStack(Material.BLAZE_POWDER)),
        CLIPPERS          (Activity.CLIP,        45, new ItemStack(Material.SHEARS)),
        HARD_BRUSH        (Activity.BRUSH,       45, new ItemStack(Material.BLAZE_ROD)),
        HOOF_PICK         (Activity.PICK_HOOVES, 45, new ItemStack(Material.QUARTZ)),
        CURRY_COMB        (Activity.COMB,        45, new ItemStack(Material.FLINT)),
        SHEDDING_BLADE    (Activity.SHED,        45, new ItemStack(Material.NETHER_BRICK_ITEM)),
        MANE_AND_TAIL_COMB(Activity.COMB_HAIR,   45, new ItemStack(Material.INK_SACK, 1, (short)13)), // Magenta dye
        FACE_BRUSH        (Activity.BRUSH_FACE,  45, new ItemStack(Material.INK_SACK, 1, (short)7)),  // Gray dye
        HOOF_OIL          (Activity.OIL_HOOVES,  15, new ItemStack(Material.INK_SACK)),
        SHOW_SHEEN        (Activity.SHEEN,       15, new ItemStack(Material.GOLD_NUGGET));

        public final String key;
        public final String humanName;
        public final Activity activity;
        public final int uses;
        public final ItemStack item;

        Tool(final Activity activity, final int uses, final ItemStack item) {
            this.key = name().toLowerCase();
            this.humanName = HumanReadable.enumToHuman(this);
            this.activity = activity;
            this.uses = uses;
            this.item = item;
        }
    }

    Tool findTool(ItemStack item) {
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(item, false);
        if (!opt.isPresent()) return null;
        opt = this.plugin.getDirtyNBT().getNBT(opt, HorsePlugin.ITEM_MARKER);
        if (!opt.isPresent()) return null;
        String tag = (String)this.plugin.getDirtyNBT().fromNBT(opt);
        try {
            return Tool.valueOf(tag.toUpperCase());
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    int getUses(ItemStack item) {
        if (item.getType() == Material.WATER_BUCKET) return 1;
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(item, false);
        if (!opt.isPresent()) return 0;
        opt = this.plugin.getDirtyNBT().getNBT(opt, HorsePlugin.ITEM_USES);
        if (!opt.isPresent()) return 0;
        return (int)this.plugin.getDirtyNBT().fromNBT(opt);
    }

    void setUses(ItemStack item, int uses) {
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(item, false);
        if (!opt.isPresent()) throw new IllegalArgumentException("item has no nbt tag");
        this.plugin.getDirtyNBT().setNBT(opt, HorsePlugin.ITEM_USES, uses);
    }

    ItemStack spawnTool(Tool tool) {
        if (tool == Tool.WATER_BUCKET) return Tool.WATER_BUCKET.item.clone();
        ItemStack result = this.plugin.getDirtyNBT().toCraftItemStack(tool.item.clone());
        Optional<Object> opt = this.plugin.getDirtyNBT().accessItemNBT(result, true);
        this.plugin.getDirtyNBT().setNBT(opt, HorsePlugin.ITEM_MARKER, tool.key);
        this.plugin.getDirtyNBT().setNBT(opt, HorsePlugin.ITEM_USES, tool.uses);
        updateTool(result, tool, tool.uses);
        return result;
    }

    void updateTool(ItemStack item, Tool tool, int uses) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + tool.humanName);
        ArrayList<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Grooming Tool");
        for (String line: this.plugin.getItemsConfig().getStringList(tool.key + ".lore")) {
            lore.add(line);
        }
        lore.add(ChatColor.GRAY + "Uses: " + ChatColor.WHITE + uses + ChatColor.GRAY + "/" + ChatColor.WHITE + tool.uses);
        meta.setLore(lore);
        item.setItemMeta(meta);
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
        Persistence persistence = spawned.extra.getGrooming();
        if (persistence == null) {
            persistence = new Persistence();
            persistence.expiration = Instant.now().getEpochSecond() + 60L * 60L * 24L; // Stay for 12 hours.
            spawned.extra.setGrooming(persistence);
        }
        long now = System.currentTimeMillis();
        if (now < persistence.cooldown) return;
        boolean success;
        final int value;
        if (tool.activity == Activity.WASH) {
            if (persistence.wash >= 3) {
                player.sendActionBar(ChatColor.RED + spawned.data.getName() + ChatColor.RESET + ChatColor.RED + " is already clean.");
                return;
            }
            value = persistence.wash;
            if (tool == Tool.WATER_BUCKET && value == 0) {
                item.setType(Material.BUCKET);
                persistence.wash = 1;
                success = true;
            } else if (tool == Tool.SHAMPOO && value == 1) {
                persistence.wash = 2;
                success = true;
            } else if (tool == Tool.SWEAT_SCRAPER && value == 2) {
                persistence.wash = 3;
                spawned.data.setAppearance(spawned.data.getAppearance() + Activity.WASH.appearance);
                this.plugin.saveHorse(spawned.data);
                success = true;
            } else {
                success = false;
            }
        } else {
            if (persistence.wash < 3) {
                player.sendActionBar(ChatColor.RED + "Clean " + spawned.data.getName() + ChatColor.RESET + ChatColor.RED + " first.");
                return;
            }
            value = tool.activity.getter.apply(persistence);
            if (value >= tool.activity.maximum) {
                success = false;
            } else {
                tool.activity.setter.accept(persistence, value + 1);
                spawned.data.setAppearance(spawned.data.getAppearance() + tool.activity.appearance);
                this.plugin.saveHorse(spawned.data);
                success = true;
            }
        }
        // Consume item and save horse.
        if (success) {
            if (tool != Tool.WATER_BUCKET) {
                int uses = getUses(item) - 1;
                if (uses <= 0) {
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    item.setAmount(0);
                } else {
                    setUses(item, uses);
                    updateTool(item, tool, uses);
                }
            }
            persistence.cooldown = now + 500L;
            this.plugin.saveExtraData(spawned.data, EXTRA_DATA_KEY, persistence);
            String title = successNotification(player, spawned, tool, value);
            player.sendMessage(title);
            player.sendActionBar(title);
            successEffect(player, spawned, tool, value);
        } else {
            String title = failNotification(player, spawned, tool, value);
            player.sendMessage(ChatColor.RED + title);
            player.sendActionBar(ChatColor.RED + title);
        }
    }

    private String mask(String fmt, SpawnedHorse spawned, ChatColor... colors) {
        StringBuilder sb = new StringBuilder(colors[0].toString());
        for (int i = 1; i < colors.length; i += 1) sb.append(colors[i].toString());
        String c = sb.toString();
        return String.format(c + fmt, spawned.data.getMaskedName() + ChatColor.RESET + c);
    }

    String successNotification(Player player, SpawnedHorse spawned, Tool tool, int value) {
        HorseData data = spawned.data;
        switch (tool) {
        case WATER_BUCKET:  return mask("You soak %s.", spawned, ChatColor.DARK_BLUE);
        case SHAMPOO:       return mask("%s is now shampooed.", spawned, ChatColor.GREEN);
        case SWEAT_SCRAPER: return mask("You use the sweat scraper on %s.", spawned, ChatColor.GOLD);
        case CLIPPERS:      return mask("You use the clippers on %s.", spawned, ChatColor.DARK_GRAY);
        case HARD_BRUSH: {
            final String c = "" + ChatColor.GOLD;
            final String cc = "" + ChatColor.GOLD + ChatColor.BOLD;
            switch (value) {
            case 0:          return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " right shoulder.";
            case 1:          return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " right hip.";
            case 2:          return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " behind.";
            case 3:          return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " left hip.";
            case 4: default: return "" + cc + "You brush " + Util.genitive(data.getMaskedName()) + cc + " left shoulder.";
            }
        }
        case HOOF_PICK:
            switch (value) {
            case 0:          return mask("You pick the front left hoof of %s.", spawned, ChatColor.BLACK);
            case 1:          return mask("You pick the front right hoof of %s.", spawned, ChatColor.DARK_GRAY);
            case 2:          return mask("You pick the back right hoof of %s.", spawned, ChatColor.GRAY);
            case 3: default: return mask("You picked all the hooves of %s.", spawned, ChatColor.WHITE, ChatColor.BOLD);
            }
        case CURRY_COMB:
            switch (value) {
            case 0:          return mask("You comb %s once.", spawned, ChatColor.BLACK);
            case 1:          return mask("You comb %s twice.", spawned, ChatColor.DARK_GRAY);
            case 2:          return mask("You comb %s three times.", spawned, ChatColor.DARK_GRAY);
            case 3:          return mask("You comb %s four times.", spawned, ChatColor.GRAY);
            case 4:          return mask("You comb %s five times.", spawned, ChatColor.GRAY);
            case 5: default: return mask("You finish combing %s.", spawned, ChatColor.WHITE, ChatColor.BOLD);
            }
        case SHEDDING_BLADE:
            switch (value) {
            case 0: return          mask("You shed %s once.", spawned, ChatColor.RED);
            case 1: return          mask("You shed %s twice.", spawned, ChatColor.GOLD);
            case 2: default: return mask("You finish shedding %s.", spawned, ChatColor.GOLD, ChatColor.BOLD);
            }
        case MANE_AND_TAIL_COMB: {
            final String c = "" + ChatColor.LIGHT_PURPLE;
            final String cc = "" + ChatColor.LIGHT_PURPLE + ChatColor.BOLD;
            switch (value) {
            case 0:          return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " mane.";
            case 1: default: return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " tail.";
            }
        }
        case HOOF_OIL:
            switch (value) {
            case 0:          return mask("You oil the front left hoof of %s.", spawned, ChatColor.BLACK);
            case 1:          return mask("You oil the front right hoof of %s.", spawned, ChatColor.BLACK);
            case 2:          return mask("You oil the back right hoof of %s.", spawned, ChatColor.BLACK);
            case 3: default: return mask("You oiled all the hooves of %s.", spawned, ChatColor.BLACK, ChatColor.BOLD);
            }
        case SHOW_SHEEN:
            switch (value) {
            case 0:          return mask("You apply the show sheen on %s.", spawned, ChatColor.YELLOW);
            case 1:          return mask("You rub the show sheen on %s.", spawned, ChatColor.GOLD);
            case 2: default: return mask("%s is now all shiny.", spawned, ChatColor.GOLD, ChatColor.BOLD);
            }
        case FACE_BRUSH: {
            String c = "" + ChatColor.GRAY;
            String cc = "" + ChatColor.GRAY + ChatColor.BOLD;
            switch (value) {
            case 0:          return c + "You brush " + Util.genitive(data.getMaskedName()) + c + " face.";
            case 1: default: return cc + "You finish brushing " + Util.genitive(spawned.data.getMaskedName()) + cc + " face.";
            }
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
        case CLIPPERS: {
            w.playSound(l, Sound.ENTITY_SHEEP_SHEAR, SoundCategory.MASTER, v, 2.0f);
            w.spawnParticle(Particle.BLOCK_DUST, e, 24, r, r, r, 0.0, Material.WOOL.getNewData((byte)7)); // dark gray wool
            break;
        }
        case HARD_BRUSH: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case HOOF_PICK: {
            w.playSound(l, Sound.ENTITY_HORSE_GALLOP, SoundCategory.MASTER, v, 2.0f);
            w.spawnParticle(Particle.SMOKE_NORMAL, l, 8, r, 0.0, r, 0.0);
            break;
        }
        case CURRY_COMB: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case SHEDDING_BLADE: {
            w.playSound(l, Sound.ITEM_HOE_TILL, SoundCategory.MASTER, v, 0.8f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case MANE_AND_TAIL_COMB: {
            w.playSound(l, Sound.BLOCK_SAND_PLACE, SoundCategory.MASTER, v, 0.6f);
            w.spawnParticle(Particle.CRIT, e, 16, r, r, r, 0.5);
            break;
        }
        case FACE_BRUSH: {
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
        case WATER_BUCKET: return "Soak, then shampoo, then scrape.";
        case SHAMPOO: return "Soak, then shampoo, then scrape.";
        case SWEAT_SCRAPER: return "Soak, then shampoo, then scrape.";
        case CLIPPERS: return "Already clipped.";
        case HARD_BRUSH: return "Already brushed.";
        case HOOF_PICK: return "Already picked hooves.";
        case CURRY_COMB: return "Already combed.";
        case SHEDDING_BLADE: return "Already shed.";
        case MANE_AND_TAIL_COMB: return "Already brushed mane.";
        case HOOF_OIL: return "Already oiled hooves.";
        case SHOW_SHEEN: return "Already applied show sheen.";
        case FACE_BRUSH: return "Face sufficiently brushed.";
        default: throw new IllegalStateException("Unsupported tool: " + tool);
        }
    }
}
