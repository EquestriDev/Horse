package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum Symptom implements HumanReadable {
    ANGRY,
    AVOID_FOOT("Will not bear weight on left foot"),
    BLEEDING,
    BRIGHT_EYED("Bright-Eyed"),
    CHILLS,
    CHIN_JAW_ABCESSES("Abcesses on chin and jaw"),
    CONSTIPATION,
    CONTENT,
    COUGH,
    DECREASED_MUSCLE_STRENGTH,
    DEHYDRATION,
    DEPRESSION,
    DIFFICULTY_SWALLOWING,
    DILATED_EYES,
    DYSPNEA("Difficulty Breathing"),
    ENERGETIC,
    EXTREME_LAMENESS,
    FEVER,
    FLAKEY_SKIN,
    GRUMPY,
    HAPPY,
    HEAD_PRESSING,
    INCREASED_HUNGER,
    ITCHY,
    JOYFUL,
    KICKING_AT_STOMACH,
    LOOSE_STOOL,
    LOSS_OF_APPETITE,
    NASAL_DISCHARGE,
    NO_TOLERANCE_TO_MOVEMENT,
    PAIN,
    PANIC,
    PARALYZED_IN_HAUNCHES,
    PATCHY_COAT,
    RELAXED,
    ROLLING,
    SHORTNESS_OF_BREATH,
    SLEEK,
    SLOW_EATING,
    SLOW_MOVING,
    SMELLING_LOOKING_LEG("Smelling and looking at leg"),
    SWELLING,
    WEIGHT_LOSS,
    ;

    public final String humanName;

    Symptom() {
        this.humanName = HumanReadable.enumToHuman(this);
    }

    Symptom(String humanName) {
        this.humanName = humanName;
    }

    public static List<Symptom> getHappySymptoms() {
        return Arrays.asList(HAPPY, JOYFUL, SLEEK, BRIGHT_EYED, ENERGETIC, CONTENT, RELAXED);
    }
}
