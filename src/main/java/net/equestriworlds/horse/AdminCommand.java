package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;

final class AdminCommand extends CommandBase implements TabExecutor {
    private final List<String> commands = Arrays.asList("edit", "new", "move", "info", "deletebrand");

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
            player.sendMessage("Horse " + data.getName() + " navigating to you.");
            return true;
        }
        case "move": {
            if (args.length != 1 && args.length != 2) return false;
            Player player = expectPlayer(sender);
            HorseData data = horseWithId(args[0]);
            SpawnedHorse spawned = spawnedHorseOf(data);
            double speed = args.length < 2 ? 1.0 : expectDouble(args[1]);
            this.plugin.getDirtyPath().moveTowards(spawned.getEntity(), player.getLocation(), speed);
            player.sendMessage("Horse " + data.getName() + " moving to you.");
            return true;
        }
        case "info": {
            if (args.length != 1) return false;
            HorseData data = horseWithId(args[0]);
            sender.sendMessage("" + data);
            return true;
        }
        case "deletebrand": {
            if (args.length != 1) return false;
            String name = args[0];
            UUID ownerId = this.plugin.cachedPlayerUuid(name);
            HorseBrand horseBrand = this.plugin.getHorseBrands().remove(ownerId);
            if (horseBrand == null) throw new CommandException(name + " does not have a brand.");
            this.plugin.getDatabase().deleteHorseBrand(horseBrand.getOwner());
            sender.sendMessage("Horse brand deleted: " + horseBrand.getFormat() + ChatColor.RESET + ".");
        }
        default:
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return tabComplete(args[0], this.commands);
        return null;
    }
}
