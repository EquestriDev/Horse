package net.equestriworlds.horse;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.ArmoredHorseInventory;
import org.bukkit.inventory.ItemStack;

@Data
final class HorseData {
    private int id = -1;
    // Identity
    private String name;
    private UUID owner;
    // EquestriWorlds Properties
    private HorseGender gender;
    private long born;
    private HorseAge age;
    private HorseBreed breed;
    // Minecraft Properties
    private HorseColor color;
    private HorseMarkings markings;
    private double jump, speed;
    // Health
    private double body = 4.0; // See BodyCondistionScale
    // Access
    private HashSet<UUID> trusted = new HashSet<>();
    // Optionals
    String armor, saddle;
    HorseLocation location;
    CrosstieData crosstie;
    HorseBrand brand;
    Breeding breeding;
    GroomingData grooming;
    Health health;

    @Value
    static final class HorseLocation {
        public final String world;
        public final double x, y, z;
        public final float pitch, yaw;
        public final int cx, cz;
        public final long chunkIndex;
        World bukkitWorld() {
            return Bukkit.getWorld(this.world);
        }
        Location bukkitLocation() {
            World bworld = Bukkit.getWorld(this.world);
            if (bworld == null) return null;
            return new Location(bworld, this.x, this.y, this.z, this.yaw, this.pitch);
        }
        HorseLocation(Location bukkitLocation) {
            this.world = bukkitLocation.getWorld().getName();
            this.x = bukkitLocation.getX();
            this.y = bukkitLocation.getY();
            this.z = bukkitLocation.getZ();
            this.pitch = bukkitLocation.getPitch();
            this.yaw = bukkitLocation.getYaw();
            this.cx = bukkitLocation.getBlockX() >> 4;
            this.cz = bukkitLocation.getBlockZ() >> 4;
            this.chunkIndex = ((long)cz << 32) | (long)cx;
        }
    }

    @Value
    static final class CrosstieData {
        public final List<Integer> hitchA;
        public final List<Integer> hitchB;
    }

    @Data
    static final class Breeding {
        enum BreedingStage {
            READY,
            PREGNANT,
            MARE_RECOVERY,
            STALLION_RECOVERY,
            ABORT_RECOVERY;
        }
        BreedingStage stage = BreedingStage.READY;
        long breedingTime;
        int partnerId = -1;
    }

    @Data
    static final class GroomingData {
        long cooldown, expiration;
        int appearance;
        int wash, clip, brush, hoof, comb, shed, hair, oil, sheen;
    }

    @Data
    static final class Health {
        // EnumSet<HorseSymptom> symptoms; // TODO
        // Disease disease; // TODO
        boolean immortal;
        boolean vaccinated;
        double temperature, pulse, eyes, hydration, body, due;
    }

    // --- Util

    double getJumpHeight() {
        double x = this.jump;
        return -0.1817584952 * Math.pow(x, 3) + 3.689713992 * Math.pow(x, 2) + 2.128599134 * x - 0.343930367;
    }

    // --- Entity Properties

    void loadProperties(AbstractHorse horse) {
        this.name = horse.getCustomName();
        if (horse instanceof Horse) {
            this.color = HorseColor.of(((Horse)horse).getColor());
            this.markings = HorseMarkings.of(((Horse)horse).getStyle());
        }
        this.jump = horse.getJumpStrength();
        this.speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
    }

    /**
     * Called by HorsePlugin#prepareHorseEntity().
     * Apply all the stored horse properties to the horse entity.
     */
    void applyProperties(AbstractHorse entity) {
        entity.setCustomName(this.name + " " + this.gender.chatColor + this.gender.symbol);
        if (entity instanceof Horse) {
            Horse horseEntity = (Horse)entity;
            if (this.color != null) horseEntity.setColor(this.color.bukkitColor);
            if (this.markings != null) horseEntity.setStyle(this.markings.bukkitStyle);
        }
        entity.setJumpStrength(this.jump);
        entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(this.crosstie != null ? this.speed : 0.0);
        entity.setAge(this.age.minecraftAge);
        entity.setAgeLock(true);
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        entity.setHealth(20.0);
        if (owner != null) {
            entity.setTamed(true);
            AnimalTamer player = Bukkit.getPlayer(this.owner);
            if (player == null) player = Bukkit.getOfflinePlayer(this.owner);
            if (player != null) entity.setOwner(player);
        } else {
            entity.setTamed(false);
            entity.setOwner(null);
        }
    }

