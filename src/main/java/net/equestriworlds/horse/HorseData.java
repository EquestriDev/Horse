package net.equestriworlds.horse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Data
final class HorseData {
    private int id;
    // Identity
    private String name;
    private UUID owner;
    private String ownerName;
    // EquestriWorlds Properties
    private HorseBreed breed;
    private int age;
    // Minecraft Properties
    private HorseColor color;
    private HorseMarkings markings;
    private double jumpStrength;
    private double movementSpeed;
    private HorseItem saddle = HorseItem.AIR;
    private HorseItem armor = HorseItem.AIR;
    // Entity
    private UUID entityId;
    // Location (optional)
    HorseLocation location;

    @Value
    final static class HorseLocation {
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
    final static class HorseItem {
        public static final HorseItem AIR = new HorseItem(new ItemStack(Material.AIR));
        public final Material material;
        HorseItem(ItemStack bukkitItem) {
            this.material = bukkitItem == null ? Material.AIR : bukkitItem.getType();
        }
        ItemStack bukkitItem() {
            if (this.material == Material.AIR) return null;
            return new ItemStack(this.material);
        }
    }

    void storeProperties(Horse horse) {
        this.name = horse.getCustomName();
        this.color = HorseColor.of(horse.getColor());
        this.markings = HorseMarkings.of(horse.getStyle());
        this.jumpStrength = horse.getJumpStrength();
        this.movementSpeed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
        this.age = horse.getAge();
        ItemStack item = horse.getInventory().getArmor();
        this.saddle = new HorseItem(horse.getInventory().getSaddle());
        this.armor = new HorseItem(horse.getInventory().getArmor());
    }

    void applyProperties(Horse horse) {
        horse.setCustomName(this.name);
        horse.setColor(this.color.bukkitColor);
        horse.setStyle(this.markings.bukkitStyle);
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
                horse.getInventory().setArmor(armor.bukkitItem());
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        } else {
            horse.getInventory().setArmor(null);
        }
        if (saddle != null) {
            try {
                horse.getInventory().setSaddle(saddle.bukkitItem());
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
