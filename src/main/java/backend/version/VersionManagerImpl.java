package backend.version;

import backend.common.AbstractCache;
import backend.data.DataManager;
import backend.transaction.TransactionManager;
import backend.transaction.TransactionManagerImpl;
import backend.utils.Panic;
import common.Error;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {

    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransaction;
    Lock lock;
    LockTable lt;

    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransaction = new HashMap<>();
        activeTransaction.put(TransactionManagerImpl.SUPER_XID, Transaction.newTransaction(TransactionManagerImpl.SUPER_XID, 0, null));
        this.lock = new ReentrantLock();
        this.lt = new LockTable();
    }

    /**
     * 读取一个 entry，注意判断下可见性即可
     * @param xid
     * @param uid
     * @return
     * @throws Exception
     */
    @SuppressWarnings("AlibabaLockShouldWithTryFinally")
    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }

        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch(Exception e) {
            if(e == Error.NullEntryException) {
                return null;
            } else {
                throw e;
            }
        }
        try {
            if(Visibility.isVisible(tm, t, entry)) {
                return entry.data();
            } else {
                return null;
            }
        } finally {
            entry.release();
        }
    }

    /**
     * 将数据包裹成 Entry，无脑交给 DM 插入即可
     * @param xid
     * @param data
     * @return
     * @throws Exception
     */
    @SuppressWarnings("AlibabaLockShouldWithTryFinally")
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }

        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid, raw);
    }

    /**
     * 删除操作：实际上主要是前置的三件事：一是可见性判断，二是获取资源的锁，三是版本跳跃判断。
     * 删除的操作只有一个设置 XMAX。
     * @param xid
     * @param uid
     * @return
     * @throws Exception
     */
    @SuppressWarnings("AlibabaLockShouldWithTryFinally")
    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch(Exception e) {
            if(e == Error.NullEntryException) {
                return false;
            } else {
                throw e;
            }
        }
        try {
            // 1. 可见性判断
            if(!Visibility.isVisible(tm, t, entry)) {
                return false;
            }
            Lock l = null;
            try {
                //2. 获取资源的锁
                l = lt.add(xid, uid);
            } catch(Exception e) {
                // 检测出死锁，自动终止事务
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }
            if(l != null) {
                // TODO 这里如何理解
                l.lock();
                l.unlock();
            }

            if(entry.getXmax() == xid) {
                return false;
            }

            // 3. 版本跳跃判断
            if(Visibility.isVersionSkip(tm, t, entry)) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }

            entry.setXmax(xid);
            return true;

        } finally {
            entry.release();
        }
    }

    /**
     * 开启一个事务，并初始化事务的结构，将其存放在 activeTransaction 中，用于检查和快照使用
     * @param level 事务等级
     * @return
     */
    @Override
    public long begin(int level) {
        lock.lock();
        try {
            long xid = tm.begin();
            Transaction t = Transaction.newTransaction(xid, level, activeTransaction);
            activeTransaction.put(xid, t);
            return xid;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 提交一个事务，主要就是 free 掉相关的结构，并且释放持有的锁，并修改 TM 状态
     * @param xid
     * @throws Exception
     */
    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        try {
            if(t.err != null) {
                throw t.err;
            }
        } catch(NullPointerException n) {
            System.out.println(xid);
            System.out.println(activeTransaction.keySet());
            Panic.panic(n);
        }

        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();

        lt.remove(xid);
        tm.commit(xid);
    }

    /**
     * 终止操作
     * @param xid
     */
    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    /**
     * 有两种方式：手动、自动
     * 手动指的是调用 abort() 方法
     * 自动，则是在事务被检测出出现死锁时，会自动撤销回滚事务；或者出现版本跳跃时，也会自动回滚
     * @param xid
     * @param autoAborted 是否自动终止
     */
    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        // 手动终止，将事务移出活跃队列；自动提交是事务回滚，不能将事务移出活跃事务队列
        if(!autoAborted) {
            activeTransaction.remove(xid);
        }
        lock.unlock();

        // 事务是否已经终止了
        if(t.autoAborted) {
            return;
        }
        lt.remove(xid);
        tm.abort(xid);
    }

    public void releaseEntry(Entry entry) {
        super.release(entry.getUid());
    }

    /**
     * VM 的实现类还被设计为 Entry 的缓存
     * 实现缓存框架中的获取缓存抽象方法
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if(entry == null) {
            throw Error.NullEntryException;
        }
        return entry;
    }

    /**
     * 实现缓存框架中的释放缓存抽象方法
     * @param entry
     */
    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }

}
