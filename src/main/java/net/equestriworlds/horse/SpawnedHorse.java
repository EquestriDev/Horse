package net.equestriworlds.horse;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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

    void despawn() {
        if (entity == null) return;
        entity.remove();
        entity = null;
    }

    boolean isPresent() {
        return entity != null && entity.isValid();
    }

    boolean represents(AbstractHorse otherEntity) {
        return this.entity != null && this.entity.equals(otherEntity);
    }
}
