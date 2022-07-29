package backend.data;


import backend.common.AbstractCache;
import backend.data.dataItem.DataItem;
import backend.data.dataItem.DataItemImpl;
import backend.data.logger.Logger;
import backend.data.page.Page;
import backend.data.page.PageOne;
import backend.data.page.PageX;
import backend.data.pageCache.PageCache;
import backend.data.pageIndex.PageIndex;
import backend.data.pageIndex.PageInfo;
import backend.transaction.TransactionManager;
import backend.utils.Panic;
import backend.utils.Types;
import common.Error;

/**
 * DataManager 是 DM 层直接对外提供方法的类
 * 同时，也实现成 DataItem 对象的缓存
 * DataItem 存储的 key，是由页号和页内偏移组成的一个 8 字节无符号整数，页号和偏移各占 4 字节。
 */
public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    /**
     * 向上层提供的功能：读
     * 根据 UID 从缓存中获取 DataItem，并校验有效位
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl)super.get(uid);
        if(!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

    /**
     * 向上层提供的功能：插入
     *
     * 在 pageIndex 中获取一个足以存储插入内容的页面的页号，
     * 获取页面后，首先需要写入插入日志，接着才可以通过 pageX 插入数据，并返回插入位置的偏移。
     * 最后需要将页面信息重新插入 pageIndex。
     * @param xid
     * @param data
     * @return
     * @throws Exception
     */
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if(raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        // 寻找空位
        PageInfo pi = null;
        for(int i = 0; i < 5; i ++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null) {
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            // 插入日志
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);

            // 插入数据
            short offset = PageX.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);

        } finally {
            // 将取出的pg重新插入pIndex
            if(pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        // 关闭 pageOne 需要设置校验
        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    // 为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    /**
     * DataItem 缓存
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int)(uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }


    /**
     * DataItem 缓存释放
     * 需要将 DataItem 写回数据源，由于对文件的读写是以页为单位进行的，
     * 只需要将 DataItem 所在的页 release 即可
     * @param di
     */
    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    /**
     * 在创建文件时初始化PageOne
     */
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    /**
     * 在打开已有文件时时读入PageOne，并验证正确性
     * @return
     */
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }


    /**
     * 初始化 pageIndex
     */
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for(int i = 2; i <= pageNumber; i ++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }

}
