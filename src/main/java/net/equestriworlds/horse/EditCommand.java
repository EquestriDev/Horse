package net.equestriworlds.horse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

/**
 * Usage:
 * /ha edit [key] [value...]
 *
 * This is part of the admin command but was outsourced for
 * readability.  An editing session requires a handful of methods and
 * data structures which cannot be used anywhere else.
 *
 * The edit command is expected to be the result of ClickEvents or
 * confirmed command suggestions.  Therefore, elaborate command
 * feedback is usually not required.
 *
 * An admin may have one editing session stored in their metadata.
 * This command acts as a key-value pair setter, along with a
 * convenient menu presented as interactive chat.
 */
final class EditCommand extends CommandBase {
    private final EditField name = new EditField<String>("name", "Name", HorseData::getName, HorseData::setName, null);
    private final EditField gender = new EditField<HorseGender>("gender", "Gender", HorseData::getGender, HorseData::setGender, HorseGender::valueOf);
    private final EditField age = new EditField<HorseAge>("age", "Age", HorseData::getAge, HorseData::setAge, HorseAge::valueOf);
    private final EditField breed = new EditField<HorseBreed>("breed", "Breed", HorseData::getBreed, HorseData::setBreed, HorseBreed::valueOf);
    private final EditField color = new EditField<HorseColor>("color", "Color", HorseData::getColor, HorseData::setColor, HorseColor::valueOf);
    private final EditField markings = new EditField<HorseMarkings>("markings", "Markings", HorseData::getMarkings, HorseData::setMarkings, HorseMarkings::valueOf);
    private final EditField brand = new EditField<HorseBrand>("brand", "Brand", null, null, null);
    private final EditField jump = new EditField<Double>("jump", "Jump", HorseData::getJump, HorseData::setJump, Double::parseDouble);
    private final EditField speed = new EditField<Double>("speed", "Speed", HorseData::getSpeed, HorseData::setSpeed, Double::parseDouble);
    private final EditField presets = new EditField("presets", "Presets", null, null, null);
    private final EditField<BodyConditionScale> body = new EditField<>("body", "Body", HorseData::getBodyCondition, HorseData::setBodyCondition, BodyConditionScale::valueOf);
    private final EditField grooming = new EditField<Double>("grooming", "Grooming", null, null, null);
    private final EditField<Boolean> hungry = new EditField<>("hungry", "Hungry", HorseData::isHungry, HorseData::setHungry, Boolean::valueOf);
    private final EditField<Boolean> thirsty = new EditField<>("thirsty", "Thirsty", HorseData::isThirsty, HorseData::setThirsty, Boolean::valueOf);
    private final List<EditField> editFields = Arrays.asList(this.name, this.gender, this.age, this.breed, this.color, this.markings, this.brand, this.body, this.jump, this.speed, this.presets, this.grooming, this.hungry, this.thirsty);
    private final List<ChatColor> colorful = Arrays.asList(ChatColor.AQUA, ChatColor.BLUE, ChatColor.GOLD, ChatColor.GREEN, ChatColor.LIGHT_PURPLE, ChatColor.RED, ChatColor.YELLOW);
    private int colorfulIndex;
    private final List<String> dice = Arrays.asList("\u2680", "\u2681", "\u2682", "\u2683", "\u2684", "\u2685");

    EditCommand(HorsePlugin plugin) {
        super(plugin);
        Collections.shuffle(this.colorful, new Random(0));
    }

    @RequiredArgsConstructor
    static class EditField<T> {
        final String key, name;
        final Function<HorseData, T> getter;
        final BiConsumer<HorseData, T> setter;
        final Function<String, T> converter;
    }

    // --- Command and subcommands

