package net.equestriworlds.horse;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;

/**
 * Provide runtime data on spawned horses.
 */
@Getter @Setter @RequiredArgsConstructor
final class SpawnedHorse {
    public final HorseData data;
    AbstractHorse entity;
    long ticksLived;
    UUID following;
    Crosstie crosstie;

    void despawn() {
        if (entity == null) return;
        entity.remove();
        entity = null;
        if (crosstie != null) removeCrosstie();
    }

    boolean isPresent() {
        return entity != null && entity.isValid();
    }

    boolean represents(AbstractHorse otherEntity) {
        return this.entity != null && this.entity.equals(otherEntity);
    }

    boolean isCrosstied() {
        return this.crosstie != null;
    }

    boolean canFreeroam() {
        if (this.crosstie != null) return false;
        if (this.entity != null && this.entity.isValid()) {
            if (!this.entity.getPassengers().isEmpty()) return false;
            if (this.entity.isLeashed()) return false;
        }
        return true;
    }

    void setupCrosstie(Crosstie newCrosstie) {
        if (this.crosstie != null) throw new IllegalStateException("crosstie already set");
        if (newCrosstie.isValid()) throw new IllegalStateException("crosstie already validated");
        this.crosstie = newCrosstie;
        this.crosstie.setValid(true);
        if (!this.crosstie.check()) throw new IllegalStateException("new crosstie immediately fails check");
        this.entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
        this.entity.setAI(false);
    }

    void removeCrosstie() {
        if (this.crosstie == null) throw new IllegalStateException("crosstie already null");
        this.crosstie.remove();
        this.crosstie = null;
        if (this.entity != null && this.entity.isValid()) {
            this.entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(data.getSpeed());
            this.entity.setAI(true);
        }
    }
}
