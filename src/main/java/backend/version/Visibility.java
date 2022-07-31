package backend.version;


import backend.transaction.TransactionManager;

public class Visibility {

    /**
     * 检查版本跳跃
     * @param tm
     * @param t
     * @param e
     * @return
     */
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();
        // 读提交是允许版本跳跃的
        if(t.level == 0) {
            return false;
        } else {
            // 可重复读则是不允许版本跳跃
            // 解决版本跳跃的思路也很简单：如果 Ti 需要修改 X，而 X 已经被 Ti 不可见的事务 Tj 修改了，那么要求 Ti 回滚。
            // Ti 不可见的 Tj，有两种情况: XID(Tj) > XID(Ti) || Tj in SP(Ti)
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }

    /**
     * 判断事务 t 在事务级别下是否可见
     * @param tm
     * @param t
     * @param e
     * @return
     */
    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if(t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    /**
     * 判断某个记录对事务 t 是否可见
     * 如果可见，那么获取 t 适合的版本，只需要从最新版本开始，依次向前检查可见性，如果为 true，就可以直接返回。
     * @param tm
     * @param t
     * @param e
     * @return
     */
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        // 由 t 创建且还未被删除
        if(xmin == xid && xmax == 0) {
            return true;
        }

        // 由一个已提交的事务创建且尚未删除或由一个未提交的事务删除
        if(tm.isCommitted(xmin)) {
            // 尚未删除
            if(xmax == 0) {
                return true;
            }
            // 由一个未提交的事务删除
            if(xmax != xid) {
                if(!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 可重复读：解决一个事务在执行期间对同一个数据项的读取得到不同结果
     * 规则：事务只能读取它开始时, 就已经结束的那些事务产生的数据版本
     * 需要忽略的事务：
     * 1. 在本事务后开始的事务的数据：只需要比较事务 ID，即可确定
     * 2. 本事务开始时还是 active 状态的事务的数据：需要在事务 Ti 开始时，记录下当前活跃的所有事务 SP(Ti)，
     * 如果记录的某个版本，XMIN 在 SP(Ti) 中，也应当对 Ti 不可见
     * @param tm
     * @param t
     * @param e
     * @return
     */
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        // 由 t 创建且尚未被删除
        if(xmin == xid && xmax == 0) {
            return true;
        }

        // 由一个已提交的事务创建且 这个事务小于t 且 这个事务在 t 开始前提交
        if(tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            // 尚未被删除
            if(xmax == 0) {
                return true;
            }
            // 由其他事务删除
            if(xmax != xid) {
                // 这个事务尚未提交 或 这个事务在t开始之后才开始 或 这个事务在t开始前还未提交
                if(!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

}
