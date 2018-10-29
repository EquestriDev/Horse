package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;

final class HorseCommand extends CommandBase implements TabExecutor {
    public static final List<String> COMMANDS = Arrays.asList("claim", "list", "here", "bring", "info");

    HorseCommand(HorsePlugin plugin) {
        super(plugin);
    }

    // --- CommandExecutor

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            this.plugin.getLogger().info("Player expected");
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) return showHorseList(player);
        try {
            return onCommand(player, args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (CommandException cex) {
            player.sendMessage(ChatColor.RED + cex.getMessage());
            return true;
        }
    }

    private boolean onCommand(Player player, String cmd, String[] args) throws CommandException {
        switch (cmd) {
        case "claim": case "c": {
            AbstractHorse entity = this.interactedHorseOf(player);
            SpawnedHorse spawned = this.spawnedHorseOf(entity);
            String name = this.horseNameOf(spawned.data, args);
            // Update and save HorseData
            spawned.data.storeOwner(player);
            spawned.data.setName(name);
            spawned.data.storeLocation(entity.getLocation());
            updateHorseData(spawned.data);
            // Update the horse entity.
            entity.setTamed(true);
            entity.setOwner(player);
            player.sendMessage(ChatColor.GREEN + "You claimed the horse named " + name + "!");
            HorseEffects.claim(plugin, player, entity);
            return true;
        }
        case "here": case "bring": {
            HorseData data = this.ownedHorseOf(player, args);
            this.plugin.teleportHorse(data, player.getLocation());
            player.sendMessage(ChatColor.GREEN + data.getName() + " teleported to you.");
            return true;
        }
        case "info": {
            HorseData data;
            if (args.length == 0) {
                AbstractHorse entity = this.interactedHorseOf(player);
                SpawnedHorse spawned = this.spawnedHorseOf(entity);
                data = spawned.data;
            } else {
                data = this.ownedHorseOf(player, args);
            }
            player.sendMessage("");
            player.sendMessage("" + ChatColor.GREEN + ChatColor.BOLD + "Horse Information");
            player.spigot().sendMessage(describeHorse(data));
            player.spigot().sendMessage(new ComponentBuilder("").append("Summon ").color(ChatColor.DARK_GRAY).italic(true).append("[Bring]").italic(false).color(ChatColor.GREEN).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse here " + data.getName())).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/horse here " + data.getName() + "\n" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "Teleport " + data.getName() + " here."))).create());
            player.sendMessage("");
            return true;
        }
        case "list": case "l": {
            if (args.length == 1) {
                player.sendMessage("");
                showHorseList(player, this.plugin.findHorses(player));
                player.sendMessage("");
                return true;
            }
            return true;
        }
        default: return false;
        }
    }

    // --- TabCompleter

    /**
     * Auto complete all the commands. Complete the argument with the
     * names of a player's horses where applicable.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player)sender;
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length <= 1) return tabComplete(args[0], COMMANDS);
        switch (args[0]) {
        case "claim":
            return Collections.emptyList();
        case "here":
        case "info":
        case "bring":
            if (args.length == 2) return tabComplete(args[0], this.plugin.findHorses(player).stream().map(HorseData::getName).map(String::toLowerCase));
            return null;
        default:
            return Collections.emptyList();
        }
    }

    // --- Helpers

    boolean showHorseList(Player player) {
        List<HorseData> list = this.plugin.findHorses(player);
        if (list.isEmpty()) {
            return false;
        } else {
            player.sendMessage("");
            showHorseList(player, list);
            player.sendMessage("");
            return true;
        }
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
        for (HorseData data: playerHorses) {
            horseIndex += 1;
            player.spigot().sendMessage(new ComponentBuilder("\u2022 ").append(data.getName()).color(chatColorOf(data)).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse info " + horseIndex)).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, describeHorse(data))).create());
        }
    }

    /**
     * Return all the properties of a horse which we will show to
     * players. Used for tooltips and the info command.
     */
    BaseComponent[] describeHorse(HorseData horse) {
        ChatColor c = chatColorOf(horse);
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

    // --- Utility

    /**
     * Get the approximate ChatColor which represents this horse
     * color.
     */
    static ChatColor chatColorOf(HorseData data) {
        switch (data.getGender()) {
        case MARE: return ChatColor.LIGHT_PURPLE;
        case STALLION: return ChatColor.BLUE;
        case GELDING: default: return ChatColor.GREEN;
        }
    }
}
