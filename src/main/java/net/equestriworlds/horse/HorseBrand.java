package net.equestriworlds.horse;

import java.util.UUID;
import lombok.Data;

/**
 * Serialized as part of HorseData.  One per player.  Unchangeable
 * forever.
 */
@Data
final class HorseBrand {
    private final UUID owner;
    private final String format;
}
