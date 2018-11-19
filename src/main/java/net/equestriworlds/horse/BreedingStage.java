package net.equestriworlds.horse;

import lombok.Getter;

/**
 * Stages of breeding.  Pregnant, labor and nurture only apply to
 * mares.
 */
@Getter
public enum BreedingStage implements HumanReadable {
    READY,
    PREGNANT,
    LABOR,    // Final 2 hours of pregnancy
    NURTURE,
    RECOVERY;

    public final String humanName;

    BreedingStage() {
        this.humanName = HumanReadable.enumToHuman(this);
    }
}
