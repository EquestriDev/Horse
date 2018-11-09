package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * All command classes are expected to subclass this lean utility
 * collection to get easy access to its methods and inner classes.
 *
 * CommandException can be thrown by most of the methods if anything
 * is not in order.  The command handlers are expected to catch this
 * exception and report its message back to the user.  This enables
 * lean error checking and elaborate user feedback.
 *
 * Implementing classes:
 * - HorseCommand - User interface (/horse, /h)
 * - AdminCommand - Admin interface (/horseadmin, /ha)
 * - EditCommand - Subcommand of (/ha)
 */
@RequiredArgsConstructor
abstract class CommandBase {
    protected final HorsePlugin plugin;

    static class CommandException extends Exception {
        CommandException(String message) {
            super(message);
        }
    }

    static final class PlayerExpectedException extends CommandException {
        PlayerExpectedException() {
            super("Player expected");
        }
    }

    protected List<String> tabComplete(String arg, List<String> args) {
        return args.stream().filter(s -> s.startsWith(arg)).collect(Collectors.toList());
    }

    protected List<String> tabComplete(String arg, Stream<String> args) {
        return args.filter(s -> s.startsWith(arg)).collect(Collectors.toList());
    }

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
    AbstractHorse interactedHorseOf(Player player) throws CommandException {
        AbstractHorse result = null;
        // If they are riding a horse, it's probably what they mean
        if (player.getVehicle() != null && player.getVehicle() instanceof AbstractHorse) {
            return (AbstractHorse)player.getVehicle();
        }
        // Next most likely horse is one on a lead.
        for (Entity e: player.getNearbyEntities(16.0, 16.0, 16.0)) {
            if (e instanceof AbstractHorse) {
                AbstractHorse entity = (AbstractHorse)e;
                if (entity.isLeashed() && entity.getLeashHolder().equals(player)) return entity;
            }
        }
        // Last chance; we attempt to figure out if the player is looking at a horse.
        Location location = player.getEyeLocation();
        Vector vector = location.getDirection().normalize().multiply(0.5);
        for (int i = 0; i < 10; i += 1) {
            location = location.add(vector);
            for (Entity e: location.getWorld().getNearbyEntities(location, 0.1, 0.1, 0.1)) {
                if (e instanceof AbstractHorse) return (AbstractHorse)e;
            }
        }
        // Give up
        throw new CommandException("Ride, leash, or look a horse first.");
    }

    SpawnedHorse spawnedHorseOf(AbstractHorse entity) throws CommandException {
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
        if (spawned == null) throw new CommandException("This is a feral horse.");
        return spawned;
    }

    SpawnedHorse spawnedHorseOf(HorseData data) throws CommandException {
        SpawnedHorse spawned = this.plugin.findSpawnedHorse(data);
        if (spawned == null || !spawned.isPresent()) throw new CommandException("Horse is not spawned.");
        return spawned;
    }

    String horseNameOf(HorseData data, String[] args) throws CommandException {
        final String name;
        if (args.length != 0) {
            StringBuilder sb = new StringBuilder(args[0]);
            for (int i = 1; i < args.length; i += 1) sb.append(" ").append(args[i]);
            name = sb.toString();
        } else {
            name = data.getName();
        }
        if (name == null || name.isEmpty()) throw new CommandException("Give this horse a name.");
        return name;
    }

    /**
     * Find all HorseData records which the player may be
     * be either an index for all horses belonging to them, or the
     * name of the horse.
     * referring to with the given arguments. Args is assumed to
     */
    HorseData ownedHorseOf(Player player, String[] args) throws CommandException {
        StringBuilder sb = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i += 1) sb.append(" ").append(args[i]);
        String arg = sb.toString();
        List<HorseData> playerHorses = this.plugin.findHorses(player);
        if (playerHorses.isEmpty()) throw new CommandException("You do not own any horses.");
        if (args.length == 0) return playerHorses.get(0);
        try {
            int index = Integer.parseInt(arg);
            if (index >= 1 && index <= playerHorses.size()) return playerHorses.get(index - 1);
        } catch (NumberFormatException nfe) { }
        for (HorseData data: playerHorses) {
            if (data.getName() != null && data.getName().equalsIgnoreCase(arg)) {
                return data;
            }
        }
        throw new CommandException("Horse not found: " + arg);
    }

    void updateHorseData(HorseData data) throws CommandException {
        if (!this.plugin.getDatabase().updateHorse(data)) {
            throw new CommandException("Horse claiming failed. Please contact an administrator.");
        }
    }

    Player expectPlayer(CommandSender sender) throws PlayerExpectedException {
        if (!(sender instanceof Player)) throw new PlayerExpectedException();
        return (Player)sender;
    }

    int expectInt(String arg) throws CommandException {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Not a number: " + arg);
        }
    }

    double expectDouble(String arg) throws CommandException {
        try {
            return Double.parseDouble(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Not a number: " + arg);
        }
    }

    HorseData horseWithId(String arg) throws CommandException {
        int id = expectInt(arg);
        if (id < 0) throw new CommandException("Positive number expected: " + id);
        HorseData data = this.plugin.findHorse(id);
        if (data == null) throw new CommandException("Horse not found: " + id);
        return data;
    }

    List<SpawnedHorse> nearbyAccessibleHorsesOf(Player player) throws CommandException {
        ArrayList<SpawnedHorse> result = new ArrayList<>();
        int anyNearby = 0;
        UUID playerId = player.getUniqueId();
        for (Entity e: player.getNearbyEntities(10.0, 10.0, 10.0)) {
            if (!(e instanceof AbstractHorse)) continue;
            anyNearby += 1;
            AbstractHorse entity = (AbstractHorse)e;
            SpawnedHorse spawned = this.plugin.findSpawnedHorse(entity);
            if (spawned == null) continue;
            if (!spawned.data.canAccess(playerId)) continue;
            result.add(spawned);
        }
        if (result.isEmpty()) {
            if (anyNearby > 0) {
                throw new CommandException("No nearby horse listens to you.");
            } else {
                throw new CommandException("No horse nearby.");
            }
        }
        return result;
    }
}
