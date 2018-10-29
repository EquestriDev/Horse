package net.equestriworlds.horse;

import java.util.HashSet;
import java.util.UUID;
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
// TODO
// Inventory (part of NBT)
final class HorseData {
    private int id = -1;
    // Identity
    private String name;
    private UUID owner;
    private String ownerName;
    private HorseGender gender;
    // EquestriWorlds Properties
    private HorseBreed breed;
    private int age;
    // Minecraft Properties
    private HorseColor color;
    private HorseMarkings markings;
    private double jumpStrength;
    private double movementSpeed;
    // Access
    private HashSet<UUID> trusted = new HashSet<>();
    // Entity
    HorseLocation location; // optional
    Breeding breeding; // optional
    Grooming grooming; // optonal
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

    enum BreedingStage {
        PREGNANT,
        MARE_RECOVERY,
        STALLION_RECOVERY,
        FOAL,
        RIDABLE,
        ABORT_RECOVERY,
        BREEDABLE;
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

    void loadProperties(AbstractHorse horse) {
        this.name = horse.getCustomName();
        if (horse instanceof Horse) {
            this.color = HorseColor.of(((Horse)horse).getColor());
            this.markings = HorseMarkings.of(((Horse)horse).getStyle());
        }
        this.jumpStrength = horse.getJumpStrength();
        this.movementSpeed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
    }

    void applyProperties(AbstractHorse horse) {
        horse.setCustomName(this.name);
        if (horse instanceof Horse) {
            ((Horse)horse).setColor(this.color.bukkitColor);
            ((Horse)horse).setStyle(this.markings.bukkitStyle);
        }
        horse.setJumpStrength(this.jumpStrength);
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(this.movementSpeed);
        horse.setAge(this.age);
        if (owner != null) {
            AnimalTamer player = Bukkit.getPlayer(owner);
            if (player == null) player = Bukkit.getOfflinePlayer(owner);
            horse.setTamed(true);
            if (player != null) horse.setOwner(player);
        }
    }

    void clearLocation() {
        this.location = null;
    }

    void storeLocation(Location bukkitLocation) {
        this.location = new HorseLocation(bukkitLocation);
    }

    void storeOwner(Player player) {
        owner = player.getUniqueId();
        ownerName = player.getName();
    }

    boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUniqueId());
    }
}
