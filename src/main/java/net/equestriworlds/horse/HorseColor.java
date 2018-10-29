package net.equestriworlds.horse;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Horse;

enum HorseColor {
    WHITE   ("White",    Horse.Color.WHITE),
    CREAMY  ("Buckskin", Horse.Color.CREAMY),
    CHESTNUT("Chestnut", Horse.Color.CHESTNUT),
    BAY     ("Bay",      Horse.Color.BROWN),
    BLACK   ("Black",    Horse.Color.BLACK),
    GRAY    ("Gray",     Horse.Color.GRAY),
    DARK_BAY("Dark Bay", Horse.Color.DARK_BROWN),
    ;

    public final String humanName;
    public final Horse.Color bukkitColor;
    public static final HorseColor[] ALL_COLORS = values();

    HorseColor(String humanName, Horse.Color bukkitColor) {
        this.humanName = humanName;
        this.bukkitColor = bukkitColor;
    }

    static HorseColor of(Horse.Color bukkitColor) {
        for (HorseColor i: values()) {
            if (i.bukkitColor == bukkitColor) return i;
        }
        throw new IllegalStateException("Uncovered horse color: " + bukkitColor);
    }

    static HorseColor random() {
        return ALL_COLORS[ThreadLocalRandom.current().nextInt(ALL_COLORS.length)];
    }
}
