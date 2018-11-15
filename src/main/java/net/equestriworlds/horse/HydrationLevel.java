package net.equestriworlds.horse;

import lombok.Getter;

@Getter
enum HydrationLevel implements HumanReadable {
    DEHYDRATED(0),
    NEED_WATER(1, "In need of water"),
    THIRSTY   (2),
    PARCHED   (3),
    HYDRATED  (4);

    public final String humanName;
    public final double score;

    HydrationLevel(int score, String humanName) {
        this.score = (double)score;
        this.humanName = humanName;
    }

    HydrationLevel(int score) {
        this.score = (double)score;
        this.humanName = HumanReadable.enumToHuman(this);
    }

    static HydrationLevel of(double hydration) {
        HydrationLevel[] vs = values();
        for (int i = vs.length - 1; i >= 0; i -= 1) {
            if (hydration >= vs[i].score) return vs[i];
        }
        return DEHYDRATED;
    }
}
