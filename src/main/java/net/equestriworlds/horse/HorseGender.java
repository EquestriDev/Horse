package net.equestriworlds.horse;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;

@Getter
enum HorseGender implements HumanReadable {
    MARE("\u2640", ChatColor.LIGHT_PURPLE),
    STALLION("\u2642", ChatColor.BLUE),
    GELDING("\u26b2", ChatColor.GREEN);

    public final String humanName;
    public final String symbol;
    public final ChatColor chatColor;

    HorseGender(String symbol, ChatColor chatColor) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.symbol = symbol;
        this.chatColor = chatColor;
    }

    boolean isFemale() {
        return this == MARE;
    }

    boolean isMale() {
        return this != MARE;
    }

    String pronoun() {
        return this == MARE ? "her" : "him";
    }
}
