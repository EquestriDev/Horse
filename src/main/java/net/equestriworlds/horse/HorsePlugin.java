package net.equestriworlds.horse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class HorsePlugin extends JavaPlugin implements Listener {
    private List<EquestriHorse> horses;
    public static final String SCORE_MARKER = "equestriworlds.horse";
    public static final String SCORE_ID = "equestriworlds.id=";

    @Override
    public void onEnable() {
        saveResource("horses.json", false);
        loadHorses();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        horses.clear();
        EquestriHorse.clearChunkCache();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) return false;
        Horse horse;
        EquestriHorse equestriHorse;
        String name = null;
        switch (args[0]) {
        case "claim":
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
            equestriHorse.storeAttributes(horse);
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
            return true;
        case "here":
            if (args.length >= 2) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
                name = sb.toString();
                equestriHorse = findEquestriHorseWithArg(player, name);
                if (equestriHorse == null) {
                    player.sendMessage(ChatColor.RED + "Horse not found: " + name + ".");
                    return true;
                }
            } else {
                equestriHorse = findMainHorseOf(player);
                if (equestriHorse == null) {
                    player.sendMessage(ChatColor.RED + "You have no horse claimed.");
                    return true;
                }
            }
            horse = equestriHorse.findEntity();
            if (horse != null) {
                horse.teleport(player);
            } else {
                Location loc = player.getLocation();
                horse = loc.getWorld().spawn(loc, Horse.class, h -> equestriHorse.applyAttributes(h));
                horse.addScoreboardTag(SCORE_MARKER);
                horse.addScoreboardTag(SCORE_ID + equestriHorse.getUniqueId());
            }
            equestriHorse.storeEntity(horse);
            equestriHorse.storeLocation(horse.getLocation());
            saveHorses();
            name = equestriHorse.getName();
            if (name == null) {
                player.sendMessage(ChatColor.GREEN + "Horse teleported to you.");
            } else {
                player.sendMessage(ChatColor.GREEN + name + " teleported to you.");
            }
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
                equestriHorse = findInteractedHorseOf(player);
                if (equestriHorse == null) {
                    player.sendMessage(ChatColor.RED + "You have no horse claimed.");
                    return true;
                }
            }
            player.sendMessage(ChatColor.GREEN + "=== Horse Information ===");
            player.sendMessage(ChatColor.WHITE + "Name " + ChatColor.GRAY + equestriHorse.getName());
            player.sendMessage(ChatColor.WHITE + "Jump " + ChatColor.GRAY + equestriHorse.getJumpStrength());
            player.sendMessage(ChatColor.WHITE + "Speed " + ChatColor.GRAY + equestriHorse.getMovementSpeed());
            player.sendMessage(ChatColor.WHITE + "Age " + ChatColor.GRAY + (equestriHorse.getAge() < 0 ? "Baby" : "Adult"));
            break;
        }
        return false;
    }

    // --- Horse Finder Functions

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

    List<EquestriHorse> findHorsesOf(Player player) {
        return horses.stream().filter(h -> h.isOwner(player)).collect(Collectors.toList());
    }

    EquestriHorse findEquestriHorseWithArg(Player player, String arg) {
        List<EquestriHorse> playerHorses = findHorsesOf(player);
        try {
            int index = Integer.parseInt(arg);
            if (index >= 0 && index < playerHorses.size()) return playerHorses.get(index);
        } catch (NumberFormatException nfe) { }
        for (EquestriHorse playerHorse: playerHorses) {
            if (playerHorse.getName() != null && playerHorse.getName().equalsIgnoreCase(arg)) {
                return playerHorse;
            }
        }
        return null;
    }

    EquestriHorse findMainHorseOf(Player player) {
        for (EquestriHorse horse: horses) {
            if (horse.isOwner(player)) return horse;
        }
        return null;
    }

    // --- Loading and Saving

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

    void saveHorses() {
        Gson gson = new Gson();
        try {
            gson.toJson(horses, new FileWriter(new File(getDataFolder(), "horses.json")));
        } catch (IOException ioe) {
            System.err.println("Saving horses failed:");
            ioe.printStackTrace();
        }
    }

    // --- Event Handlers

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
        boolean saving = false;
        for (EquestriHorse horse: EquestriHorse.horsesInChunk(chunk)) {
            if (horse.findEntity() == null) {
                Horse entity = chunk.getWorld().spawn(horse.getLocation(), Horse.class, h -> horse.applyAttributes(h));
                horse.storeEntity(entity);
                saving = true;
            }
        }
        saveHorses();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onChunkUnload(ChunkUnloadEvent event) {
        final Chunk chunk = event.getChunk();
        boolean saving = false;
        for (Entity entity: chunk.getEntities()) {
            if (entity instanceof Horse && entity.getScoreboardTags().contains(SCORE_MARKER)) {
                Horse horse = (Horse)entity;
                EquestriHorse equestriHorse = findEquestriHorseOf(horse);
                if (equestriHorse != null) {
                    equestriHorse.storeAttributes(horse);
                    equestriHorse.storeEntity(horse);
                    equestriHorse.storeLocation(horse.getLocation());
                    saving = true;
                }
                horse.remove();
            }
        }
        if (saving) saveHorses();
    }
}
