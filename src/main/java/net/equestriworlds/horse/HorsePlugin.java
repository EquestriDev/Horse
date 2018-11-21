package net.equestriworlds.horse;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Naming guidelines:
 * - (Abstract)Horse entities are named `entity`.
 * - HorseData are named `data`.
 * - SpawnedHorse instances are named `spawned`.
 *
 * When a horse entity is encountered, the correct path of inspections is:
 * - Find the SpawnedHorse instance.
 * - Get the HorseData from it.
 */
@Getter
public final class HorsePlugin extends JavaPlugin implements Runnable {
    // --- Constants
    public static final String SCOREBOARD_MARKER = "equestriworlds.horse";
    public static final String ITEM_MARKER = "equestriworlds.item";
    public static final String ITEM_USES = "equestriworlds.uses";
    // --- Horse Data
    private HorseDatabase database;
    private List<HorseData> horses;
    private ArrayList<SpawnedHorse> spawnedHorses = new ArrayList<>();
    // --- Commands
    private HorseCommand horseCommand;
    private AdminCommand adminCommand;
    private EditCommand editCommand;
    // --- Features
    private HorseListener horseListener;
    private Gaits gaits;
    private Grooming grooming;
    private Feeding feeding;
    private Breeding breeding;
    private Economy economy;
    // Data
    private Map<UUID, HorseBrand> horseBrands;
    private List<String> horseNames; // lazy
    private ConfigurationSection itemsConfig; // lazy
    // --- Dirty
    private net.equestriworlds.horse.dirty.NBT dirtyNBT = new net.equestriworlds.horse.dirty.NBT();
    private net.equestriworlds.horse.dirty.Path dirtyPath = new net.equestriworlds.horse.dirty.Path();
    private net.equestriworlds.horse.dirty.Entities dirtyEntities = new net.equestriworlds.horse.dirty.Entities();

    // --- JavaPlugin

    /**
     * Load all the horses and attempt to spawn them in where
     * necessary.
     */
    @Override
    public void onEnable() {
        saveResource("horse_names.json", false);
        // Prepare and load database
        this.database = new HorseDatabase(this);
        this.database.createTables();
        this.horses = this.database.loadHorses();
        spawnAllHorses();
        this.horseBrands = new HashMap<>();
        for (HorseBrand horseBrand: this.database.loadHorseBrands()) {
            this.horseBrands.put(horseBrand.getOwner(), horseBrand);
        }
        // Commands
        this.horseCommand = new HorseCommand(this);
        this.adminCommand = new AdminCommand(this);
        this.editCommand = new EditCommand(this);
        // Feature listeners
        this.horseListener = new HorseListener(this);
        this.gaits = new Gaits(this);
        this.grooming = new Grooming(this);
        this.feeding = new Feeding(this);
        this.breeding = new Breeding(this);
        // Register events and commands
        getCommand("horse").setExecutor(this.horseCommand);
        getCommand("horseadmin").setExecutor(this.adminCommand);
        getServer().getPluginManager().registerEvents(this.horseListener, this);
        getServer().getPluginManager().registerEvents(this.gaits, this);
        getServer().getPluginManager().registerEvents(this.grooming, this);
        getServer().getPluginManager().registerEvents(this.feeding, this);
        getServer().getPluginManager().registerEvents(this.breeding, this);
        // Start tick timer
        getServer().getScheduler().runTaskTimer(this, this, 1L, 1L);
        // Setup economy one tick later to make sure the unknown economy plugin (NOT Vault) was loaded.
        getServer().getScheduler().runTask(this, this::setupEconomy);
    }

