package dio.budgeting.support.mongo;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

public class MongoCleaner {

    private final MongoTemplate mongoTemplate;

    public MongoCleaner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void clean() {
        mongoTemplate
                .getCollectionNames()
                .forEach(collection -> mongoTemplate.getCollection(collection).deleteMany(new Document()));
    }
}
