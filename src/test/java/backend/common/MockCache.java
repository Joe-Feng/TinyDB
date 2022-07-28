package backend.common;

/**
 * @author Joe
 * @ClassName MockCache.java
 * @Description
 * @createTime 2022年07月28日 21:48:00
 */
public class MockCache  extends AbstractCache<Long> {

    public MockCache() {
        super(50);
    }

    @Override
    protected Long getForCache(long key) throws Exception {
        return key;
    }

    @Override
    protected void releaseForCache(Long obj) {}

}
