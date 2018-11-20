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
    FOAL      (0, 7),
    YEARLING  (7, 7), // Not ridable
    ADOLESCENT(14, 14), // Ridable, not breedable
    ADULT     (28, 0);

    public final String humanName;
    public final int daysOld;
    public final int duration;

    HorseAge(int daysOld, int duration) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.daysOld = daysOld;
        this.duration = duration;
    }

    HorseAge next() {
        switch (this) {
        case FOAL: return YEARLING;
        case YEARLING: return ADOLESCENT;
        case ADOLESCENT: return ADULT;
        case ADULT:
        default:
            throw new IllegalStateException("cannot call next() on HorseAge.ADULT");
        }
    }

    String indefiniteArticle() {
        return this == FOAL || this == YEARLING ? "a" : "an";
    }
}
