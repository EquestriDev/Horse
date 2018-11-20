package net.equestriworlds.horse;

import lombok.Getter;

@Getter
enum HydrationLevel implements HumanReadable {
    DEHYDRATED(0),
    NEED_WATER(1, "In need of water"),
    THIRSTY   (2),
    PARCHED   (3),
    HYDRATED  (4);

    public static final double MAX_VALUE = 5.0;
    public final String humanName;
    public final int score;

    HydrationLevel(int score, String humanName) {
        this.score = score;
        this.humanName = humanName;
    }

    HydrationLevel(int score) {
        this.score = score;
        this.humanName = HumanReadable.enumToHuman(this);
    }

    static HydrationLevel of(double hydration) {
        int sc = (int)Math.floor(hydration);
        if (sc <= 0) return DEHYDRATED;
        if (sc >= 4) return HYDRATED;
        for (HydrationLevel lvl: values()) {
            if (lvl.score == sc) return lvl;
        }
        return DEHYDRATED;
    }
}
