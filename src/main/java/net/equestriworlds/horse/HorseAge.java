package net.equestriworlds.horse;

import lombok.Getter;

/**
 * Abstraction of horse age.  A horse is a baby for 7 days, yearling
 * until day 14 and adolescent until day 28.  After that, they are
 * considered adult.
 *
 * Yearlings appear as grown up yet are not yet ridable.  Adolescents
 * are ridable, but not yet breedable.  Adults have none of the
 * limitations above.
 */
@Getter
enum HorseAge implements HumanReadable {
    FOAL      (-1,  0),
    YEARLING  (0,   7), // Not ridable
    ADOLESCENT(0,  14), // Ridable, not breedable
    ADULT     (0,  28);

    public final String humanName;
    public final int minecraftAge;
    public final int daysOld;

    HorseAge(int minecraftAge, int daysOld) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.minecraftAge = minecraftAge;
        this.daysOld = daysOld;
    }
}
