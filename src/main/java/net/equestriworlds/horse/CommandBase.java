package net.equestriworlds.horse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Command related utilities.  They are expected to throw an exception
 * if there is an error which should be reported to the user.
 */
@RequiredArgsConstructor
abstract class CommandBase {
    protected final HorsePlugin plugin;

    static final class CommandException extends Exception {
        CommandException(String message) {
            super(message);
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
        StringBuilder sb = new StringBuilder(args[1]);
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
}
