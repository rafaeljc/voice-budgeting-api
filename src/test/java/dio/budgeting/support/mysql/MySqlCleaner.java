package dio.budgeting.support.mysql;

import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public class MySqlCleaner {

    private static final String LIST_TABLES =
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

    private final JdbcTemplate jdbcTemplate;

    public MySqlCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        List<String> tables = jdbcTemplate.queryForList(LIST_TABLES, String.class);
        // FOREIGN_KEY_CHECKS is per session, so every statement must run on the same connection
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String table : tables) {
                        statement.execute("TRUNCATE TABLE `" + table + "`");
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not clean the MySQL tables", e);
            }
            return null;
        });
    }
}
