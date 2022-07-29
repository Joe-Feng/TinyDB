package backend.data.dataItem;

import backend.common.SubArray;
import backend.data.DataManagerImpl;
import backend.data.page.Page;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Joe
 * @ClassName DataItemImpl.java
 * @Description
 * @createTime 2022年07月29日 16:29:00
 */
public class DataItemImpl implements DataItem{
    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;

    private SubArray raw;
    private byte[] oldRaw;
    private Lock rLock;
    private Lock wLock;
    /**
     * 保存一个 dm 的引用是因为其释放依赖 dm 的释放（dm 同时实现了缓存接口，用于缓存 DataItem），以及修改数据时落日志。
     */
    private DataManagerImpl dm;
    private long uid;
    private Page pg;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.dm = dm;
        this.uid = uid;
        this.pg = pg;
    }

    public boolean isValid() {
        return raw.raw[raw.start+OF_VALID] == (byte)0;
    }

    /**
     * 上层模块获取到 DataItem 后通过该方法获取数据
     * @return 该方法返回的数组是数据共享的，而不是拷贝实现的，所以使用 SubArray
     */
    @Override
    public SubArray data() {
        return new SubArray(raw.raw, raw.start+OF_DATA, raw.end);
    }


    //整个流程，主要是为了保存前相数据，并及时落日志。DM 会保证对 DataItem 的修改是原子性的。
    /**
     * 在上层模块试图对 DataItem 进行修改时，需要遵循一定的流程：在修改之前需要调用 before() 方法
     */
    @SuppressWarnings("AlibabaLockShouldWithTryFinally")
    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length);
    }

    /**
     * 想要撤销修改时，调用 unBefore() 方法
     */
    @Override
    public void unBefore() {
        System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
        wLock.unlock();
    }

    /**
     * 在修改完成后，调用 after() 方法
     * @param xid
     */
    @Override
    public void after(long xid) {
        //生成更新日志
        dm.logDataItem(xid, this);
        wLock.unlock();
    }

    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unlock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void rUnLock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return pg;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    @Override
    public SubArray getRaw() {
        return raw;
    }
}