    /**
     * Called by AdminCommand#onCommand.
     */
    boolean onEditCommand(Player player, String[] args) throws CommandException {
        HorseData data;
        // /ha edit
        if (args.length == 0) {
            AbstractHorse entity = interactedHorseOf(player);
            SpawnedHorse spawned = spawnedHorseOf(entity);
            data = spawned.data;
            setEditingSession(player, new Editing(data));
        } else {
            data = editingSessionOf(player).data;
        }
        if (args.length == 0) {
            showEditingMenu(player, data);
            return true;
        }
        // build the value
        String key = args[0];
        String value;
        if (args.length == 1 && args[0].equals("spawn")) {
            if (data.getId() >= 0) throw new CommandException("Horse already exists.");
            data.setBorn(Instant.now().getEpochSecond());
            this.plugin.addHorse(data);
            this.plugin.spawnHorse(data, player.getLocation());
            return true;
        }
        if (args.length == 2 && args[0].equals("randomize")) {
            data.randomize(this.plugin, args[1]);
            if (data.getId() >= 0) {
                this.plugin.getDatabase().updateHorse(data);
                this.plugin.updateHorseEntity(data);
            }
            showEditingMenu(player, data);
            return true;
        }
        if (args.length == 1 && args[0].equals("removebrand")) {
            data.setBrand(null);
            if (data.getId() >= 0) {
                this.plugin.getDatabase().updateHorse(data);
                this.plugin.updateHorseEntity(data);
            }
            showEditingMenu(player, data);
            return true;
        }
        if (args.length == 1 && args[0].equals("resetgrooming")) {
            data.setGrooming(null);
            if (data.getId() >= 0) {
                this.plugin.getDatabase().updateHorse(data);
                this.plugin.updateHorseEntity(data);
            }
            showEditingMenu(player, data);
            return true;
        }
        if (args.length == 2 && args[0].equals("preset")) {
            switch (args[1]) {
            case "hunter":
                data.setJump(0.7);
                data.setSpeed(0.38);
                break;
            case "dressage":
                data.setJump(0.79);
                data.setSpeed(0.225);
                break;
            case "max":
                data.setJump(0.79);
                data.setSpeed(0.38);
                break;
            default:
                throw new CommandException("Invalid preset: " + args[1]);
            }
            if (data.getId() >= 0) {
                this.plugin.getDatabase().updateHorse(data);
                this.plugin.updateHorseEntity(data);
            }
            showEditingMenu(player, data);
            return true;
        }
        if (args.length == 1) {
            return onEditCommand(player, key);
        } else {
            StringBuilder sb = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
            return onEditCommand(player, args[0], sb.toString());
        }
    }

    boolean newSession(Player player) {
        Editing editing = new Editing(new HorseData());
        editing.data.randomize(this.plugin);
        setEditingSession(player, editing);
        showEditingMenu(player, editing.data);
        return true;
    }

