package net.equestriworlds.horse;

import lombok.Getter;

@Getter
enum HorseAge implements HumanReadable {
    FOAL(-1),
    ADOLESCENT(0), // Ridable
    ADULT(0);

    public final String humanName;
    public final int minecraftAge;

    HorseAge(int minecraftAge) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.minecraftAge = minecraftAge;
    }
}
