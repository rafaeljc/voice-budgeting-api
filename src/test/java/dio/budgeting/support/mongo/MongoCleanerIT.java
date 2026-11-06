package dio.budgeting.support.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import dio.budgeting.support.IntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

@IntegrationTest
class MongoCleanerIT {

    private static final String COLLECTION = "cleaner_probe";

    private final MongoTemplate mongoTemplate;
    private final MongoCleaner cleaner;

    MongoCleanerIT(MongoTemplate mongoTemplate, MongoCleaner cleaner) {
        this.mongoTemplate = mongoTemplate;
        this.cleaner = cleaner;
    }

    @Test
    void should_removeAllDocuments_when_cleaned() {
        // given
        mongoTemplate.insert(new Document("name", "probe"), COLLECTION);

        // when
        cleaner.clean();

        // then
        assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments()).isZero();
    }
}