    /**
     * Display a list of possible values for one field of this
     * player's editing session.
     *
     * It is a user error if this is called without an existing
     * session, or for a field which does not yield a list of possible
     * values and the corresponding CommandException will be thrown.
     *
     * @see EditCommand#listPossibleValues(HorseData, EditField)
     */
    private boolean onEditCommand(Player player, String key) throws CommandException {
        EditField field = editFieldOf(key);
        HorseData data = editingSessionOf(player).data;
        List<Object> values = listPossibleValues(data, field);
        if (values == null) throw new CommandException("Not possible values for " + field.name);
        ComponentBuilder cb = new ComponentBuilder("Select " + field.name + ":");
        cb.color(ChatColor.GOLD).bold(true);
        int i = 0;
        for (Object o: values) {
            ChatColor valueColor = this.colorful.get(i++ % colorful.size());
            cb.append(" ").reset();
            String displayValue = displayValue(data, field, o);
            cb.append("[" + displayValue + "]").color(valueColor);
            String cmd = "/ha edit " + key + " " + ("" + o);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(valueColor + displayValue)));
        }
        player.spigot().sendMessage(cb.create());
        return true;
    }

    /**
     * Set one field of this player's editing session to a new value.
     *
     * It is a user error if this is called without an existing
     * session, and the corresponding CommandException will be thrown.
     *
     * Called by the main editing command method.  At this point we
     * can be certain that there is a key and a value entered by the
     * admin.
     */
    private boolean onEditCommand(Player player, String key, String value) throws CommandException {
        EditField field = editFieldOf(key);
        HorseData data = editingSessionOf(player).data;
        if (field.converter == null) {
            field.setter.accept(data, value);
        } else {
            try {
                field.setter.accept(data, field.converter.apply(value));
            } catch (Exception e) {
                throw new CommandException("Invalid value: " + value);
            }
        }
        // Update markings and color if the breed changed
        if (field == this.breed) {
            HorseBreed databreed = data.getBreed();
            if (data.getColor() != null && !databreed.colors.contains(data.getColor())) data.randomize(this.plugin, "color");
            if (data.getColor() == null && !databreed.colors.isEmpty()) data.randomize(this.plugin, "color");
            if (data.getMarkings() != null && !databreed.markings.contains(data.getMarkings())) data.randomize(this.plugin, "markings");
            if (data.getMarkings() == null && !databreed.markings.isEmpty()) data.randomize(this.plugin, "markings");
        }
        if (field == this.age) {
            if (data.getAge() != HorseAge.ADULT) data.setAgeCooldown(data.getAge().duration * Util.ONE_DAY);
        }
        if (data.getId() >= 0) this.plugin.updateHorseEntity(data);
        showEditingMenu(player, data);
        return true;
    }

    // --- Chat Menu

    void showEditingMenu(Player player, HorseData data) {
        player.sendMessage("");
        player.sendMessage(""
                           + ChatColor.YELLOW + ChatColor.STRIKETHROUGH + "            "
                           + ChatColor.YELLOW + "[ "
                           + ChatColor.GOLD + ChatColor.BOLD + "Horse Editor"
                           + ChatColor.YELLOW + " ]"
                           + ChatColor.YELLOW + ChatColor.STRIKETHROUGH + "            ");
        ComponentBuilder cb = null;
        // All edit fields
        this.colorfulIndex = 0;
        for (EditField field: this.editFields) {
            if (cb == null) cb = new ComponentBuilder("");
            boolean printLine;
            if (field == this.brand) {
                HorseBrand horseBrand = data.getBrand();
                if (horseBrand != null) {
                    printLine = true;
                    cb.append("Brand").color(ChatColor.GOLD).bold(true).italic(true).append(" ").reset();
                    cb.append(horseBrand.getFormat()).append(" ").reset();
                    cb.append("(" + this.plugin.cachedPlayerName(horseBrand.getOwner()) + ")").color(ChatColor.GRAY).append(" ").reset();
                    cb.append("[Remove]").color(ChatColor.RED)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit removebrand"))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "Remove this brand from this horse.")));
                } else {
                    printLine = false;
                }
            } else if (field == this.grooming) {
                HorseData.GroomingData groomingData = data.getGrooming();
                if (groomingData != null) {
                    printLine = true;
                    cb.append("Grooming").color(ChatColor.GOLD).bold(true).italic(true).append(" ").reset();
                    cb.append("" + groomingData.appearance).append(" ").reset();
                    cb.append("[Reset]").color(ChatColor.RED)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit resetgrooming"))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "Reset the grooming status of this horse.")));
                } else {
                    printLine = false;
                }
            } else if (field == this.presets) {
                printLine = presetsEditField(data, field, cb);
            } else {
                printLine = standardEditField(data, field, cb);
            }
            if (printLine) {
                player.spigot().sendMessage(cb.create());
                cb = null;
            }
        }
        if (data.getId() < 0) {
            cb = new ComponentBuilder("New Horse").color(ChatColor.DARK_GRAY);
            cb.append("  ").reset();
            cb.append("[Spawn]").color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit spawn"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "Spawn this " + data.getBreed().humanName)));
            player.spigot().sendMessage(cb.create());
            cb = null;
        }
    }

    boolean standardEditField(HorseData data, EditField field, ComponentBuilder cb) {
        Object o = field.getter.apply(data);
        if (o == null) return false;
        String displayValue = displayValue(data, field, o);
        cb.append(field.name).color(ChatColor.GOLD).bold(true).italic(true);
        if (field != this.hungry && field != this.thirsty) {
            cb.append(" ").reset();
            // Randomize
            cb.append("[" + this.dice.get(ThreadLocalRandom.current().nextInt(this.dice.size())) + "]").color(ChatColor.GREEN); // dice
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit randomize " + field.key));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "Randomize " + field.name)));
        }
        cb.append(" ").reset();
        boolean adjustButtons = false;
        boolean slideButtons = false;
        List<Object> possibleValues = listPossibleValues(data, field);
        if (field == this.speed || field == this.jump) {
            adjustButtons = true;
        } else if (possibleValues != null && possibleValues.size() > 1) {
            slideButtons = true;
        }
        // Minus button
        if (adjustButtons) {
            for (int i = 0; i < 2; i += 1) {
                double dec = i == 0 ? 0.1 : 0.01;
                ChatColor adjustColor = i == 0 ? ChatColor.DARK_RED : ChatColor.RED;
                double newValue = Math.max(0.0, (Double)o - dec);
                cb.append("-").color(adjustColor).bold(true);
                String cmd = "/ha edit " + field.key + " " + String.format("%.02f", newValue);
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(adjustColor + "Reduce " + field.name + String.format(" by %.2f.", dec))));
            }
        } else if (slideButtons) {
            int valueIndex = Math.max(0, possibleValues.indexOf(o));
            valueIndex -= 1;
            if (valueIndex < 0) valueIndex += possibleValues.size();
            Object newValue = possibleValues.get(valueIndex);
            String newDisplay = displayValue(data, field, newValue);
            cb.append("< ").color(ChatColor.GRAY).bold(true);
            String cmd = "/ha edit " + field.key + " " + newValue;
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + "Set " + field.name + " to " + newDisplay)));
        }
        // Show value button
        {
            ChatColor valueColor = colorful.get(this.colorfulIndex++ % colorful.size());
            if (possibleValues != null) {
                // With available values, print a menu on click
                String cmd = "/ha edit " + field.key;
                if (possibleValues.size() > 1) {
                    cb.append("[" + displayValue + "]").color(valueColor);
                    cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                    cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(valueColor + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Set the " + field.name)));
                } else {
                    cb.append(displayValue).color(valueColor);
                }
            } else {
                // Else just suggest the command to allow editing
                String cmd = "/ha edit " + field.key + " " + ChatColor.stripColor(displayValue);
                cb.append("[" + displayValue + valueColor + "]").color(valueColor);
                cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd));
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(valueColor + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Set the " + field.name)));
            }
        }
        if (adjustButtons) {
            for (int i = 0; i < 2; i += 1) {
                double inc = i == 0 ? 0.01 : 0.1;
                ChatColor adjustColor = i == 0 ? ChatColor.DARK_GREEN : ChatColor.GREEN;
                double newValue = Math.max(0.0, (Double)o + inc);
                String cmd = "/ha edit " + field.key + " " + String.format("%.02f", newValue);
                cb.append("+").color(adjustColor).bold(true);
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(adjustColor + "Increase " + field.name + String.format(" by %.2f.", inc))));
            }
        } else if (slideButtons) {
            int valueIndex = Math.max(0, possibleValues.indexOf(o));
            valueIndex = (valueIndex + 1) % possibleValues.size();
            Object newValue = possibleValues.get(valueIndex);
            String newDisplay = displayValue(data, field, newValue);
            cb.append(" >").color(ChatColor.GRAY).bold(true);
            String cmd = "/ha edit " + field.key + " " + newValue;
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + "Set " + field.name + " to " + newDisplay)));
        }
        // Jump Height
        if (field == this.speed) {
            cb.append(" ").reset();
            cb.append("(" + String.format("%.02f", data.getSpeed() * 4.3) + " blocks/sec)").color(ChatColor.GRAY).italic(true);
        } else if (field == this.jump) {
            cb.append(" ").reset();
            cb.append("(" + String.format("%.02f", data.getJumpHeight()) + " blocks)").color(ChatColor.GRAY).italic(true);
        } else if (field == this.gender) {
            cb.append(" ").reset();
            HorseGender horseGender = (HorseGender)o;
            cb.append(horseGender.symbol).color(horseGender.chatColor);
        }
        return true;
    }

    boolean presetsEditField(HorseData data, EditField field, ComponentBuilder cb) {
        cb.append("Presets").color(ChatColor.DARK_GRAY);
        cb.append("  ").reset();
        ChatColor valueColor = colorful.get(this.colorfulIndex++ % colorful.size());
        cb.append("[Hunter]").color(valueColor)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit preset hunter"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(valueColor + "Preset Hunter")));
        cb.append(" ").reset();
        valueColor = colorful.get(this.colorfulIndex++ % colorful.size());
        cb.append("[Dressage]").color(valueColor)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit preset dressage"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(valueColor + "Preset Dressage")));
        cb.append(" ").reset();
        valueColor = colorful.get(this.colorfulIndex++ % colorful.size());
        cb.append("[Max]").color(valueColor)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ha edit preset max"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(valueColor + "Preset Maximum")));
        return true;
    }

    // --- Utility

    String displayValue(HorseData data, EditField field, Object o) {
        if (field == this.age) {
            HorseAge hage = (HorseAge)o;
            if (hage == HorseAge.FOAL && data.getGender().isMale()) return "Colt";
            if (hage == HorseAge.FOAL && data.getGender().isFemale()) return "Filly";
            return hage.humanName;
        } else if (o instanceof HumanReadable) {
            return ((HumanReadable)o).getHumanName();
        } else if (o instanceof Integer) {
            return "" + o;
        } else if (o instanceof Number) {
            return String.format("%.02f", ((Number)o).doubleValue());
        } else if (o instanceof Boolean) {
            return (boolean)o ? "Yes" : "No";
        } else {
            return "" + o;
        }
    }

    List<Object> listPossibleValues(HorseData data, EditField field) {
        if (field == this.gender)   return Arrays.asList(HorseGender.values());
        if (field == this.age)      return Arrays.asList(HorseAge.values());
        if (field == this.breed)    return Arrays.asList(HorseBreed.values());
        if (field == this.body)     return Arrays.asList(BodyConditionScale.values());
        if (field == this.color)    return new ArrayList(data.getBreed().colors);
        if (field == this.markings) return new ArrayList(data.getBreed().markings);
        if (field == this.hungry)   return Arrays.asList(true, false);
        if (field == this.thirsty)  return Arrays.asList(true, false);
        return null;
    }

    // --- Editing Session utility

    @RequiredArgsConstructor
    static final class Editing {
        static final String KEY = "horse.admin.editing";
        final HorseData data;
    }

    Editing editingSessionOf(Player player) throws CommandException {
        Editing editing = getEditingSession(player);
        if (editing == null) throw new CommandException("No editing session");
        return editing;
    }

    Editing getEditingSession(Player player) {
        for (MetadataValue v: player.getMetadata(Editing.KEY)) {
            if (v.getOwningPlugin().equals(this.plugin)
                && v.value() instanceof Editing) return (Editing)v.value();
        }
        return null;
    }

    void setEditingSession(Player player, Editing editing) {
        player.setMetadata(Editing.KEY, new FixedMetadataValue(this.plugin, editing));
    }

    void removeEditingSession(Player player) {
        player.removeMetadata(Editing.KEY, this.plugin);
    }

    EditField editFieldOf(String arg) throws CommandException {
        for (EditField field: this.editFields) {
            if (field.key.equals(arg)) return field;
        }
        throw new CommandException("Unknown key: " + arg);
    }
}
