package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AllArgsConstructor;
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

@Data
// TODO: Inventory (part of NBT)
final class HorseData {
    private int id = -1;
    // Identity
    private String name;
    private Equestrian owner;
    // EquestriWorlds Properties
    private HorseGender gender;
    private long born;
    private HorseAge age;
    private HorseBreed breed;
    // Minecraft Properties
    private HorseColor color;
    private HorseMarkings markings;
    private double jump, speed;
    // Access
    private HashSet<Equestrian> trusted = new HashSet<>();
    // Entity
    HorseLocation location; // optional
    Breeding breeding; // optional
    Grooming grooming; // optonal
    Health health;

    @Data @AllArgsConstructor
    static final class Equestrian {
        public final UUID uuid;
        String name;
    }

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

    enum BreedingStage {
        FOAL,
        RIDABLE,
        BREEDABLE,
        PREGNANT,
        MARE_RECOVERY,
        STALLION_RECOVERY,
        ABORT_RECOVERY;
    }

    @Data
    static final class Breeding {
        BreedingStage stage;
        int pregnancyTime;
        int partnerId;
    }

    @Data
    static final class Grooming {
        int appearance;
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

    void applyProperties(AbstractHorse horse) {
        horse.setCustomName(this.name + " " + this.gender.color + this.gender.symbol);
        if (horse instanceof Horse) {
            ((Horse)horse).setColor(this.color.bukkitColor);
            ((Horse)horse).setStyle(this.markings.bukkitStyle);
        }
        horse.setJumpStrength(this.jump);
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(this.speed);
        horse.setAge(this.age.minecraftAge);
        horse.setAgeLock(true);
        horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        horse.setHealth(20.0);
        if (owner != null) {
            horse.setTamed(true);
            AnimalTamer player = Bukkit.getPlayer(this.owner.uuid);
            if (player == null) player = Bukkit.getOfflinePlayer(this.owner.uuid);
            if (player != null) horse.setOwner(player);
        } else {
            horse.setTamed(false);
            horse.setOwner(null);
        }
        horse.getInventory().setSaddle(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SADDLE));
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
        this.owner = new Equestrian(player.getUniqueId(), player.getName());
    }

    boolean isOwner(Player player) {
        return this.owner != null && this.owner.uuid.equals(player.getUniqueId());
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
        if (this.owner != null && this.owner.uuid.equals(playerId)) return true;
        for (Equestrian trustee: this.trusted) {
            if (trustee.uuid.equals(playerId)) return true;
        }
        return false;
    }

    boolean canAccess(Player player) {
        return canAccess(player.getUniqueId());
    }
}
