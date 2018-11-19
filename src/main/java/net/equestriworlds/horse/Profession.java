package net.equestriworlds.horse;

import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import org.bukkit.entity.Player;

/**
 * Professions are implemented as permissions (probably assigned to
 * ranks) which are acquired via staff application.
 */
@Getter
public enum Profession implements HumanReadable {
    VET,
    FARRIER,
    FLOATER;

    public final String permission;
    public final String humanName;

    Profession() {
        this.permission = "horse." + name().toLowerCase();
        this.humanName = HumanReadable.enumToHuman(this);
    }

    public boolean has(Player player) {
        return player.hasPermission(this.permission);
    }

    public Set<Profession> of(Player player) {
        EnumSet<Profession> result = EnumSet.noneOf(Profession.class);
        for (Profession p: values()) {
            if (p.has(player)) {
                result.add(p);
            }
        }
        return result;
    }
}
