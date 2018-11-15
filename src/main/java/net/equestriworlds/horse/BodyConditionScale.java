package net.equestriworlds.horse;

import lombok.Getter;

@Getter
enum BodyConditionScale implements HumanReadable {
    EXTREMELY_UNDERWEIGHT(0),
    UNDERWEIGHT(1),
    THIN       (2),
    MILD_THIN  (3),
    HEALTHY    (4),
    MILD_FLESHY(5),
    FLESHY     (6),
    OVERWEIGHT (7),
    OBESE      (8),
    VERY_OBESE (9);

    public final String humanName;
    public final int score;

    BodyConditionScale(int score) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.score = score;
    }

    static BodyConditionScale of(double body) {
        BodyConditionScale[] vs = values();
        int bodyInt = (int)Math.floor(body);
        if (bodyInt < 0) return EXTREMELY_UNDERWEIGHT;
        if (bodyInt > 9) return VERY_OBESE;
        for (BodyConditionScale scale: values()) {
            if (bodyInt == scale.score) return scale;
        }
        return EXTREMELY_UNDERWEIGHT;
    }
}
