package dio.budgeting.support;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ImportTestcontainers(MySqlTestContainer.class)
@Import(MySqlCleaner.class)
@ExtendWith(ResetMySqlExtension.class)
public @interface IntegrationTest {}
