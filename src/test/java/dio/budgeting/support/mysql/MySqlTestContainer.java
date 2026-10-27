package dio.budgeting.support.mysql;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mysql.MySQLContainer;

public interface MySqlTestContainer {

    // One container per JVM, shared by every Spring context; same image as compose.yml
    @Container
    @ServiceConnection
    MySQLContainer MYSQL = new MySQLContainer("mysql:9.6");
}