    /**
     * Called by HorsePlugin#prepareHorseEntity().
     * Apply the stored inventory items to the horse entity.
     */
    void applyInventory(HorsePlugin plugin, AbstractHorse entity) {
        Gson gson = new Gson();
        AbstractHorseInventory inv = entity.getInventory();
        inv.setSaddle(this.saddle == null ? null : plugin.getDirtyNBT().deserializeItem((Map<String, Object>)gson.fromJson(this.saddle, Map.class)));
        if (inv instanceof ArmoredHorseInventory) {
            ((ArmoredHorseInventory)inv).setArmor(this.armor == null ? null : plugin.getDirtyNBT().deserializeItem((Map<String, Object>)gson.fromJson(this.armor, Map.class)));
        }
    }

    // --- Location

    void clearLocation() {
        this.location = null;
    }

    void storeLocation(Location bukkitLocation) {
        this.location = new HorseLocation(bukkitLocation);
    }

    // --- Owner

    void storeOwner(Player player) {
        this.owner = player.getUniqueId();
    }

    boolean isOwner(Player player) {
        return player.getUniqueId().equals(owner);
    }

    // --- Inventory

    void storeInventory(HorsePlugin plugin, AbstractHorse entity) {
        Gson gson = new Gson();
        AbstractHorseInventory inv = entity.getInventory();
        ItemStack saddleItem = inv.getSaddle();
        this.saddle = saddleItem == null ? null : gson.toJson(plugin.getDirtyNBT().serializeItem(saddleItem));
        if (inv instanceof ArmoredHorseInventory) {
            ItemStack armorItem = ((ArmoredHorseInventory)inv).getArmor();
            this.armor = armorItem == null ? null : gson.toJson(plugin.getDirtyNBT().serializeItem(armorItem));
        }
    }

    // --- Randomization

    private static <T extends Enum> T randomEnum(T[] es, Random random) {
        return es[random.nextInt(es.length)];
    }

    private static <T extends Enum> T randomEnum(Set<T> es, Random random) {
        if (es.isEmpty()) return null;
        return new ArrayList<T>(es).get(random.nextInt(es.size()));
    }

    void randomize(HorsePlugin plugin) {
        Random random = ThreadLocalRandom.current();
        randomize(plugin, "name", random);
        randomize(plugin, "gender", random);
        randomize(plugin, "age", random);
        randomize(plugin, "breed", random);
        randomize(plugin, "color", random);
        randomize(plugin, "markings", random);
        randomize(plugin, "jump", random);
        randomize(plugin, "speed", random);
        randomize(plugin, "markings", random);
        randomize(plugin, "jump", random);
        randomize(plugin, "speed", random);
    }

    Object randomize(HorsePlugin plugin, String field, Random random) {
        switch (field) {
        case "name": return this.name = plugin.randomHorseName(random);
        case "gender": return this.gender = randomEnum(HorseGender.values(), random);
        case "age": return this.age = randomEnum(HorseAge.values(), random);
        case "breed": return this.breed = randomEnum(HorseBreed.values(), random);
        case "color": return this.color = randomEnum(this.breed.colors, random);
        case "markings": return this.markings = randomEnum(this.breed.markings, random);
        case "jump": return this.jump = 0.4 + random.nextDouble() * 0.3 + random.nextDouble() * 0.3; // 0.4 - 1.0, skewing towards 0.7
        case "speed": return this.speed = 0.1125 + random.nextDouble() * 0.1125 + random.nextDouble() * 0.1125; // 0.1125 - 0.3375
        default: return null;
        }
    }

    Object randomize(HorsePlugin plugin, String field) {
        return randomize(plugin, field, ThreadLocalRandom.current());
    }

    // --- Access

    boolean canAccess(UUID playerId) {
        return playerId.equals(this.owner) || this.trusted.contains(playerId);
    }

    boolean canAccess(Player player) {
        return canAccess(player.getUniqueId());
    }
}
