package dio.budgeting.domain;

public record TransactionSort(Field field, Direction direction) {
    public static final TransactionSort DEFAULT = new TransactionSort(Field.CREATED_AT, Direction.DESC);

    public enum Field {
        CREATED_AT,
        AMOUNT
    }

    public enum Direction {
        ASC,
        DESC
    }
}
