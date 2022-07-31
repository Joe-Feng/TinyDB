package backend.version;

import backend.transaction.TransactionManagerImpl;

import java.util.HashMap;
import java.util.Map;

// vm对一个事务的抽象
public class Transaction {
    public long xid;
    /**
     * 事务级别：0：Read Committed   其他：repeatable read
     */
    public int level;
    public Map<Long, Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;

    /**
     * 构造方法
     * @param xid
     * @param level
     * @param active 保存着当前所有 active 的事务
     * @return
     */
    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        if(level != 0) {
            t.snapshot = new HashMap<>();
            for(Long x : active.keySet()) {
                t.snapshot.put(x, true);
            }
        }
        return t;
    }

    /**
     * 判断xid是否在当前活跃的事务中
     * @param xid
     * @return
     */
    public boolean isInSnapshot(long xid) {
        if(xid == TransactionManagerImpl.SUPER_XID) {
            return false;
        }
        return snapshot.containsKey(xid);
    }
}
