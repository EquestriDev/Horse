package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

@Getter
public enum Disease implements HumanReadable {
    INFLUENZA_VIRUS,
    WEST_NILE_FEVER,
    TETANUS,
    COLIC,
    STRANGLES,
    EQUINE_HERPESVIRUS("EHV"),
    MANGE,
    PULLED_TENDON,
    BOTULISM,
    COMMON_WOUND,
    BROKEN_BONE,
    BONE_CHIP,
    PREGNANCY,
    WORMS;

    public final String humanName;

    Disease(String humanName) {
        this.humanName = humanName;
    }

    Disease() {
        this.humanName = HumanReadable.enumToHuman(this);
    }

    @Getter
    public enum Discovery implements HumanReadable {
        BLOODWORK,
        SALIVA,
        NASAL_SWAB,
        COAT_SAMPLE,
        SKIN_SAMPLE,
        ULTRASOUND,
        URINE_SAMPLE,
        FECES_SAMPLE,
        XRAY("X-Ray");

        public final String humanName;

        Discovery() {
            this.humanName = HumanReadable.enumToHuman(this);
        }

        Discovery(String humanName) {
            this.humanName = humanName;
        }
    }

    double getContagiousness() {
        switch (this) {
        case INFLUENZA_VIRUS: return 0.25;
        default: return 0;
        }
    }

    double getUnvaccinatedChance() {
        switch (this) {
        case INFLUENZA_VIRUS: return 0.04;
        default: return 0;
        }
    }

    PotionData getVaccinePotion() {
        switch (this) {
        case INFLUENZA_VIRUS: return new PotionData(PotionType.NIGHT_VISION, false, false);
        default: return null;
        }
    }

    List<Symptom> getSymptoms() {
        switch (this) {
        case INFLUENZA_VIRUS: return Arrays.asList(Symptom.CHILLS, Symptom.DEHYDRATION, Symptom.FEVER);
        default: return Collections.emptyList();
        }
    }

    List<Discovery> getDiscoveries() {
        switch (this) {
        case INFLUENZA_VIRUS: return Arrays.asList(Discovery.BLOODWORK, Discovery.SALIVA, Discovery.NASAL_SWAB);
        default: return Collections.emptyList();
        }
    }
}
