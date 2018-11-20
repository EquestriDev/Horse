package net.equestriworlds.horse;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
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

/**
 * HorseData are completely (de)serializable via Gson and will be
 * stored in the `horses.db` as such.
 */
@Data
final class HorseData {
    private int id = -1;
    // Timing
    private long lastSeen; // Unix Time
    // Identity
    private String name;
    private UUID owner;
    private int motherId = -1, fatherId = -1;
    // EquestriWorlds Properties
    private HorseGender gender;
    private long born; // Unix Time
    private HorseAge age;
    private int ageCooldown;
    private HorseBreed breed;
    // Minecraft Properties
    private HorseColor color;
    private HorseMarkings markings;
    private double jump, speed;
    // Feed
    private double body = 4.0; // See BodyConditionScale
    private double hydration = 5.0;
    private int eatCooldown = Util.ONE_HOUR;
    private int drinkCooldown = Util.ONE_HOUR;
    private int burnFatCooldown = Util.ONE_HOUR;
    private int dehydrateCooldown = Util.ONE_HOUR;
    // Breeding
    BreedingStage breedingStage = BreedingStage.READY;
    int breedingCooldown = 0;
    // Access
    private HashSet<UUID> trusted = new HashSet<>();
    // Optionals
    private Pregnancy pregnancy;
    private String armor, saddle;
    private HorseLocation location;
    private CrosstieData crosstie;
    private HorseBrand brand; // See HorseBrand
    private GroomingData grooming;
    private Health health;

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
            this.x = Util.roundDouble(bukkitLocation.getX(), 2);
            this.y = Util.roundDouble(bukkitLocation.getY(), 2);
            this.z = Util.roundDouble(bukkitLocation.getZ(), 2);
            this.pitch = Util.roundFloat(bukkitLocation.getPitch(), 2);
            this.yaw = Util.roundFloat(bukkitLocation.getYaw(), 2);
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
    static final class GroomingData {
        long expiration;
        int appearance;
        int wash, clip, brush, hoof, comb, shed, hair, oil, sheen;
        transient long cooldown;
    }

    @Data
    static final class Health {
        boolean immortal;
        boolean vaccinated;
        double temperature, pulse, eyes, hydration, body, due;
    }

    @Data
    static final class Pregnancy {
        enum Flag {
            OVERWEIGHT,
            UNDERWEIGHT,
            PREMATURE,
            MISCARRIAGE;
        }
        long conceived;
        int partnerId = -1;
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
    }

    // --- Util

    double getJumpHeight() {
        double x = this.jump;
        return -0.1817584952 * Math.pow(x, 3) + 3.689713992 * Math.pow(x, 2) + 2.128599134 * x - 0.343930367;
    }

    String getStrippedName() {
        return ChatColor.stripColor(this.name);
    }

    String getMaskedName() {
        return ChatColor.RESET + this.name + ChatColor.RESET;
    }

    // --- Entity Properties

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
        entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(this.crosstie == null ? this.speed : 0.0);
        if (this.age == HorseAge.FOAL) {
            entity.setAge(Integer.MIN_VALUE);
        } else if (this.breedingStage == BreedingStage.READY) {
            entity.setAge(0);
        } else {
            entity.setAge(Integer.MAX_VALUE);
        }
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

    String getOwnerName(HorsePlugin plugin) {
        if (this.owner == null) return "Nobody";
        String result = plugin.cachedPlayerName(this.owner);
        return result == null ? "N/A" : result;
    }

    Player getOwningPlayer() {
        return this.owner == null ? null : Bukkit.getPlayer(this.owner);
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

    // --- Timing

    void passSecond(long now) {
        if (this.ageCooldown > 0) this.ageCooldown -= 1;
        if (this.eatCooldown > 0) this.eatCooldown -= 1;
        if (this.drinkCooldown > 0) this.drinkCooldown -= 1;
        if (this.burnFatCooldown > 0) this.burnFatCooldown -= 1;
        if (this.breedingCooldown > 0) this.breedingCooldown -= 1;
        this.lastSeen = now;
    }

    // --- Body

    BodyConditionScale getBodyCondition() {
        return BodyConditionScale.of(this.body);
    }

    void setBodyCondition(BodyConditionScale scale) {
        this.body = (double)scale.score;
    }

    boolean isHungry() {
        return eatCooldown == 0;
    }

    void setHungry(boolean hungry) {
        this.eatCooldown = hungry ? 0 : 3600;
    }

    boolean isThirsty() {
        return drinkCooldown == 0;
    }

    void setThirsty(boolean thirsty) {
        this.drinkCooldown = thirsty ? 0 : 3600;
    }

    void setBody(double val) {
        this.body = Util.roundDouble(val, 6);
    }

    // Hydration

    HydrationLevel getHydrationLevel() {
        return HydrationLevel.of(this.hydration);
    }

    void setHydrationLevel(HydrationLevel level) {
        this.hydration = (double)level.score;
    }

    void setHydration(double val) {
        this.hydration = Util.roundDouble(val, 6);
    }

    // --- Parents

    HorseData getMother(HorsePlugin plugin) {
        return this.motherId < 0 ? null : plugin.findHorse(this.motherId);
    }

    HorseData getFather(HorsePlugin plugin) {
        return this.fatherId < 0 ? null : plugin.findHorse(this.fatherId);
    }
}
