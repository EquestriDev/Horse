package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;

public final class HorsePlugin extends JavaPlugin implements Listener {
    // --- Constants
    public static final String SCOREBOARD_MARKER = "equestriworlds.horse";
    public static final String SCOREBOARD_ID = "equestriworlds.id";
    // --- Horse Data
    private final Map<String, Map<Long, List<HorseData>>> chunkCache = new HashMap<>();
    private HorseDatabase database;
    private List<HorseData> horses;

    // --- JavaPlugin

    /**
     * Load all the horses and attempt to spawn them in where
     * necessary.
     */
    @Override
    public void onEnable() {
        this.database = new HorseDatabase(this);
        this.database.createTables();
        loadHorses();
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Remove all known horse entities. Save in the main thread if
     * necessary.
     */
    @Override
    public void onDisable() {
        for (HorseData data: horses) {
            Horse entity = data.findEntity();
            if (entity != null) entity.remove();
        }
        horses.clear();
        chunkCache.clear();
    }

    /**
     * Horse claiming, listing, and warping.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) {
            List<HorseData> list = findHorsesOf(player);
            if (list.isEmpty()) {
                return false;
            } else {
                player.sendMessage("");
                showHorseList(player, list);
                player.sendMessage("");
                return true;
            }
        }
        Horse horse;
        HorseData horseData;
        String name = null;
        switch (args[0]) {
        case "claim": case "c":
            horse = findInteractedHorseOf(player);
            if (horse == null) {
                player.sendMessage(ChatColor.RED + "Ride, leash, or look at the horse you want to claim.");
                return true;
            }
            if (horse.getScoreboardTags().contains(SCOREBOARD_MARKER)) {
                player.sendMessage(ChatColor.RED + "This horse has already been claimed.");
                return true;
            }
            // Create the HorseData list entry.
            horseData = new HorseData();
            horseData.storeProperties(horse);
            horseData.storeLocation(horse.getLocation());
            horseData.storeEntity(horse);
            horseData.storeOwner(player);
            // Build the name, if there is one.
            if (args.length >= 2) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
                name = sb.toString();
                horseData.setName(name);
                horse.setCustomName(name);
            } else {
                name = horse.getCustomName();
            }
            if (name == null) {
                player.sendMessage(ChatColor.RED + "Give this horse a name.");
                return true;
            }
            if (!this.database.saveHorse(horseData)) {
                player.sendMessage(ChatColor.RED + "Horse claiming failed. Please contact an administrator.");
                return true;
            }
            horses.add(horseData);
            // Mark the horse entity with marker, as well as the
            // unique id of the HorseData.
            horse.addScoreboardTag(SCOREBOARD_MARKER);
            horse.addScoreboardTag(SCOREBOARD_ID + "=" + horseData.getId());
            // Set the horse's owner.
            horse.setTamed(true);
            horse.setOwner(player);
            // Inform the player
            if (name == null) {
                player.sendMessage(ChatColor.GREEN + "You claimed this horse!");
            } else {
                player.sendMessage(ChatColor.GREEN + "You claimed the horse named " + name + "!");
            }
            horseClaimEffect(player, horse);
            return true;
        case "here": case "bring":
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Please specify a name.");
                return true;
            }
            if (true) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
                name = sb.toString();
            }
            horseData = findHorseDataWithArg(player, name);
            if (horseData == null) {
                player.sendMessage(ChatColor.RED + "Horse not found: " + name + ".");
                return true;
            }
            horse = horseData.findEntity();
            if (horse != null) {
                horse.teleport(player);
            } else {
                Location loc = player.getLocation();
                horse = loc.getWorld().spawn(loc, Horse.class, h -> horseData.applyProperties(h));
                horse.addScoreboardTag(SCOREBOARD_MARKER);
                horse.addScoreboardTag(SCOREBOARD_ID + horseData.getId());
                horseData.storeEntity(horse);
            }
            horseData.storeLocation(horse.getLocation());
            this.database.updateHorse(horseData);
            name = horseData.getName();
            if (name == null) {
                player.sendMessage(ChatColor.GREEN + "Horse teleported to you.");
            } else {
                player.sendMessage(ChatColor.GREEN + name + " teleported to you.");
            }
            horseTeleportEffect(player, horse.getLocation());
            return true;
        case "info":
            if (args.length >= 2) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
                name = sb.toString();
                horseData = findHorseDataWithArg(player, name);
                if (horseData == null) {
                    player.sendMessage(ChatColor.RED + "Horse not found: " + name);
                    return true;
                }
            } else {
                horse = findInteractedHorseOf(player);
                if (horse != null) {
                    horseData = findHorseDataOf(horse);
                    if (horseData == null) {
                        player.sendMessage(ChatColor.RED + "This horse has not been claimed.");
                        return true;
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Please specify a name.");
                    return true;
                }
            }
            int horseIndex = findHorsesOf(player).indexOf(horseData);
            player.sendMessage("");
            player.sendMessage("" + ChatColor.GREEN + ChatColor.BOLD + "Horse Information");
            player.spigot().sendMessage(describeHorse(horseData));
            player.spigot().sendMessage(new ComponentBuilder("").append("Summon ").color(ChatColor.DARK_GRAY).italic(true).append("[Bring]").italic(false).color(ChatColor.GREEN).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse here " + horseData.getName())).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/horse here " + horseData.getName() + "\n" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "Teleport " + horseData.getName() + " here."))).create());
            player.sendMessage("");
            return true;
        case "list": case "l":
            if (args.length == 1) {
                player.sendMessage("");
                showHorseList(player, findHorsesOf(player));
                player.sendMessage("");
                return true;
            }
            break;
        default:
            break;
        }
        return false;
    }

    // --- Horse

    void loadHorses() {
        this.horses = this.database.loadHorses();
        spawnAllHorses();
    }

    List<Horse> spawnAllHorses() {
        List<Horse> result = new ArrayList<>();
        for (HorseData data: this.horses) {
            HorseData.HorseLocation dataLoc = data.getLocation();
            if (dataLoc != null) {
                World world = dataLoc.bukkitWorld();
                if (world != null && world.isChunkLoaded(dataLoc.cx, dataLoc.cz)) {
                    Horse horse = spawnHorse(data, dataLoc.bukkitLocation());
                    if (horse != null) result.add(horse);
                }
            }
        }
        return result;
    }

    Horse spawnHorse(HorseData data, Location location) {
        Horse result = location.getWorld().spawn(location, Horse.class, h -> data.applyProperties(h));
        result.addScoreboardTag(SCOREBOARD_MARKER);
        result.addScoreboardTag(SCOREBOARD_ID + data.getId());
        data.storeEntity(result);
        data.storeLocation(location);
        this.database.updateHorse(data);
        return result;
    }

    /**
     * Show a list of horses to some player; they are assumed to
     * belong to the player.
     */
    void showHorseList(Player player, List<HorseData> playerHorses) {
        if (playerHorses.size() == 0) {
            player.sendMessage(ChatColor.RED + "You have no horses claimed.");
            return;
        }
        String nu;
        switch (playerHorses.size()) {
        case 1: nu = "one horse"; break;
        case 2: nu = "two horses"; break;
        case 3: nu = "three horses"; break;
        case 4: nu = "four horses"; break;
        case 5: nu = "five horses"; break;
        case 6: nu = "six horses"; break;
        case 7: nu = "seven horses"; break;
        case 8: nu = "eight horses"; break;
        case 9: nu = "nine horses"; break;
        case 10: nu = "ten horses"; break;
        case 11: nu = "eleven horses"; break;
        case 12: nu = "twelve horses"; break;
        default: nu = "" + playerHorses.size() + " horses";
        }
        player.sendMessage("" + ChatColor.GREEN + ChatColor.BOLD + "Your have " + nu + ". " + ChatColor.GRAY + ChatColor.ITALIC + "Click for more info.");
        int horseIndex = 0;
        for (HorseData playerHorse: playerHorses) {
            horseIndex += 1;
            player.spigot().sendMessage(new ComponentBuilder("\u2022 ").append(playerHorse.getName()).color(chatColorOf(playerHorse.getColor())).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse info " + horseIndex)).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, describeHorse(playerHorse))).create());
        }
    }

    /**
     * Auto complete all the commands. Complete the argument with the
     * names of a player's horses where applicable.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player)sender;
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length <= 1) {
            return Arrays.asList("claim", "list", "here", "info").stream().filter(s -> s.startsWith(arg)).collect(Collectors.toList());
        }
        switch (args[0]) {
        case "claim": return Collections.emptyList();
        case "here":
        case "info":
        case "bring":
            if (args.length == 2) {
                return findHorsesOf(player).stream().map(HorseData::getName).filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
            } else {
                return null;
            }
        default:
            break;
        }
        return null;
    }

    // --- Horse Finder Functions

    /**
     * Find the horse which a player may be referring to while
     * entering a related command right now. This may be any of the
     * following, in order.
     * - The horse they're riding on.
     * - One of the leashed horses.
     * - The horse they're looking at.
     *
     * We will assume optimistically that no more than one of the
     * above may apply at the same time, and that no more than one
     * horse will be leashed at any time.
     *
     * Leashed entities and looked at entities is slightly heuristic,
     * but all tests have proven successful without false positives.
     */
    Horse findInteractedHorseOf(Player player) {
        Horse result = null;
        // If they are riding a horse, it's probably what they mean
        if (player.getVehicle() != null && player.getVehicle() instanceof Horse) {
            return (Horse)player.getVehicle();
        }
        // Next most likely horse is one on a lead.
        for (Entity e: player.getNearbyEntities(16.0, 16.0, 16.0)) {
            if (e instanceof Horse) {
                Horse horse = (Horse)e;
                if (horse.isLeashed() && horse.getLeashHolder().equals(player)) return horse;
            }
        }
        // Last chance; we attempt to figure out if the player is looking at a horse.
        Location location = player.getEyeLocation();
        Vector vector = location.getDirection().normalize().multiply(0.5);
        for (int i = 0; i < 10; i += 1) {
            location = location.add(vector);
            for (Entity e: location.getWorld().getNearbyEntities(location, 0.1, 0.1, 0.1)) {
                if (e instanceof Horse) return (Horse)e;
            }
        }
        // Give up
        return null;
    }

    /**
     * Find the HorseData which describes the given horse
     * entity. The information will have been stored in the entity's
     * scoreboard tags. No tags means that this entity does not belong
     * to an HorseData. Existing tags with an invalid unique ID
     * means that this horse is a leftover invalid entity, but we
     * don't remove it at this point. Instead, we do so when the chunk
     * unloads or the entity is interacted with or damaged, which
     * should be enough to avoid confusing incidents.
     *
     * @return The HorseData record or null if none was found.
     */
    HorseData findHorseDataOf(Horse horse) {
        if (!horse.getScoreboardTags().contains(SCOREBOARD_MARKER)) return null;
        int horseId = -1;
        for (String tag: horse.getScoreboardTags()) {
            if (tag.startsWith(SCOREBOARD_ID)) {
                String id = tag.substring(SCOREBOARD_ID.length());
                try {
                    horseId = Integer.parseInt(id);
                } catch (NumberFormatException nfe) {
                    nfe.printStackTrace();
                    return null;
                }
                break;
            }
        }
        if (horseId < 0) return null;
        for (HorseData horseData: horses) {
            if (horseData.getId() == horseId) return horseData;
        }
        return null;
    }

    /**
     * Find all HorseData records belonging to the player.
     */
    List<HorseData> findHorsesOf(Player player) {
        return horses.stream().filter(h -> h.isOwner(player)).collect(Collectors.toList());
    }

    /**
     * Find all HorseData records which the player may be
     * referring to with the given argument. The latters is assumed to
     * be either an index for all horses belonging to them, or the
     * name of the horse.
     */
    HorseData findHorseDataWithArg(Player player, String arg) {
        List<HorseData> playerHorses = findHorsesOf(player);
        try {
            int index = Integer.parseInt(arg);
            if (index >= 1 && index <= playerHorses.size()) return playerHorses.get(index - 1);
        } catch (NumberFormatException nfe) { }
        for (HorseData playerHorse: playerHorses) {
            if (playerHorse.getName() != null && playerHorse.getName().equalsIgnoreCase(arg)) {
                return playerHorse;
            }
        }
        return null;
    }

    // --- Utility

    /**
     * Turn an enumeration into a nice human readable, capitalized
     * String. Also works with non-enums.
     * Example: STONE_BRICKS -> Stone Bricks
     */
    String niceEnumName(String in) {
        String[] toks = in.split("_");
        StringBuilder sb = new StringBuilder(toks[0].substring(0, 1).toUpperCase()).append(toks[0].substring(1).toLowerCase());
        for (int i = 1; i < toks.length; i += 1) {
            sb .append(" ")
                .append(toks[i].substring(0, 1).toUpperCase())
                .append(toks[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Get the approximate ChatColor which represents this horse
     * color.
     */
    static ChatColor chatColorOf(HorseColor c) {
        switch (c) {
        case BLACK: return ChatColor.BLACK;
        case BAY: return ChatColor.DARK_RED;
        case CHESTNUT: return ChatColor.RED;
        case CREAMY: return ChatColor.GOLD;
        case DARK_BAY: return ChatColor.DARK_GRAY;
        case GRAY: return ChatColor.GRAY;
        case WHITE: return ChatColor.WHITE;
        default: return ChatColor.WHITE;
        }
    }

    /**
     * Return all the properties of a horse which we will show to
     * players. Used for tooltips and the info command.
     */
    BaseComponent[] describeHorse(HorseData horse) {
        ChatColor c = chatColorOf(horse.getColor());
        return new BaseComponent[] {
            new TextComponent("" + c + ChatColor.BOLD + horse.getName()),
            new TextComponent("\nOwner " + ChatColor.GRAY + horse.getOwnerName()),
            new TextComponent("\nJump " + ChatColor.GRAY + String.format("%.2f", horse.getJumpStrength())),
            new TextComponent("\nSpeed " + ChatColor.GRAY + String.format("%.2f", horse.getMovementSpeed())),
            new TextComponent("\nColor " + c + horse.getColor().humanName),
            new TextComponent("\nMarkings " + ChatColor.GRAY + horse.getMarkings().humanName),
            new TextComponent("\nAge " + ChatColor.GRAY + (horse.getAge() < 0 ? "Baby" : "Adult"))
        };
    }

    // --- Event Handlers

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
        for (Entity entity: chunk.getEntities()) {
            if (entity instanceof Horse && entity.getScoreboardTags().contains(SCOREBOARD_MARKER)) {
                entity.remove();
            }
        }
        // Spawn in HorseData where they left.
        for (HorseData horseData: horsesInChunk(chunk)) {
            if (horseData.findEntity() == null && horseData.getLocation() != null) {
                spawnHorse(horseData, horseData.getLocation().bukkitLocation());
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
        for (Entity entity: chunk.getEntities()) {
            if (entity instanceof Horse && entity.getScoreboardTags().contains(SCOREBOARD_MARKER)) {
                Horse horse = (Horse)entity;
                HorseData horseData = findHorseDataOf(horse);
                if (horseData != null) {
                    horseData.storeProperties(horse);
                    horseData.storeEntity(horse);
                    horseData.storeLocation(horse.getLocation());
                    this.database.updateHorse(horseData);
                }
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
        if (!(holder instanceof Horse)) return;
        Horse horse = (Horse)holder;
        HorseData horseData = findHorseDataOf(horse);
        if (horseData == null) return;
        horseData.storeProperties(horse);
        horseData.storeLocation(horse.getLocation());
        this.database.updateHorse(horseData);
    }

    /**
     * When a horse is unmounted, it is likely to have moved. Record
     * the location.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof Horse)) return;
        Horse horse = (Horse)event.getDismounted();
        HorseData horseData = findHorseDataOf(horse);
        if (horseData == null) return;
        horseData.storeLocation(horse.getLocation());
        this.database.updateHorse(horseData);
    }

    /**
     * Use this event to check if a horse entity is a valid equestri
     * horse. Remove it if it is invalid due to some prior crash or
     * error.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Horse)) return;
        Horse horse = (Horse)event.getEntity();
        if (!horse.getScoreboardTags().contains(SCOREBOARD_MARKER)) return;
        HorseData horseData = findHorseDataOf(horse);
        if (horseData == null || !horse.getUniqueId().equals(horseData.getEntityId())) horse.remove();
    }

    /**
     * Same as above, but for right clicks.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse)) return;
        Horse horse = (Horse)event.getRightClicked();
        if (!horse.getScoreboardTags().contains(SCOREBOARD_MARKER)) return;
        HorseData horseData = findHorseDataOf(horse);
        if (horseData == null || !horse.getUniqueId().equals(horseData.getEntityId())) horse.remove();
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
        if (player.getVehicle() == null || !(player.getVehicle() instanceof Horse)) return;
        Horse horse = (Horse)player.getVehicle();
        HorseData horseData = findHorseDataOf(horse);
        if (horseData == null) return;
        horseData.storeLocation(horse.getLocation());
        horse.eject();
        this.database.updateHorse(horseData);
    }

    // --- Effects

    void horseClaimEffect(final Player player, final Horse horse) {
        new BukkitRunnable() {
            int ticks;
            @Override
            public void run() {
                if (!player.isValid() || !horse.isValid()) return;
                switch (ticks) {
                case 0: case 20: case 40: case 60: case 80: case 100:
                    player.spawnParticle(Particle.HEART, horse.getEyeLocation().add(0, 0.5, 0), 4, 0.4, 0.4, 0.4, 0.0);
                    break;
                case 1:
                    player.playSound(horse.getEyeLocation(), Sound.ENTITY_HORSE_ARMOR, SoundCategory.NEUTRAL, 0.45f, 1.25f);
                    break;
                case 21:
                    cancel();
                    return;
                default: break;
                }
                ticks += 1;
            }
        }.runTaskTimer(this, 1, 1);
    }

    void horseTeleportEffect(final Player player, Location loc) {
        new BukkitRunnable() {
            int ticks;
            @Override
            public void run() {
                if (!player.isValid()) return;
                switch (ticks) {
                case 0:
                    player.playSound(loc, Sound.ENTITY_HORSE_GALLOP, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                    break;
                case 10:
                    player.playSound(loc, Sound.ENTITY_HORSE_GALLOP, SoundCategory.NEUTRAL, 0.75f, 1.0f);
                    cancel();
                    return;
                default: break;
                }
                ticks += 1;
            }
        }.runTaskTimer(this, 1, 1);
    }

    // --- Chunk cache

    // TODO: Rewrite
    // void updateChunkCache() {
    //     if (chunkWorld != null) {
    //         Map<Long, List<HorseData>> worldCache = chunkCache.get(chunkWorld);
    //         if (worldCache != null) {
    //             List<HorseData> horseCache = worldCache.get(chunkCoord);
    //             if (horseCache != null) horseCache.remove(this);
    //         }
    //     }
    //     chunkWorld = world;
    //     chunkCoord = ((long)cz << 32) | (long)cx;
    //     if (chunkWorld != null) {
    //         Map<Long, List<HorseData>> worldCache = chunkCache.get(chunkWorld);
    //         if (worldCache == null) {
    //             worldCache = new HashMap<>();
    //             chunkCache.put(chunkWorld, worldCache);
    //         }
    //         List<HorseData> horseCache = worldCache.get(chunkCoord);
    //         if (horseCache == null) {
    //             horseCache = new ArrayList<>();
    //             worldCache.put(chunkCoord, horseCache);
    //         }
    //         horseCache.add(this);
    //     }
    // }

    List<HorseData> horsesInChunk(Chunk chunk) {
        List<HorseData> result = new ArrayList<>();
        Map<Long, List<HorseData>> worldCache = chunkCache.get(chunk.getWorld().getName());
        if (worldCache == null) return result;
        long c = ((long)chunk.getZ() << 32) | (long)chunk.getX();
        List<HorseData> horseCache = worldCache.get(c);
        if (horseCache != null) result.addAll(horseCache);
        return result;
    }
}
