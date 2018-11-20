package net.equestriworlds.horse;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Util {
    private Util() { }

    public static final int ONE_DAY = 60 * 60 * 24; // One day in seconds

    // --- Double setters

    public static double roundDouble(double v, int digits) {
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(digits, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static float roundFloat(float v, int digits) {
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(digits, RoundingMode.HALF_UP);
        return bd.floatValue();
    }
}
