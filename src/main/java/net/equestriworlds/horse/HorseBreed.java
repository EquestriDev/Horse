package net.equestriworlds.horse;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.Horse.Color;
import org.bukkit.entity.Horse.Style;

/**
 * An enum of all available horse breeds with their possible colors
 * and styles.  Names are auto generated except in cases where it does
 * not map nicely to a Java enum.
 */
public enum HorseBreed {
    AKHAL_TEKE("Akhal-Teke", EnumSet.allOf(Color.class),
               EnumSet.of(Style.WHITE_DOTS, Style.BLACK_DOTS)),
    AMERICAN_CREAM_DRAFT(EnumSet.of(Color.CREAMY),
                         EnumSet.of(Style.WHITE_DOTS, Style.NONE)), // NONE = Solid
    AMERICAN_PAINT_HORSE(EnumSet.allOf(Color.class),
                         EnumSet.of(Style.WHITEFIELD)),
    AMERICAN_QUARTER_HORSE, // All, All
    AMERICAN_SADDLEBRED,
    ANDALUSIAN,
    APPALOOSA(EnumSet.allOf(Color.class),
              EnumSet.of(Style.WHITE_DOTS)),
    ARABIAN,
    ARDENNES(EnumSet.of(Color.DARK_BROWN, Color.BLACK, Color.CHESTNUT),
             EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))), // No Whitefield
    BELGIAN(EnumSet.of(Color.CREAMY, Color.CHESTNUT),
            EnumSet.of(Style.WHITE, Style.WHITE_DOTS, Style.NONE)), // WHITE = socks
    BRETON(EnumSet.allOf(Color.class),
           EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))),
    CLYDESDALE(EnumSet.allOf(Color.class),
               EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))),
    CHINCOTEAGUE_PONY,
    CONNEMARA,
    CURLY,
    DARTMOOR,
    FJORD, // Fjord type?
    FRENCH_TROTTER(EnumSet.allOf(Color.class),
                   EnumSet.of(Style.NONE, Style.WHITE)),
    FRIESIAN(EnumSet.of(Color.BLACK),
             EnumSet.of(Style.NONE)), // "No markings": Does this mean solid?
    GYPSY_VANNER,
    HACKNEY,
    HAFLINGER(EnumSet.of(Color.CHESTNUT, Color.CREAMY),
              EnumSet.of(Style.WHITE, Style.NONE)),
    HANOVERIAN(EnumSet.allOf(Color.class),
               EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))),
    IRISH_DRAUGHT_HORSE,
    IRISH_SPORT_HORSE,
    ICELANDIC,
    LIPIZZANER(EnumSet.of(Color.GRAY, Color.WHITE, Color.BLACK, Color.DARK_BROWN),
               EnumSet.of(Style.BLACK_DOTS, Style.NONE, Style.WHITE)),
    LUSITANO(EnumSet.of(Color.GRAY, Color.BROWN, Color.DARK_BROWN, Color.CHESTNUT),
             EnumSet.of(Style.NONE, Style.WHITE, Style.BLACK_DOTS)),
    MARWARI,
    MISSOURY_FOX_TROTTER,
    MORGAN,
    MUSTANG,
    OLDENBURG,
    PASO_FINO,
    PERCHERON,
    SELLE_FRANCAIS("Selle Français", EnumSet.of(Color.DARK_BROWN, Color.BROWN, Color.CHESTNUT, Color.BLACK),
                   EnumSet.of(Style.WHITE, Style.BLACK_DOTS, Style.NONE)),
    SHIRE,
    STANDARDBRED(EnumSet.allOf(Color.class),
                 EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))),
    SUFFOLK_PUNCH(EnumSet.of(Color.CHESTNUT, Color.CREAMY),
                  EnumSet.of(Style.WHITE, Style.NONE)),
    TENNESEE_WALKING_HORSE,
    THOROUGHBRED(EnumSet.allOf(Color.class),
                 EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))),
    WELSH_PONY(EnumSet.allOf(Color.class),
               EnumSet.complementOf(EnumSet.of(Style.WHITEFIELD))),
    WARMBLOOD;

    public final String name;
    public final Set<Color> colors;
    public final Set<Style> styles;

    HorseBreed(String name, Set<Color> colors, Set<Style> styles) {
        if (name == null) {
            String[] ns = name().split("_");
            StringBuilder sb = new StringBuilder(ns[0].substring(0, 1))
                .append(ns[0].substring(1).toLowerCase());
            for (int i = 1; i < ns.length; i += 1) {
                sb.append(" ")
                    .append(ns[i].substring(0, 1))
                    .append(ns[i].substring(1).toLowerCase());
            }
            this.name = sb.toString();
        } else {
            this.name = name;
        }
        this.colors = colors;
        this.styles = styles;
    }

    HorseBreed(Set<Color> colors, Set<Style> styles) {
        this((String)null, colors, styles);
    }

    HorseBreed() {
        this((String)null, EnumSet.allOf(Color.class), EnumSet.allOf(Style.class));
    }
}
