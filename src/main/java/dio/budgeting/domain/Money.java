package dio.budgeting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal value) {
    public static final String CURRENCY = "BRL";

    private static final int SCALE = 2;
    private static final int MAX_INTEGER_DIGITS = 16;

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("Money value must not be null");
        }
        if (value.scale() != SCALE) {
            throw new IllegalArgumentException("Money value must have exactly " + SCALE + " decimal places");
        }
        if (value.precision() - SCALE > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(
                    "Money value must have at most " + MAX_INTEGER_DIGITS + " integer digits");
        }
    }

    public static Money of(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Money value must not be null");
        }
        try {
            return new Money(value.setScale(SCALE, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Money value must not need rounding to " + SCALE + " decimal places", e);
        }
    }
}
