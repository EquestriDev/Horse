package net.equestriworlds.horse;

import lombok.Getter;

@Getter
public enum BreedingStage implements HumanReadable {
    NOT_PREGNANT,
    PREGNANT,
    RECOVERY;

    public final String humanName;

    BreedingStage() {
        this.humanName = HumanReadable.enumToHuman(this);
    }
}
