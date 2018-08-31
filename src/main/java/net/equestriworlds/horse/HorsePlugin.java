package net.equestriworlds.horse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;

public final class HorsePlugin extends JavaPlugin implements Listener {
    private List<EquestriHorse> horses;
    private boolean horsesDirty; // Were horses modified since the last save?
    public static final String SCORE_MARKER = "equestriworlds.horse";
    public static final String SCORE_ID = "equestriworlds.id=";

    /**
     * Load all the horses and attempt to spawn them in where
     * necessary.
     */
    @Override
    public void onEnable() {
        saveResource("horses.json", false);
        loadHorses();
        for (EquestriHorse horse: horses) {
            Location location = horse.getLocation();
            if (location != null && location.getWorld().isChunkLoaded(horse.getCx(), horse.getCz())) {
                Horse entity = location.getWorld().spawn(location, Horse.class, h -> horse.applyProperties(h));
                entity.addScoreboardTag(SCORE_MARKER);
                entity.addScoreboardTag(SCORE_ID + horse.getUniqueId());
                horse.storeEntity(entity);
                horsesDirty = true;
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> saveHorsesIncremental(), 10L, 10L);
    }

    /**
     * Remove all known horse entities. Save in the main thread if
     * necessary.
     */
    @Override
    public void onDisable() {
        if (horsesDirty) saveHorsesSync();
        for (EquestriHorse horse: horses) {
            Horse entity = horse.findEntity();
            if (entity != null) entity.remove();
        }
        horses.clear();
        EquestriHorse.clearChunkCache();
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
            List<EquestriHorse> list = findHorsesOf(player);
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
        EquestriHorse equestriHorse;
        String name = null;
        switch (args[0]) {
        case "claim": case "c":
            horse = findInteractedHorseOf(player);
            if (horse == null) {
                player.sendMessage(ChatColor.RED + "Ride, leash, or look at the horse you want to claim.");
                return true;
            }
            if (horse.getScoreboardTags().contains(SCORE_MARKER)) {
                player.sendMessage(ChatColor.RED + "This horse has already been claimed.");
                return true;
            }
            // Create the EquestriHorse list entry.
            equestriHorse = new EquestriHorse();
            UUID equestriId = new UUID((long)(horses.size() + 1), ThreadLocalRandom.current().nextLong());
            equestriHorse.setUniqueId(equestriId);
            equestriHorse.storeProperties(horse);
            equestriHorse.storeLocation(horse.getLocation());
            equestriHorse.storeEntity(horse);
            equestriHorse.storeOwner(player);
            // Build the name, if there is one.
            if (args.length >= 2) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
                name = sb.toString();
                equestriHorse.setName(name);
                horse.setCustomName(name);
            } else {
                name = horse.getCustomName();
            }
            if (name == null) {
                player.sendMessage(ChatColor.RED + "Give this horse a name.");
                return true;
            }
            horses.add(equestriHorse);
            saveHorses();
            // Mark the horse entity with marker, as well as the
            // unique id of the EquestriHorse.
            horse.addScoreboardTag(SCORE_MARKER);
            horse.addScoreboardTag(SCORE_ID + equestriId);
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
            equestriHorse = findEquestriHorseWithArg(player, name);
            if (equestriHorse == null) {
                player.sendMessage(ChatColor.RED + "Horse not found: " + name + ".");
                return true;
            }
            horse = equestriHorse.findEntity();
            if (horse != null) {
                horse.teleport(player);
            } else {
                Location loc = player.getLocation();
                horse = loc.getWorld().spawn(loc, Horse.class, h -> equestriHorse.applyProperties(h));
                horse.addScoreboardTag(SCORE_MARKER);
                horse.addScoreboardTag(SCORE_ID + equestriHorse.getUniqueId());
                equestriHorse.storeEntity(horse);
            }
            equestriHorse.storeLocation(horse.getLocation());
            horsesDirty = true;
            name = equestriHorse.getName();
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
                equestriHorse = findEquestriHorseWithArg(player, name);
                if (equestriHorse == null) {
                    player.sendMessage(ChatColor.RED + "Horse not found: " + name);
                    return true;
                }
            } else {
                horse = findInteractedHorseOf(player);
                if (horse != null) {
                    equestriHorse = findEquestriHorseOf(horse);
                    if (equestriHorse == null) {
                        player.sendMessage(ChatColor.RED + "This horse has not been claimed.");
                        return true;
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Please specify a name.");
                    return true;
                }
            }
            int horseIndex = findHorsesOf(player).indexOf(equestriHorse);
            player.sendMessage("");
            player.sendMessage("" + ChatColor.GREEN + ChatColor.BOLD + "Horse Information");
            player.spigot().sendMessage(describeHorse(equestriHorse));
            player.spigot().sendMessage(new ComponentBuilder("").append("Summon ").color(ChatColor.DARK_GRAY).italic(true).append("[Bring]").italic(false).color(ChatColor.GREEN).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse here " + equestriHorse.getName())).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/horse here " + equestriHorse.getName() + "\n" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "Teleport " + equestriHorse.getName() + " here."))).create());
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

    /**
     * Show a list of horses to some player; they are assumed to
     * belong to the player.
     */
    void showHorseList(Player player, List<EquestriHorse> playerHorses) {
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
        for (EquestriHorse playerHorse: playerHorses) {
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
                return findHorsesOf(player).stream().map(EquestriHorse::getName).filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
            } else {
                return null;
            }
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
     * Find the EquestriHorse which describes the given horse
     * entity. The information will have been stored in the entity's
     * scoreboard tags. No tags means that this entity does not belong
     * to an EquestriHorse. Existing tags with an invalid unique ID
     * means that this horse is a leftover invalid entity, but we
     * don't remove it at this point. Instead, we do so when the chunk
     * unloads or the entity is interacted with or damaged, which
     * should be enough to avoid confusing incidents.
     *
     * @return The EquestriHorse record or null if none was found.
     */
    EquestriHorse findEquestriHorseOf(Horse horse) {
        if (!horse.getScoreboardTags().contains(SCORE_MARKER)) return null;
        UUID uuid = null;
        for (String tag: horse.getScoreboardTags()) {
            if (tag.startsWith(SCORE_ID)) {
                String id = tag.substring(SCORE_ID.length());
                try {
                    uuid = UUID.fromString(id);
                    break;
                } catch (IllegalArgumentException iae) {
                    iae.printStackTrace();
                    return null;
                }
            }
        }
        if (uuid == null) return null;
        for (EquestriHorse equestriHorse: horses) {
            if (uuid.equals(equestriHorse.getUniqueId())) return equestriHorse;
        }
        return null;
    }

    /**
     * Find all EquestriHorse records belonging to the player.
     */
    List<EquestriHorse> findHorsesOf(Player player) {
        return horses.stream().filter(h -> h.isOwner(player)).collect(Collectors.toList());
    }

    /**
     * Find all EquestriHorse records which the player may be
     * referring to with the given argument. The latters is assumed to
     * be either an index for all horses belonging to them, or the
     * name of the horse.
     */
    EquestriHorse findEquestriHorseWithArg(Player player, String arg) {
        List<EquestriHorse> playerHorses = findHorsesOf(player);
        try {
            int index = Integer.parseInt(arg);
            if (index >= 1 && index <= playerHorses.size()) return playerHorses.get(index - 1);
        } catch (NumberFormatException nfe) { }
        for (EquestriHorse playerHorse: playerHorses) {
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
    static ChatColor chatColorOf(Horse.Color c) {
        switch (c) {
        case BLACK: return ChatColor.BLACK;
        case BROWN: return ChatColor.DARK_RED;
        case CHESTNUT: return ChatColor.RED;
        case CREAMY: return ChatColor.GOLD;
        case DARK_BROWN: return ChatColor.DARK_GRAY;
        case GRAY: return ChatColor.GRAY;
        case WHITE: return ChatColor.WHITE;
        default: return ChatColor.WHITE;
        }
    }

    /**
     * Return all the properties of a horse which we will show to
     * players. Used for tooltips and the info command.
     */
    BaseComponent[] describeHorse(EquestriHorse horse) {
        ChatColor c = chatColorOf(horse.getColor());
        return new BaseComponent[] {
            new TextComponent("" + c + ChatColor.BOLD + horse.getName()),
            new TextComponent("\nOwner " + ChatColor.GRAY + horse.getOwnerName()),
            new TextComponent("\nJump " + ChatColor.GRAY + String.format("%.2f", horse.getJumpStrength())),
            new TextComponent("\nSpeed " + ChatColor.GRAY + String.format("%.2f", horse.getMovementSpeed())),
            new TextComponent("\nStyle " + ChatColor.GRAY + niceEnumName(horse.getStyle().name())),
            new TextComponent("\nColor " + c + niceEnumName(horse.getColor().name())),
            new TextComponent("\nAge " + ChatColor.GRAY + (horse.getAge() < 0 ? "Baby" : "Adult"))
        };
    }

    // --- Loading and Saving

    /**
     * Load all horses from the JSON file, deleting whatever was in
     * memory before.
     */
    void loadHorses() {
        Gson gson = new Gson();
        EquestriHorse.clearChunkCache();
        try {
            horses = gson.fromJson(new FileReader(new File(getDataFolder(), "horses.json")), new TypeToken<List<EquestriHorse>>(){}.getType());
        } catch (IOException ioe) {
            System.err.println("Loading horses failed:");
            ioe.printStackTrace();
            horses = new ArrayList<>();
            return;
        }
        for (EquestriHorse horse: horses) horse.updateChunkCache();
    }

    /**
     * Save all horses only if the data were marked as dirty after the
     * previous save.
     */
    private void saveHorsesIncremental() {
        if (!horsesDirty) return;
        saveHorses();
    }

    /**
     * Save all horses. The blocking write to file will happen in an
     * async thread.
     */
    void saveHorses() {
        horsesDirty = false;
        Gson gson = new Gson();
        String json = gson.toJson(horses);
        File file = new File(getDataFolder(), "horses.json");
        getServer().getScheduler().runTaskAsynchronously(this, () -> saveFile(json, file));
    }

    /**
     * Same as above but in the main thread. Only used when the plugin
     * is disabled and thread scheduling is illegal.
     */
    void saveHorsesSync() {
        horsesDirty = false;
        Gson gson = new Gson();
        String json = gson.toJson(horses);
        File file = new File(getDataFolder(), "horses.json");
        saveFile(json, file);
    }

    /**
     * Helper method to write a String to a file. Used by the
     * saveHorses() methods.
     */
    private synchronized void saveFile(String str, File file) {
        try {
            PrintWriter pw = new PrintWriter(file);
            pw.write(str);
            pw.flush();
            pw.close();
        } catch (IOException ioe) {
            System.err.println("Saving file failed: " + file.getName());
            ioe.printStackTrace();
        }
    }

    // --- Event Handlers

    /**
     * Remove any horse entities marked as EquestriHorse from the
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
        // Remove stray EquestriHorses.
        for (Entity entity: chunk.getEntities()) {
            if (entity instanceof Horse && entity.getScoreboardTags().contains(SCORE_MARKER)) {
                entity.remove();
            }
        }
        // Spawn in EquestriHorses where they left.
        for (EquestriHorse horse: EquestriHorse.horsesInChunk(chunk)) {
            if (horse.findEntity() == null) {
                Horse entity = chunk.getWorld().spawn(horse.getLocation(), Horse.class, h -> horse.applyProperties(h));
                entity.addScoreboardTag(SCORE_MARKER);
                entity.addScoreboardTag(SCORE_ID + horse.getUniqueId());
                horse.storeEntity(entity);
                horsesDirty = true;
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
            if (entity instanceof Horse && entity.getScoreboardTags().contains(SCORE_MARKER)) {
                Horse horse = (Horse)entity;
                EquestriHorse equestriHorse = findEquestriHorseOf(horse);
                if (equestriHorse != null) {
                    equestriHorse.storeProperties(horse);
                    equestriHorse.storeEntity(horse);
                    equestriHorse.storeLocation(horse.getLocation());
                    horsesDirty = true;
                }
            }
        }
    }

    /**
     * When a horse's inventory is closed, there is a chance that it
     * was modified. Use this opportunity to record the inventory into
     * the EquestriHorse record.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Horse)) return;
        Horse horse = (Horse)holder;
        EquestriHorse equestriHorse = findEquestriHorseOf(horse);
        if (equestriHorse == null) return;
        equestriHorse.storeProperties(horse);
        equestriHorse.storeLocation(horse.getLocation());
        horsesDirty = true;
    }

    /**
     * When a horse is unmounted, it is likely to have moved. Record
     * the location.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof Horse)) return;
        Horse horse = (Horse)event.getDismounted();
        EquestriHorse equestriHorse = findEquestriHorseOf(horse);
        if (equestriHorse == null) return;
        equestriHorse.storeLocation(horse.getLocation());
        horsesDirty = true;
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
        if (!horse.getScoreboardTags().contains(SCORE_MARKER)) return;
        EquestriHorse equestriHorse = findEquestriHorseOf(horse);
        if (equestriHorse == null || !horse.getUniqueId().equals(equestriHorse.getEntityId())) horse.remove();
    }

    /**
     * Same as above, but for right clicks.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse)) return;
        Horse horse = (Horse)event.getRightClicked();
        if (!horse.getScoreboardTags().contains(SCORE_MARKER)) return;
        EquestriHorse equestriHorse = findEquestriHorseOf(horse);
        if (equestriHorse == null || !horse.getUniqueId().equals(equestriHorse.getEntityId())) horse.remove();
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
        EquestriHorse equestriHorse = findEquestriHorseOf(horse);
        if (equestriHorse == null) return;
        equestriHorse.storeLocation(horse.getLocation());
        horsesDirty = true;
        horse.eject();
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
}
