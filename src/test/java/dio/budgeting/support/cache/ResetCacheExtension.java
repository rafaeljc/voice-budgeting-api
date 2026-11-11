package dio.budgeting.support.cache;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class ResetCacheExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        var cacheManager = SpringExtension.getApplicationContext(context).getBean(CacheManager.class);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
}
