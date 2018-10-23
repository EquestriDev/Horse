package net.equestriworlds.horse;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Horse;

enum HorseMarkings {
    SOLID      ("Solid",      Horse.Style.NONE),
    SOCKS      ("Socks",      Horse.Style.WHITE),
    WHITEFIELD ("Whitefield", Horse.Style.WHITEFIELD),
    WHITE_DOTS ("Snowflake",  Horse.Style.WHITE_DOTS),
    BLACK_DOTS ("Sooty",      Horse.Style.BLACK_DOTS),
    ;

    public final String humanName;
    public final Horse.Style bukkitStyle;
    public static final HorseMarkings[] ALL_MARKINGS = values();

    HorseMarkings(String humanName, Horse.Style bukkitStyle) {
        this.humanName = humanName;
        this.bukkitStyle = bukkitStyle;
    }

    static HorseMarkings of(Horse.Style bukkitStyle) {
        for (HorseMarkings i: values()) {
            if (i.bukkitStyle == bukkitStyle) return i;
        }
        throw new IllegalStateException("Uncovered horse style: " + bukkitStyle);
    }

    static HorseMarkings random() {
        return ALL_MARKINGS[ThreadLocalRandom.current().nextInt(ALL_MARKINGS.length)];
    }
}
