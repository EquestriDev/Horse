package net.equestriworlds.horse;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.bukkit.Location;
import org.bukkit.entity.Bat;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;

/**
 * Runtime object for one crosstie.  Never used for persistence.
 */
@Setter @Getter @RequiredArgsConstructor
final class Crosstie {
    public final SpawnedHorse spawned;
    // Either hitchB or holder may be null.  Never both.
    // Deserialized crossties always set:
    //   hitchB != null && holder == null;
    LeashHitch hitchA, hitchB;
    Player holder = null;
    Bat leashedBat = null; // Leashed by hitchA.
    boolean valid = false; // Creator must set to true.

    @Value
    static class Hitch {
        private final int x, y, z;
    }

    void remove() {
        if (!valid) return;
        this.valid = false;
        if (this.spawned.isPresent()) this.spawned.getEntity().setLeashHolder(null);
        if (this.leashedBat != null && this.leashedBat.isValid()) this.leashedBat.remove();
        this.hitchA = null;
        this.hitchB = null;
        this.holder = null;
        this.leashedBat = null;
    }

    boolean check() {
        if (!this.valid) return false;
        if (!this.spawned.isPresent()) return false;
        if (!this.spawned.getEntity().isLeashed()) return false;
        if (this.hitchA == null || !this.hitchA.isValid()) return false;
        if ((this.hitchB == null || !this.hitchB.isValid()) && (this.holder == null || !this.holder.isValid())) return false;
        if (this.leashedBat != null) this.leashedBat.teleport(this.spawned.getEntity().getEyeLocation().add(0.0, -1.0, 0.0));
        return true;
    }

    HorseData.CrosstieData serialize() {
        Location ha = this.hitchA.getLocation();
        Location hb = this.hitchB.getLocation();
        return new HorseData.CrosstieData(Arrays.asList(ha.getBlockX(), ha.getBlockY(), ha.getBlockZ()),
                                          Arrays.asList(hb.getBlockX(), hb.getBlockY(), hb.getBlockZ()));
    }
}