    /**
     * Remove all known horse entities. Save in the main thread if
     * necessary.
     */
    @Override
    public void onDisable() {
        for (SpawnedHorse spawned: this.spawnedHorses) {
            if (spawned.isPresent()) {
                AbstractHorse entity = spawned.getEntity();
                spawned.data.storeLocation(entity.getLocation());
                spawned.data.storeInventory(this, entity);
                this.database.updateHorse(spawned.data);
                spawned.despawn();
            }
        }
        horses.clear();
        spawnedHorses.clear();
        for (Player player: getServer().getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view != null && view.getTopInventory().getHolder() instanceof HorseGUI) {
                player.closeInventory();
            }
            this.editCommand.removeEditingSession(player);
            this.gaits.removeGaitMeta(player);
        }
        for (World w: getServer().getWorlds()) {
            for (Entity e: w.getEntities()) {
                if (e.getScoreboardTags().contains(SCOREBOARD_MARKER)) e.remove();
            }
        }
    }

    // --- HorseData

    List<AbstractHorse> spawnAllHorses() {
        List<AbstractHorse> result = new ArrayList<>();
        for (HorseData data: this.horses) {
            HorseData.HorseLocation dataLoc = data.getLocation();
            if (dataLoc != null) {
                World world = dataLoc.bukkitWorld();
                if (world != null && world.isChunkLoaded(dataLoc.cx, dataLoc.cz)) {
                    SpawnedHorse horse = spawnHorse(data, dataLoc.bukkitLocation());
                    result.add(horse.getEntity());
                }
            }
        }
        return result;
    }

    void addHorse(HorseData data) {
        if (data.getId() >= 0) throw new IllegalArgumentException("Horse already exists");
        this.database.saveHorse(data);
        this.horses.add(data);
    }

    // --- SpawnedHorse

    /**
     * Spawn a horse at the given location, deleting it from the world
     * if it is already represented as a SpawnedHorse.
     * Shall never return null.
     */
    SpawnedHorse spawnHorse(HorseData data, Location location) {
        if (data == null) throw new NullPointerException("data cannot be null");
        if (location == null) throw new NullPointerException("location cannot be null");
        AbstractHorse entity = (AbstractHorse)location.getWorld().spawn(location, (Class<? extends AbstractHorse>)data.getBreed().entityType.getEntityClass(), e -> this.prepareHorseEntity(data, e));
        data.storeLocation(location);
        this.database.updateHorse(data);
        // Update or create the SpawnedHorse
        SpawnedHorse spawned = findSpawnedHorse(data);
        if (spawned == null) {
            spawned = new SpawnedHorse(data);
            spawned.setEntity(entity);
            this.spawnedHorses.add(spawned);
            HorseData.CrosstieData cd = data.getCrosstie();
            if (cd != null
                && cd.hitchA != null && cd.hitchA.size() == 3
                && cd.hitchB != null && cd.hitchB.size() == 3) {
                Bat bat = spawnCrosstieBat(location);
                LeashHitch a = this.dirtyEntities.tieUp(bat, cd.hitchA.get(0), cd.hitchA.get(1), cd.hitchA.get(2));
                LeashHitch b = this.dirtyEntities.tieUp(entity, cd.hitchB.get(0), cd.hitchB.get(1), cd.hitchB.get(2));
                Crosstie crosstie = new Crosstie(spawned);
                crosstie.setLeashedBat(bat);
                crosstie.setHitchA(a);
                crosstie.setHitchB(b);
                spawned.setupCrosstie(crosstie);
            }
        } else {
            spawned.despawn();
            spawned.setEntity(entity);
        }
        return spawned;
    }

    /**
     * Will spawn the horse if it is not represented as an entity yet.
     * Shall never return null.
     */
    SpawnedHorse teleportHorse(HorseData data, Location location) {
        SpawnedHorse spawned = findSpawnedHorse(data);
        if (spawned == null) {
            spawned = new SpawnedHorse(data);
            this.spawnedHorses.add(spawned);
        }
        if (!spawned.isPresent()) {
            // Spawn the entity
            AbstractHorse entity = (AbstractHorse)location.getWorld().spawn(location, (Class<? extends AbstractHorse>)data.getBreed().entityType.getEntityClass(), e -> this.prepareHorseEntity(data, e));
            spawned.setEntity(entity);
        } else {
            spawned.getEntity().teleport(location);
        }
        // Update data
        spawned.data.storeLocation(location);
        spawned.data.setCrosstie(null);
        this.database.updateHorse(spawned.data);
        return spawned;
    }

    /**
     * Update a horse entity to match the new data if it exists in the
     * world and return its SpawnedHorse instance. Do nothing if the
     * entity doesn't exist in the world.
     *
     * This may respawn the horse if the breed changed.  A horse breed
     * does not usually change unless an admin makes it so via the
     * EditCommand.
     *
     * This also saves to database as a side effect.
     */
    SpawnedHorse updateHorseEntity(HorseData data) {
        SpawnedHorse spawned = this.findSpawnedHorse(data);
        if (spawned == null || !spawned.isPresent()) return null;
        AbstractHorse entity = spawned.getEntity();
        Location location = entity.getLocation();
        if (entity.getType() != data.getBreed().entityType) {
            spawned.despawn();
            spawnHorse(data, location);
        } else {
            prepareHorseEntity(data, entity);
        }
        data.storeLocation(location);
        this.database.updateHorse(data);
        return spawned;
    }

    /**
     * Called by the above 3 functions to prepare a possible newly
     * spawned horse entity for further user in the system.  The
     * scoreboard is set and all HorseData values applied.
     */
    void prepareHorseEntity(HorseData data, AbstractHorse entity) {
        entity.addScoreboardTag(SCOREBOARD_MARKER);
        data.applyProperties(entity);
        data.applyInventory(this, entity);
    }

    SpawnedHorse findSpawnedHorse(HorseData data) {
        return this.spawnedHorses.stream().filter(sh -> sh.data == data).findFirst().orElse(null);
    }

    SpawnedHorse findSpawnedHorse(AbstractHorse entity) {
        return this.spawnedHorses.stream().filter(sh -> sh.represents(entity)).findFirst().orElse(null);
    }

    // --- Horse Finder Functions

    /**
     * Find all HorseData records belonging to the player.
     */
    List<HorseData> findHorses(Player player) {
        return horses.stream().filter(h -> h.isOwner(player)).collect(Collectors.toList());
    }

    HorseData findHorse(int id) {
        return horses.stream().filter(h -> h.getId() == id).findFirst().orElse(null);
    }

    // --- Random

    List<String> getHorseNames() {
        if (this.horseNames == null) {
            try (FileReader reader = new FileReader(new File(getDataFolder(), "horse_names.json"))) {
                this.horseNames = (List<String>)new Gson().fromJson(reader, List.class);
            } catch (Exception e) {
                e.printStackTrace();
                this.horseNames = Collections.emptyList();
            }
        }
        return this.horseNames;
    }

    ConfigurationSection getItemsConfig() {
        if (this.itemsConfig == null) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items.yml"));
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("items.yml"))));
            this.itemsConfig = yaml;
        }
        return this.itemsConfig;
    }

    String randomHorseName(Random random) {
        List<String> names = getHorseNames();
        if (names == null || names.isEmpty()) return "X";
        return names.get(random.nextInt(names.size()));
    }

    // --- Ticking

    @Override
    public void run() {
        // SpawnedHorse
        for (Iterator<SpawnedHorse> iter = this.spawnedHorses.iterator(); iter.hasNext();) {
            if (!iter.next().isPresent()) iter.remove();
        }
        for (SpawnedHorse spawned: new ArrayList<>(this.spawnedHorses)) {
            tickSpawnedHorse(spawned);
        }
    }

    private void tickSpawnedHorse(SpawnedHorse spawned) {
        long ticksLived = spawned.getTicksLived();
        spawned.setTicksLived(ticksLived + 1);
        if ((ticksLived % 10) == 0 && spawned.getFollowing() != null) {
            if (!followHorse(spawned)) spawned.setFollowing(null);
        }
        // Crossties
        Crosstie crosstie = spawned.getCrosstie();
        if (crosstie != null && !crosstie.check()) {
            spawned.removeCrosstie();
            spawned.data.setCrosstie(null);
            this.database.updateHorse(spawned.data);
            spawned.getEntity().getWorld().playSound(spawned.getEntity().getEyeLocation(), Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.NEUTRAL, 1.0f, 2.0f);
        }
        // Grooming
        HorseData.GroomingData groomingData = spawned.data.getGrooming();
        if (groomingData != null) {
            if (groomingData.expiration < Instant.now().getEpochSecond()) {
                spawned.data.setGrooming(null);
                this.database.updateHorse(spawned.data);
            } else if (groomingData.wash == 1 && (ticksLived % 8 == 0)) {
                spawned.getEntity().getWorld().spawnParticle(Particle.WATER_DROP, spawned.getEntity().getEyeLocation(), 8, 0.5, 0.5, 0.5, 0.0);
            } else if (groomingData.wash == 2 && (ticksLived % 8 == 0)) {
                spawned.getEntity().getWorld().spawnParticle(Particle.CLOUD, spawned.getEntity().getEyeLocation(), 8, 0.5, 0.5, 0.5, 0.0);
            }
        }
        // Eating and drinking
        if (ticksLived > 0 && (ticksLived % 20) == 0) {
            Long now = Instant.now().getEpochSecond();
            spawned.data.passSecond(now);
            this.feeding.passSecond(spawned, now);
            if (spawned.data.getBreedingStage() != BreedingStage.READY) this.breeding.passSecond(spawned, now);
            if (spawned.data.getAge() != HorseAge.ADULT && spawned.data.getAgeCooldown() == 0) {
                HorseAge nx = spawned.data.getAge().next();
                spawned.data.setAge(nx);
                spawned.data.setAgeCooldown(nx.duration * Util.ONE_DAY);
                spawned.getEntity().setAge(0);
                this.database.updateHorse(spawned.data);
                Player owner = spawned.data.getOwningPlayer();
                if (owner != null) {
                    String txt = ChatColor.GOLD + "Your " + spawned.data.getGender().humanName.toLowerCase() + " " + spawned.data.getMaskedName() + ChatColor.GOLD + " has grown up and is now " + nx.indefiniteArticle() + " " + ChatColor.BOLD + nx.humanName + ChatColor.RESET + ChatColor.GOLD + ".";
                    owner.sendMessage(txt);
                    owner.sendActionBar(txt);
                    owner.playSound(owner.getEyeLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 2.0f);
                }
            }
        }
    }

    private boolean followHorse(SpawnedHorse spawned) {
        Player followedPlayer = getServer().getPlayer(spawned.getFollowing());
        if (followedPlayer == null) {
            return false;
        }
        Location followLocation = followedPlayer.getLocation();
        Location horseLocation = spawned.getEntity().getLocation();
        if (!followLocation.getWorld().equals(horseLocation.getWorld())) return false;
        double distanceSquared = followLocation.distanceSquared(horseLocation);
        if (distanceSquared > 128.0 * 128.0) return false;
        if (distanceSquared < 4.0) return true;
        double speed;
        if (distanceSquared > 32.0 * 32.0) {
            speed = 2.25;
        } else if (distanceSquared > 16.0 * 16.0) {
            speed = 2.0;
        } else if (distanceSquared > 8.0 * 8.0) {
            speed = 1.75;
        } else {
            speed = 1.5;
        }
        this.dirtyPath.navigate(spawned.getEntity(), followedPlayer.getLocation(), speed);
        return true;
    }

    // --- Player Cache

    String cachedPlayerName(UUID uuid) {
        return getServer().getOfflinePlayer(uuid).getName();
    }

    UUID cachedPlayerUuid(String name) {
        return getServer().getOfflinePlayer(name).getUniqueId();
    }

    // --- Economy

    void setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) this.economy = economyProvider.getProvider();
    }

    boolean playerHasMoney(Player player, double amount) {
        if (this.economy == null) return false;
        return this.economy.has(player, amount);
    }

    boolean takePlayerMoney(Player player, double amount) {
        if (this.economy == null) return false;
        return this.economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    String formatMoney(double amount) {
        if (this.economy == null) return String.format("%.02f", amount);
        return this.economy.format(amount);
    }

    // --- Util

    Bat spawnCrosstieBat(Location location) {
        return location.getWorld().spawn(location, Bat.class, b -> {
                b.addScoreboardTag(HorsePlugin.SCOREBOARD_MARKER);
                b.setAI(false);
                b.setSilent(true);
                b.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 0, true, false));
            });
    }
}
