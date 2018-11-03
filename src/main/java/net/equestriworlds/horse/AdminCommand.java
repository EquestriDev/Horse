package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;

final class AdminCommand extends CommandBase implements TabExecutor {
    public static final List<String> COMMANDS = Arrays.asList("claim", "list", "here", "bring", "info");

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
        case "edit": // /horse edit [edit args]
            return this.plugin.getEditCommand().onEditCommand(expectPlayer(sender), args);
        default: return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }
}
