package net.equestriworlds.horse;

import java.util.UUID;
import lombok.Value;

/**
 * Serialized as part of HorseData.  One per player.  Unchangeable
 * forever.
 */
@Value
final class HorseBrand {
    private final UUID owner;
    private final String format;
}
