package dio.budgeting.support;

public final class ApiPaths {

    public static final String BASE_PATH = "/api/v1";
    public static final String TRANSACTIONS_PATH = BASE_PATH + "/transactions";
    public static final String VOICE_COMMAND_PATH = TRANSACTIONS_PATH + "/ai";

    private ApiPaths() {}
}
