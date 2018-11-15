package net.equestriworlds.horse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class AdminCommand extends CommandBase implements TabExecutor {
    private final List<String> commands = Arrays.asList("edit", "new", "move", "info", "deletebrand", "spawntool", "spawnfeed");

    AdminCommand(HorsePlugin plugin) {
        super(plugin);
    }

    // --- CommandExecutor

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        try {
            return onAdminCommand(sender, args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (PlayerExpectedException ce) {
            this.plugin.getLogger().info("Player expected");
            return true;
        } catch (CommandException ce) {
            sender.sendMessage(ChatColor.RED + ce.getMessage());
            return true;
        }
    }

    private boolean onAdminCommand(CommandSender sender, String cmd, String[] args) throws CommandException {
        switch (cmd) {
        case "edit": { // /horse edit [edit args]
            return this.plugin.getEditCommand().onEditCommand(expectPlayer(sender), args);
        }
        case "new": {
            return this.plugin.getEditCommand().newSession(expectPlayer(sender));
        }
        case "navigate": {
            if (args.length != 1 && args.length != 2) return false;
            Player player = expectPlayer(sender);
            HorseData data = horseWithId(args[0]);
            SpawnedHorse spawned = spawnedHorseOf(data);
            double speed = args.length < 2 ? 1.0 : expectDouble(args[1]);
            this.plugin.getDirtyPath().navigate(spawned.getEntity(), player.getLocation(), speed);
            player.sendMessage("Horse " + ChatColor.stripColor(data.getName()) + " navigating to you.");
            return true;
        }
        case "move": {
            if (args.length != 1 && args.length != 2) return false;
            Player player = expectPlayer(sender);
            HorseData data = horseWithId(args[0]);
            SpawnedHorse spawned = spawnedHorseOf(data);
            double speed = args.length < 2 ? 1.0 : expectDouble(args[1]);
            this.plugin.getDirtyPath().moveTowards(spawned.getEntity(), player.getLocation(), speed);
            player.sendMessage("Horse " + ChatColor.stripColor(data.getName()) + " moving to you.");
            return true;
        }
        case "info": {
            if (args.length != 1) return false;
            HorseData data = horseWithId(args[0]);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            sender.sendMessage(ChatColor.stripColor(gson.toJson(data)));
            return true;
        }
        case "deletebrand": {
            if (args.length != 1) return false;
            String name = args[0];
            UUID ownerId = this.plugin.cachedPlayerUuid(name);
            HorseBrand horseBrand = this.plugin.getHorseBrands().remove(ownerId);
            if (horseBrand == null) throw new CommandException(name + " does not have a brand.");
            this.plugin.getDatabase().deleteHorseBrand(horseBrand.getOwner());
            sender.sendMessage("Horse brand of " + name + " deleted: " + horseBrand.getFormat() + ChatColor.RESET + ".");
            return true;
        }
        case "spawntool": {
            if (args.length != 1 && args.length != 2) return false;
            Grooming.Tool tool;
            if (args[0].equals("all")) {
                tool = null;
            } else {
                try {
                    tool = Grooming.Tool.valueOf(args[0].toUpperCase());
                } catch (IllegalArgumentException iae) {
                    throw new CommandException("Unknown tool: " + args[0] + ".");
                }
            }
            Player target;
            if (args.length >= 2) {
                target = playerWithName(args[1]);
            } else if (sender instanceof Player) {
                target = (Player)sender;
            } else {
                throw new CommandException("Player expected");
            }
            for (Grooming.Tool itool: tool == null ? Arrays.asList(Grooming.Tool.values()) : Arrays.asList(tool)) {
                ItemStack item = this.plugin.getGrooming().spawnTool(itool);
                for (ItemStack drop: target.getInventory().addItem(item).values()) {
                    target.getWorld().dropItem(target.getEyeLocation(), drop);
                }
                sender.sendMessage("Tool " + itool.humanName + " given to " + target.getName());
            }
            return true;
        }
        case "spawnfeed": {
            if (args.length < 1 || args.length > 3) return false;
            Feeding.Feed feed;
            if (args[0].equals("all")) {
                feed = null;
            } else {
                try {
                    feed = Feeding.Feed.valueOf(args[0].toUpperCase());
                } catch (IllegalArgumentException iae) {
                    throw new CommandException("Unknown feed: " + args[0] + ".");
                }
            }
            int amount = 16;
            if (args.length >= 2) amount = expectInt(args[1]);
            Player target;
            if (args.length >= 3) {
                target = playerWithName(args[2]);
            } else if (sender instanceof Player) {
                target = (Player)sender;
            } else {
                throw new CommandException("Player expected");
            }
            for (Feeding.Feed ifeed: feed == null ? Arrays.asList(Feeding.Feed.values()) : Arrays.asList(feed)) {
                ItemStack item = this.plugin.getFeeding().spawnFeed(ifeed, amount);
                for (ItemStack drop: target.getInventory().addItem(item).values()) {
                    target.getWorld().dropItem(target.getEyeLocation(), drop);
                }
                sender.sendMessage("Feed " + amount + "x" + ifeed.humanName + " given to " + target.getName() + ".");
            }
            return true;
        }
        default:
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return tabComplete(args[0], this.commands);
        switch (args[0]) {
        case "spawntool":
            if (args.length == 2) return tabComplete(args[1], Stream.concat(Arrays.stream(Grooming.Tool.values()).map(Enum::name).map(String::toLowerCase), Stream.of("all")));
            break;
        case "spawnfeed":
            if (args.length == 2) return tabComplete(args[1], Stream.concat(Arrays.stream(Feeding.Feed.values()).map(Enum::name).map(String::toLowerCase), Stream.of("all")));
            break;
        default: break;
        }
        return null;
    }
}
