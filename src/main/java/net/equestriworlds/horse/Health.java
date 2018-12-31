package net.equestriworlds.horse;

import lombok.Data;

public final class Health {
    @Data
    public static final class Persistence {
        public static final String EXTRA_KEY = "health";
        boolean immortal;
        boolean vaccinated;
        double temperature, pulse, eyes, hydration, body, due;
    }
}
