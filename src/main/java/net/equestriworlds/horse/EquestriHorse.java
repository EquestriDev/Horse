package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * This data structure represents every aspect of a claimed horse.  It
 * is designed so the horse can be serialized to memory and removed
 * from the world, and respawned later on.
 */
@Data
public final class EquestriHorse {
    private UUID uniqueId;
    private String name;
    // Ownership
    private UUID owner;
    private String ownerName;
    // Attributes
    private Horse.Color color;
    private Horse.Style style;
    private double jumpStrength;
    private double movementSpeed;
    private int age;
    private Map<String, Object> armor;
    private Map<String, Object> saddle;
    // Entity
    private UUID entityId;
    private int version;
    // Location
    private String world;
    private double x, y, z;
    private float pitch, yaw;
    private int cx, cz;
    // ChunkCache
    private static final Map<String, Map<Long, List<EquestriHorse>>> CHUNKS = new HashMap<>();
    private transient String chunkWorld;
    private transient long chunkCoord;

    void storeAttributes(Horse horse) {
        this.name = horse.getCustomName();
        this.color = horse.getColor();
        this.style = horse.getStyle();
        this.jumpStrength = horse.getJumpStrength();
        this.movementSpeed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
        this.age = horse.getAge();
        ItemStack item = horse.getInventory().getArmor();
        armor = item == null || item.getType() == Material.AIR ? null : item.serialize();
        item = horse.getInventory().getSaddle();
        saddle = item == null || item.getType() == Material.AIR ? null : item.serialize();
    }

    void applyAttributes(Horse horse) {
        horse.setCustomName(this.name);
        horse.setColor(this.color);
        horse.setStyle(this.style);
        horse.setJumpStrength(this.jumpStrength);
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(this.movementSpeed);
        horse.setAge(this.age);
        if (owner != null) {
            AnimalTamer player = Bukkit.getPlayer(owner);
            if (player == null) player = Bukkit.getOfflinePlayer(owner);
            horse.setTamed(true);
            if (player != null) horse.setOwner(player);
        }
        if (armor != null) {
            try {
                horse.getInventory().setArmor(ItemStack.deserialize(armor));
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        } else {
            horse.getInventory().setArmor(null);
        }
        if (saddle != null) {
            try {
                horse.getInventory().setSaddle(ItemStack.deserialize(saddle));
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        } else {
            horse.getInventory().setSaddle(null);
        }
    }

    void storeEntity(Horse horse) {
        this.entityId = horse.getUniqueId();
    }

    Horse findEntity() {
        if (entityId == null) return null;
        return (Horse)Bukkit.getEntity(entityId);
    }

    void storeLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = location.getPitch();
        this.yaw = location.getYaw();
        Chunk chunk = location.getChunk();
        this.cx = chunk.getX();
        this.cz = chunk.getZ();
        updateChunkCache();
    }

    Location getLocation() {
        if (world == null) return null;
        World bworld = Bukkit.getWorld(world);
        if (bworld == null) return null;
        return new Location(bworld, x, y, z, yaw, pitch);
    }

    void storeOwner(Player player) {
        owner = player.getUniqueId();
        ownerName = player.getName();
    }

    boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUniqueId());
    }

    // Internal house keeping

    void updateChunkCache() {
        if (chunkWorld != null) {
            Map<Long, List<EquestriHorse>> worldCache = CHUNKS.get(chunkWorld);
            if (worldCache != null) {
                List<EquestriHorse> horseCache = worldCache.get(chunkCoord);
                if (horseCache != null) horseCache.remove(this);
            }
        }
        chunkWorld = world;
        chunkCoord = ((long)cz << 32) | (long)cx;
        if (chunkWorld != null) {
            Map<Long, List<EquestriHorse>> worldCache = CHUNKS.get(chunkWorld);
            if (worldCache == null) {
                worldCache = new HashMap<>();
                CHUNKS.put(chunkWorld, worldCache);
            }
            List<EquestriHorse> horseCache = worldCache.get(chunkCoord);
            if (horseCache == null) {
                horseCache = new ArrayList<>();
                worldCache.put(chunkCoord, horseCache);
            }
            horseCache.add(this);
        }
    }

    static List<EquestriHorse> horsesInChunk(Chunk chunk) {
        List<EquestriHorse> result = new ArrayList<>();
        Map<Long, List<EquestriHorse>> worldCache = CHUNKS.get(chunk.getWorld().getName());
        if (worldCache == null) return result;
        long c = ((long)chunk.getZ() << 32) | (long)chunk.getX();
        List<EquestriHorse> horseCache = worldCache.get(c);
        if (horseCache != null) result.addAll(horseCache);
        return result;
    }

    static void clearChunkCache() {
        CHUNKS.clear();
    }
}
