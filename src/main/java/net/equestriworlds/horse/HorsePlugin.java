package net.equestriworlds.horse;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
public final class HorsePlugin extends JavaPlugin {
    // --- Constants
    public static final String SCOREBOARD_MARKER = "equestriworlds.horse";
    public static final String SCOREBOARD_ID = "equestriworlds.id";
    // --- Horse Data
    private HorseDatabase database;
    private List<HorseData> horses;
    private List<SpawnedHorse> spawnedHorses = new ArrayList<>();
    private final Map<String, Map<Long, List<HorseData>>> chunkCache = new HashMap<>();
    // --- Interaction
    private HorseListener horseListener;
    private HorseCommand horseCommand;
    private AdminCommand adminCommand;
    private EditCommand editCommand;
    //
    private List<String> horseNames;

    // --- JavaPlugin

    /**
     * Load all the horses and attempt to spawn them in where
     * necessary.
     */
    @Override
    public void onEnable() {
        saveResource("horse_names.json", false);
        this.database = new HorseDatabase(this);
        this.database.createTables();
        loadHorses();
        this.horseListener = new HorseListener(this);
        this.horseCommand = new HorseCommand(this);
        this.adminCommand = new AdminCommand(this);
        this.editCommand = new EditCommand(this);
        getServer().getPluginManager().registerEvents(this.horseListener, this);
        getCommand("horse").setExecutor(this.horseCommand);
        getCommand("horseadmin").setExecutor(this.adminCommand);
    }

    /**
     * Remove all known horse entities. Save in the main thread if
     * necessary.
     */
    @Override
    public void onDisable() {
        for (SpawnedHorse spawnedHorse: spawnedHorses) {
            spawnedHorse.despawn();
        }
        horses.clear();
        spawnedHorses.clear();
        chunkCache.clear();
    }

    // --- HorseData

    void loadHorses() {
        this.horses = this.database.loadHorses();
        spawnAllHorses();
    }

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

    // --- SpawnedHorse

    /**
     * Spawn a horse at the given location, deleting it from the world
     * if it is already represented as a SpawnedHorse.
     * Shall never return null.
     */
    SpawnedHorse spawnHorse(HorseData data, Location location) {
        if (data == null) throw new NullPointerException("data cannot be null");
        if (location == null) throw new NullPointerException("location cannot be null");
        AbstractHorse entity = (AbstractHorse)location.getWorld().spawn(location, (Class<? extends AbstractHorse>)data.getBreed().entityType.getEntityClass(), data::applyProperties);
        entity.addScoreboardTag(SCOREBOARD_MARKER);
        entity.addScoreboardTag(SCOREBOARD_ID + data.getId());
        data.storeLocation(location);
        this.database.updateHorse(data);
        // Update or create the SpawnedHorse
        SpawnedHorse spawnedHorse = findSpawnedHorse(data);
        if (spawnedHorse == null) {
            spawnedHorse = new SpawnedHorse(data);
            spawnedHorse.setEntity(entity);
            spawnedHorses.add(spawnedHorse);
        } else {
            spawnedHorse.despawn();
            spawnedHorse.setEntity(entity);
        }
        return spawnedHorse;
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
            AbstractHorse entity = (AbstractHorse)location.getWorld().spawn(location, (Class<? extends AbstractHorse>)data.getBreed().entityType.getEntityClass(), data::applyProperties);
            entity.addScoreboardTag(SCOREBOARD_MARKER);
            entity.addScoreboardTag(SCOREBOARD_ID + data.getId());
            spawned.setEntity(entity);
        } else {
            spawned.getEntity().teleport(location);
        }
        // Update data
        spawned.data.storeLocation(location);
        this.database.updateHorse(spawned.data);
        return spawned;
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

    // --- Chunk cache

    // TODO: Rewrite
    // void updateChunkCache() {
    //     if (chunkWorld != null) {
    //         Map<Long, List<HorseData>> worldCache = chunkCache.get(chunkWorld);
    //         if (worldCache != null) {
    //             List<HorseData> horseCache = worldCache.get(chunkCoord);
    //             if (horseCache != null) horseCache.remove(this);
    //         }
    //     }
    //     chunkWorld = world;
    //     chunkCoord = ((long)cz << 32) | (long)cx;
    //     if (chunkWorld != null) {
    //         Map<Long, List<HorseData>> worldCache = chunkCache.get(chunkWorld);
    //         if (worldCache == null) {
    //             worldCache = new HashMap<>();
    //             chunkCache.put(chunkWorld, worldCache);
    //         }
    //         List<HorseData> horseCache = worldCache.get(chunkCoord);
    //         if (horseCache == null) {
    //             horseCache = new ArrayList<>();
    //             worldCache.put(chunkCoord, horseCache);
    //         }
    //         horseCache.add(this);
    //     }
    // }

    List<HorseData> horsesInChunk(Chunk chunk) {
        List<HorseData> result = new ArrayList<>();
        Map<Long, List<HorseData>> worldCache = chunkCache.get(chunk.getWorld().getName());
        if (worldCache == null) return result;
        long c = ((long)chunk.getZ() << 32) | (long)chunk.getX();
        List<HorseData> horseCache = worldCache.get(c);
        if (horseCache != null) result.addAll(horseCache);
        return result;
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

    String randomHorseName(Random random) {
        List<String> names = getHorseNames();
        if (names == null || names.isEmpty()) return "X";
        return names.get(random.nextInt(names.size()));
    }
}
