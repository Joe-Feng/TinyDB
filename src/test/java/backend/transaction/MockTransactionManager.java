package backend.transaction;

/**
 * @author Joe
 * @ClassName MockTransactionManager.java
 * @Description
 * @createTime 2022年07月28日 20:18:00
 */
public class MockTransactionManager implements TransactionManager {

    @Override
    public long begin() {
        return 0;
    }

    @Override
    public void commit(long xid) {}

    @Override
    public void abort(long xid) {}

    @Override
    public boolean isActive(long xid) {
        return false;
    }

    @Override
    public boolean isCommitted(long xid) {
        return false;
    }

    @Override
    public boolean isAborted(long xid) {
        return false;
    }

    @Override
    public void close() {}
}
