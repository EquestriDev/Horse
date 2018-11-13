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
    public final double score;

    BodyConditionScale(int score) {
        this.humanName = HumanReadable.enumToHuman(this);
        this.score = (double)score;
    }

    static BodyConditionScale of(double body) {
        BodyConditionScale[] vs = values();
        for (int i = vs.length - 1; i >= 0; i -= 1) {
            if (body >= vs[i].score) return vs[i];
        }
        return EXTREMELY_UNDERWEIGHT;
    }
}
