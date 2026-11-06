package dio.budgeting.support.mongo;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mongodb.MongoDBContainer;

public interface MongoTestContainer {

    // One container per JVM, shared by every Spring context; same image as compose.yml
    @Container
    @ServiceConnection
    MongoDBContainer MONGO = new MongoDBContainer("mongo:9.0");
}
