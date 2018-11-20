package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;

final class HorseCommand extends CommandBase implements TabExecutor {
    public final List<String> commands = Arrays.asList("claim", "rename", "list", "here", "bring", "info", "follow", "unfollow", "trust", "untrust", "registerbrand", "brandlist");

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
        if (args.length == 0) return false;
        try {
            return onCommand(player, args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (CommandException cex) {
            player.sendMessage(ChatColor.RED + cex.getMessage());
            return true;
        }
    }

    private boolean onCommand(Player player, String cmd, String[] args) throws CommandException {
        switch (cmd) {
        case "list": case "l": {
            new HorseGUI(this.plugin).horseList(player).open(player);
            return true;
        }
        case "claim": case "c": {
            AbstractHorse entity = this.interactedHorseOf(player);
            SpawnedHorse spawned = this.spawnedHorseOf(entity);
            if (spawned.data.getOwner() != null) throw new CommandException("Already claimed!");
            // Update and save HorseData
            if (args.length > 0) spawned.data.setName(this.horseNameOf(args)); // throws
            spawned.data.storeOwner(player);
            spawned.data.storeLocation(entity.getLocation());
            updateHorseData(spawned.data);
            // Update the horse entity.
            entity.setTamed(true);
            entity.setOwner(player);
            player.sendMessage(ChatColor.GREEN + "You claimed the horse named " + spawned.data.getName() + ChatColor.RESET + ChatColor.GREEN + "!");
            player.playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.25f, 1.25f);
            HorseEffects.claim(plugin, player, entity);
            return true;
        }
        case "rename": {
            if (args.length == 0) return false;
            AbstractHorse entity = this.interactedHorseOf(player);
            SpawnedHorse spawned = this.spawnedHorseOf(entity);
            if (!spawned.data.isOwner(player)) throw new CommandException(spawned.data.getStrippedName() + " does not belong to you!");
            String name = this.horseNameOf(args);
            spawned.data.setName(name);
            this.plugin.getDatabase().updateHorse(spawned.data);
            this.plugin.updateHorseEntity(spawned.data);
            player.sendMessage(ChatColor.GREEN + "Renamed to " + spawned.data.getName() + ChatColor.RESET + ChatColor.GREEN + ".");
            player.playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.25f, 1.25f);
            return true;
        }
        case "here": case "bring": {
            if (args.length == 0) return false;
            HorseData data = this.ownedHorseOf(player, args);
            this.plugin.teleportHorse(data, player.getLocation());
            player.sendMessage(ChatColor.GREEN + data.getName() + ChatColor.RESET + ChatColor.GREEN + " teleported to you.");
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_HORSE_LAND, SoundCategory.MASTER, 1.0f, 1.0f);
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
            sendHorseInfo(player, data);
            return true;
        }
        case "follow": {
            if (args.length != 0) return false;
            UUID playerId = player.getUniqueId();
            int followed = 0;
            for (SpawnedHorse spawned: nearbyAccessibleHorsesOf(player)) {
                spawned.setFollowing(playerId);
                followed += 1;
                player.sendMessage(ChatColor.GOLD + spawned.data.getName() + ChatColor.RESET + ChatColor.GOLD + " is now following you.");
                player.playSound(spawned.getEntity().getEyeLocation(), Sound.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                player.spawnParticle(Particle.VILLAGER_HAPPY, spawned.getEntity().getEyeLocation(), 8, 0.5, 0.5, 0.5, 0.0);
            }
            player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.PLAYERS, 0.1f, 1.7f);
            if (followed == 0) throw new CommandException("No horse can hear you.");
            return true;
        }
        case "unfollow": {
            if (args.length != 0) return false;
            UUID playerId = player.getUniqueId();
            int unfollowed = 0;
            for (SpawnedHorse spawned: nearbyAccessibleHorsesOf(player)) {
                if (playerId.equals(spawned.getFollowing())) {
                    spawned.setFollowing(null);
                    unfollowed += 1;
                    player.sendMessage(ChatColor.YELLOW + spawned.data.getName() + ChatColor.RESET + ChatColor.YELLOW + " is no longer following you.");
                    player.playSound(spawned.getEntity().getEyeLocation(), Sound.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                    player.spawnParticle(Particle.VILLAGER_ANGRY, spawned.getEntity().getEyeLocation(), 4, 0.5, 0.5, 0.5, 0.0);
                }
            }
            if (unfollowed == 0) throw new CommandException("No nearby horse is following you.");
            return true;
        }
        case "trust": {
            if (args.length != 1) return false;
            SpawnedHorse spawned = spawnedHorseOf(interactedHorseOf(player));
            if (!spawned.data.isOwner(player)) throw new CommandException("You do not own " + spawned.data.getStrippedName() + ".");
            Player target = playerWithName(args[0]);
            if (spawned.data.canAccess(target)) throw new CommandException(target.getName() + " can already access " + spawned.data.getStrippedName() + ".");
            spawned.data.getTrusted().add(target.getUniqueId());
            this.plugin.getDatabase().updateHorse(spawned.data);
            player.sendMessage(ChatColor.GOLD + "Trusted " + target.getName() + " to access " + spawned.data.getName() + ChatColor.RESET + ChatColor.GOLD + ".");
            target.sendMessage(ChatColor.GOLD + player.getName() + " trusted you to access their horse " + spawned.data.getName() + ChatColor.RESET + ChatColor.GOLD + ".");
            HorseEffects.friendJingle(this.plugin, player);
            HorseEffects.friendJingle(this.plugin, target);
            return true;
        }
        case "untrust": {
            if (args.length != 1) return false;
            SpawnedHorse spawned = spawnedHorseOf(interactedHorseOf(player));
            if (!spawned.data.isOwner(player)) throw new CommandException("You do not own " + spawned.data.getStrippedName() + ".");
            UUID revokee = playerUuidOf(args[0]);
            if (!spawned.data.getTrusted().remove(revokee)) throw new CommandException(args[0] + " is not trusted to use " + spawned.data.getStrippedName() + ".");
            this.plugin.getDatabase().updateHorse(spawned.data);
            player.sendMessage(ChatColor.YELLOW + "Removed trust for " + args[0] + " from " + spawned.data.getName() + ChatColor.RESET + ChatColor.YELLOW + ".");
            HorseEffects.unfriendJingle(this.plugin, player);
            return true;
        }
        case "registerbrand": {
            if (args.length < 1) return false;
            if (null != this.plugin.getHorseBrands().get(player.getUniqueId())) throw new CommandException("You already have a brand.");
            StringBuilder sb = new StringBuilder(args[0]);
            for (int i = 1; i < args.length; i += 1) sb.append(" ").append(args[i]);
            String format = ChatColor.translateAlternateColorCodes('&', sb.toString());
            String formatNoColor = ChatColor.stripColor(format);
            if (formatNoColor.length() > 6) throw new CommandException("This brand is too long.");
            HorseBrand horseBrand = new HorseBrand(player.getUniqueId(), format);
            double price = this.plugin.getConfig().getDouble("brands.RegistrationPrice", 250000.0);
            String priceFormat = this.plugin.formatMoney(price);
            if (!this.plugin.takePlayerMoney(player, price)) throw new CommandException("You cannot afford " + priceFormat + ".");
            this.plugin.getDatabase().saveHorseBrand(horseBrand);
            this.plugin.getHorseBrands().put(player.getUniqueId(), horseBrand);
            player.sendMessage(ChatColor.GOLD + "You have registered the brand " + ChatColor.RESET + format + ChatColor.RESET + ChatColor.GOLD + " for " + ChatColor.YELLOW + ChatColor.ITALIC + priceFormat + ChatColor.RESET + ChatColor.GOLD + ".");
            player.playSound(player.getEyeLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.5f, 1.0f);
            return true;
        }
        case "brandlist": {
            if (args.length != 0) return false;
            new HorseGUI(this.plugin).brandList(0).open(player);
            return true;
        }
        case "brand": {
            if (args.length != 0) return false;
            SpawnedHorse spawned = spawnedHorseOf(interactedHorseOf(player));
            if (!spawned.data.isOwner(player)) throw new CommandException("This horse does not belong to you.");
            HorseBrand horseBrand = this.plugin.getHorseBrands().get(player.getUniqueId());
            if (horseBrand == null) throw new CommandException("You don't have a registered brand.");
            if (spawned.data.getBrand() != null) throw new CommandException("This horse is already branded.");
            if (spawned.data.getAge() != HorseAge.FOAL) throw new CommandException("You can only brand a foal.");
            double price = this.plugin.getConfig().getDouble("brands.ApplicationPrice", 10000.0);
            String priceFormat = this.plugin.formatMoney(price);
            if (!this.plugin.takePlayerMoney(player, price)) throw new CommandException("You cannot afford " + priceFormat + ".");
            spawned.data.setBrand(horseBrand);
            this.plugin.getDatabase().updateHorse(spawned.data);
            player.sendMessage(ChatColor.GOLD + spawned.data.getName() + ChatColor.RESET + ChatColor.GOLD + " was branded with " + ChatColor.RESET + horseBrand.getFormat() + ChatColor.RESET + ChatColor.GOLD + " for " + ChatColor.YELLOW + ChatColor.ITALIC + priceFormat + ChatColor.RESET + ChatColor.GOLD + ".");
            player.getWorld().playSound(spawned.getEntity().getEyeLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.NEUTRAL, 0.5f, 1.0f);
            player.getWorld().playSound(spawned.getEntity().getEyeLocation(), Sound.ENTITY_HORSE_HURT, SoundCategory.NEUTRAL, 0.5f, 1.0f);
            player.getWorld().spawnParticle(Particle.LAVA, spawned.getEntity().getLocation(), 32, 0.25, 0.0, 0.25, 0.0);
            return true;
        }
        default: return false;
        }
    }

    void sendHorseInfo(Player player, HorseData data) {
            player.sendMessage("");
            player.sendMessage(""
                               + ChatColor.YELLOW + ChatColor.STRIKETHROUGH + "            "
                               + ChatColor.YELLOW + "[ "
                               + ChatColor.GOLD + ChatColor.BOLD + "Horse Info"
                               + ChatColor.YELLOW + " ]"
                               + ChatColor.YELLOW + ChatColor.STRIKETHROUGH + "            ");
            for (BaseComponent bc: this.describeHorse(data)) player.spigot().sendMessage(bc);
            ComponentBuilder cb = new ComponentBuilder("")
                .append("Options").color(ChatColor.DARK_GRAY).italic(true);
            cb.append("  ").reset();
            cb.append("[Bring]").italic(false).color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse here " + data.getStrippedName()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/horse here " + data.getStrippedName() + "\n" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "Teleport " + data.getStrippedName() + " here.")));
            cb.append("  ").reset();
            cb.append("[Rename]").italic(false).color(ChatColor.BLUE)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/horse rename " + data.getStrippedName()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.BLUE + "/horse rename " + data.getStrippedName() + "\n" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "Change the name of " + data.getStrippedName() + ".")));
            cb.append("  ").reset();
            cb.append("[Info]").italic(false).color(ChatColor.YELLOW)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horse info"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "/horse info\n" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "View this info screen.")));
            player.spigot().sendMessage(cb.create());
            player.sendMessage("");
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
        if (args.length <= 1) return tabComplete(args[0], this.commands);
        switch (args[0]) {
        case "claim":
            return Collections.emptyList();
        case "here":
        case "info":
        case "bring":
            if (args.length == 2) return tabComplete(args[1], this.plugin.findHorses(player).stream().map(HorseData::getStrippedName).map(n -> n.replace(" ", "")));
            return null;
        case "trust":
            if (args.length == 2) return null;
            return Collections.emptyList();
        case "untrust": {
            if (args.length == 2) {
                try {
                    return tabComplete(args[1], spawnedHorseOf(interactedHorseOf(player)).data.getTrusted().stream().map(u -> playerNameOrElse(u, "?")));
                } catch (Exception e) { }
            }
            return Collections.emptyList();
        }
        default:
            return Collections.emptyList();
        }
    }

    // --- Helpers

    /**
     * Return all the properties of a horse which we will show to
     * players. Used for tooltips and the info command.
     *
     * Used by `/h info` and HorseGUI#horseList(Player).
     */
    List<BaseComponent> describeHorse(HorseData data) {
        String c = "" + data.getGender().chatColor;
        String d = "" + ChatColor.GRAY + ChatColor.ITALIC;
        ArrayList<BaseComponent> result = new ArrayList<>();
        result.add(new TextComponent(d + "Name " + ChatColor.RESET + data.getName()));
        result.add(new TextComponent(d + ""));
        if (data.getOwner() == null) {
            result.add(new TextComponent(d + "Owner " + c + ChatColor.ITALIC + "Unclaimed"));
        } else {
            result.add(new TextComponent(d + "Owner " + c + playerNameOrElse(data.getOwner(), "N/A")));
        }
        result.add(new TextComponent(d + "Gender " + c + data.getGender().humanName + " " + data.getGender().symbol));
        result.add(new TextComponent(d + "Age " + c + data.getAge().humanName));
        if (data.getBrand() != null) result.add(new TextComponent(d + "Brand " + c + data.getBrand().getFormat()));
        HorseData mother = data.getMother(this.plugin);
        if (mother != null) result.add(new TextComponent(d + "Mother " + mother.getMaskedName()));
        HorseData father = data.getFather(this.plugin);
        if (father != null) result.add(new TextComponent(d + "Father " + father.getMaskedName()));
        result.add(new TextComponent(""));
        result.add(new TextComponent(d + "Breed " + c + data.getBreed().humanName));
        if (data.getColor() != null) result.add(new TextComponent(d + "Color " + c + data.getColor().humanName));
        if (data.getMarkings() != null) result.add(new TextComponent(d + "Markings " + c + data.getMarkings().humanName));
        result.add(new TextComponent(""));
        result.add(new TextComponent(d + "Body " + c + data.getBodyCondition().humanName + ChatColor.GRAY + ", " + c + data.getHydrationLevel().humanName));
        result.add(new TextComponent(d + "Jump " + c + String.format("%.2f", data.getJump()) + ChatColor.GRAY + ChatColor.ITALIC + String.format(" (%.02f blocks)", data.getJumpHeight())));
        result.add(new TextComponent(d + "Speed " + c + String.format("%.2f", data.getSpeed())));
        if (data.getGrooming() != null) {
            result.add(new TextComponent(d + "Appearance " + c + data.getGrooming().getAppearance()));
        }
        if (!data.getTrusted().isEmpty()) {
            result.add(new TextComponent(""));
            Iterator<UUID> iter = data.getTrusted().iterator();
            StringBuilder sb = new StringBuilder(playerNameOrElse(iter.next(), "N/A"));
            while (iter.hasNext()) sb.append(", ").append(playerNameOrElse(iter.next(), "N/A"));
            result.add(new TextComponent(d + "Trusted " + ChatColor.GRAY + sb.toString()));
        }
        return result;
    }
}
