package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import static net.equestriworlds.horse.HorseColor.*;
import static net.equestriworlds.horse.HorseMarkings.*;
import org.bukkit.entity.EntityType;

/**
 * An enum of all available horse breeds with their possible colors
 * and styles.  Names are auto generated except in cases where it does
 * not map nicely to a Java enum.
 */
public enum HorseBreed {
    AKHAL_TEKE("Akhal-Teke", set(ALL_COLORS), set(SOLID, WHITE_DOTS, BLACK_DOTS)),
    AMERICAN_CREAM_DRAFT(set(CREAMY), set(WHITE_DOTS, SOLID)),
    AMERICAN_PAINT_HORSE(set(ALL_COLORS), set(WHITEFIELD)),
    AMERICAN_QUARTER_HORSE(set(ALL_COLORS), set(ALL_MARKINGS)),
    AMERICAN_SADDLEBRED(set(ALL_COLORS), set(ALL_MARKINGS)),
    ANDALUSIAN(set(ALL_COLORS), set(ALL_MARKINGS)),
    APPALOOSA(set(ALL_COLORS), set(WHITE_DOTS)),
    ARABIAN(set(ALL_COLORS), set(ALL_MARKINGS)),
    ARDENNES(set(DARK_BAY, BLACK, CHESTNUT), but(WHITEFIELD)),
    BELGIAN(set(CREAMY, CHESTNUT), set(SOCKS, SOLID)),
    BRETON(set(ALL_COLORS), but(WHITEFIELD)),
    CLYDESDALE(set(ALL_COLORS), but(WHITEFIELD)),
    CHINCOTEAGUE_PONY(set(ALL_COLORS), set(ALL_MARKINGS)),
    CONNEMARA(set(ALL_COLORS), set(ALL_MARKINGS)),
    CURLY(set(ALL_COLORS), set(ALL_MARKINGS)),
    DARTMOOR(set(ALL_COLORS), set(ALL_MARKINGS)),
    DONKEY(EntityType.DONKEY),
    FJORD(EntityType.MULE),
    FRENCH_TROTTER(set(ALL_COLORS), set(SOLID, SOCKS)),
    FRIESIAN(set(BLACK), set(SOLID)),
    GYPSY_VANNER(set(ALL_COLORS), set(ALL_MARKINGS)),
    HACKNEY(set(ALL_COLORS), set(ALL_MARKINGS)),
    HAFLINGER(set(CHESTNUT, CREAMY), set(SOCKS, SOLID)),
    HANOVERIAN(set(ALL_COLORS), but(WHITEFIELD)),
    IRISH_DRAUGHT_HORSE(set(ALL_COLORS), set(ALL_MARKINGS)),
    IRISH_SPORT_HORSE(set(ALL_COLORS), set(ALL_MARKINGS)),
    ICELANDIC(set(ALL_COLORS), set(ALL_MARKINGS)),
    LIPIZZANER(set(GRAY, WHITE, BLACK, DARK_BAY), set(BLACK_DOTS, SOLID, SOCKS)),
    LUSITANO(set(GRAY, BAY, DARK_BAY, CHESTNUT), set(SOLID, SOCKS, BLACK_DOTS)),
    MARWARI(set(ALL_COLORS), set(ALL_MARKINGS)),
    MISSOURY_FOX_TROTTER(set(ALL_COLORS), set(ALL_MARKINGS)),
    MORGAN(set(ALL_COLORS), set(ALL_MARKINGS)),
    MUSTANG(set(ALL_COLORS), set(ALL_MARKINGS)),
    OLDENBURG(set(ALL_COLORS), set(ALL_MARKINGS)),
    PASO_FINO(set(ALL_COLORS), set(ALL_MARKINGS)),
    PERCHERON(set(ALL_COLORS), set(ALL_MARKINGS)),
    SELLE_FRANCAIS("Selle Français", set(DARK_BAY, BAY, CHESTNUT, BLACK), set(SOCKS, BLACK_DOTS, SOLID)),
    SHIRE(set(ALL_COLORS), set(ALL_MARKINGS)),
    STANDARDBRED(set(ALL_COLORS), but(WHITEFIELD)),
    SUFFOLK_PUNCH(set(CHESTNUT, CREAMY), set(SOCKS, SOLID)),
    TENNESEE_WALKING_HORSE(set(ALL_COLORS), set(ALL_MARKINGS)),
    THOROUGHBRED(set(ALL_COLORS), but(WHITEFIELD)),
    WELSH_PONY(set(ALL_COLORS), but(WHITEFIELD)),
    WARMBLOOD(set(ALL_COLORS), set(ALL_MARKINGS)),
    ;

    public final String humanName;
    public final EntityType entityType;
    public final EnumSet<HorseColor> colors;
    public final EnumSet<HorseMarkings> markings;
    public static final HorseBreed[] ALL_BREEDS = values();

    HorseBreed(String humanName, EnumSet<HorseColor> colors, EnumSet<HorseMarkings> markings) {
        this.humanName = humanName == null ? niceEnumName(name()) : humanName;
        this.entityType = EntityType.HORSE;
        this.colors = colors;
        this.markings = markings;
    }

    HorseBreed(EnumSet<HorseColor> colors, EnumSet<HorseMarkings> markings) {
        this((String)null, colors, markings);
    }

    HorseBreed(EntityType entityType) {
        this.humanName = niceEnumName(name());
        this.entityType = entityType;
        this.colors = EnumSet.noneOf(HorseColor.class);
        this.markings = EnumSet.noneOf(HorseMarkings.class);
    }

    private static String niceEnumName(String in) {
        String[] ns = in.split("_");
        StringBuilder sb = new StringBuilder(ns[0].substring(0, 1))
            .append(ns[0].substring(1).toLowerCase());
        for (int i = 1; i < ns.length; i += 1) {
            sb.append(" ")
                .append(ns[i].substring(0, 1))
                .append(ns[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static EnumSet set(Enum... en) {
        return EnumSet.of(en[0], Arrays.copyOfRange(en, 1, en.length));
    }

    private static EnumSet but(Enum en) {
        return EnumSet.complementOf(set(en));
    }

    static HorseBreed random() {
        return ALL_BREEDS[ThreadLocalRandom.current().nextInt(ALL_BREEDS.length)];
    }
}
