package net.equestriworlds.horse;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;

@Getter
enum HorseGender implements HumanReadable {
    MARE("\u2640", ChatColor.BLUE),
    STALLION("\u2642", ChatColor.LIGHT_PURPLE),
    GELDING("\u26b2", ChatColor.GREEN);

    public final String humanName;
    public final String symbol;
    public final ChatColor color;

    HorseGender(String symbol, ChatColor color) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.symbol = symbol;
        this.color = color;
    }

    boolean isFemale() {
        return this == MARE;
    }

    boolean isMale() {
        return this != MARE;
    }
}
