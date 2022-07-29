package backend.data;


import backend.data.dataItem.DataItem;
import backend.data.logger.Logger;
import backend.data.page.PageOne;
import backend.data.pageCache.PageCache;
import backend.transaction.TransactionManager;

public interface DataManager {
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close();

    /**
     * 从空文件创建 DataManager
     * @param path
     * @param mem
     * @param tm
     * @return
     */
    public static DataManager create(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.create(path, mem);
        Logger lg = Logger.create(path);

        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        // 对第一页进行初始化
        dm.initPageOne();
        return dm;
    }

    /**
     * 从已有文件创建 DataManager
     * @param path
     * @param mem
     * @param tm
     * @return
     */
    public static DataManager open(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.open(path, mem);
        Logger lg = Logger.open(path);
        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        // 对第一页进行校验,来判断是否需要执行恢复流程,
        if(!dm.loadCheckPageOne()) {
            Recover.recover(tm, lg, pc);
        }
        dm.fillPageIndex();
        // 重新对第一页生成随机字节
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);

        return dm;
    }
}
