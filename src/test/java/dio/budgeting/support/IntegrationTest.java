package dio.budgeting.support;

import dio.budgeting.support.cache.ResetCacheExtension;
import dio.budgeting.support.mongo.MongoCleaner;
import dio.budgeting.support.mongo.MongoTestContainer;
import dio.budgeting.support.mongo.ResetMongoExtension;
import dio.budgeting.support.mysql.MySqlCleaner;
import dio.budgeting.support.mysql.MySqlTestContainer;
import dio.budgeting.support.mysql.ResetMySqlExtension;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ImportTestcontainers({MySqlTestContainer.class, MongoTestContainer.class})
@Import({MySqlCleaner.class, MongoCleaner.class})
@ExtendWith({ResetMySqlExtension.class, ResetMongoExtension.class, ResetCacheExtension.class})
public @interface IntegrationTest {
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
