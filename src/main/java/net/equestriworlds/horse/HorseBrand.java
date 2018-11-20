package net.equestriworlds.horse;

import java.util.UUID;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;

/**
 * Serialized as part of HorseData.  One per player.  Unchangeable
 * forever.
 */
@Value
final class HorseBrand {
    private final UUID owner;
    private final String format;

    String getMaskedFormat() {
        return ChatColor.RESET + this.format + ChatColor.RESET;
    }
}
