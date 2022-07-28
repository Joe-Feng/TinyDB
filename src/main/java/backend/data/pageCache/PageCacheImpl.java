package backend.data.pageCache;

import backend.common.AbstractCache;
import backend.data.page.Page;
import backend.data.page.PageImpl;
import backend.utils.Panic;
import common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageCacheImpl extends AbstractCache<Page> implements PageCache {
    /**
     * 最小内存限制
     */
    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;
    /**
     * 记录了前打开的数据库文件有多少页,这个数字在数据库文件被打开时就会被计算，并在新建页面时自增
     */
    private AtomicInteger pageNumbers;

    PageCacheImpl(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        super(maxResource);
        if(maxResource < MEM_MIN_LIM) {
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fileChannel;
        this.fileLock = new ReentrantLock();
        this.pageNumbers = new AtomicInteger((int)length / PAGE_SIZE);
    }

    /**
     * 新建一页：pageNumbers自增
     * @param initData
     * @return 返回新建页的页号
     */
    @Override
    public int newPage(byte[] initData) {
        int pgno = pageNumbers.incrementAndGet();
        Page pg = new PageImpl(pgno, initData, null);
        // 新建的页面需要立刻写回
        flush(pg);
        return pgno;
    }

    /**
     * 通过引用计数缓存获取页面
     * @param pgno
     * @return
     * @throws Exception
     */
    @Override
    public Page getPage(int pgno) throws Exception {
        return get((long)pgno);
    }

    /**
     * 根据pageNumber从数据库文件中读取页数据，并包裹成Page
     */
    @SuppressWarnings("AlibabaLockShouldWithTryFinally")
    @Override
    protected Page getForCache(long key) throws Exception {
        int pgno = (int)key;
        long offset = pageOffset(pgno);

        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch(IOException e) {
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImpl(pgno, buf.array(), this);
    }

    /**
     * 驱逐页面：根据页面是否是脏页面，来决定是否需要写回文件系统
     * @param pg
     */
    @Override
    protected void releaseForCache(Page pg) {
        if(pg.isDirty()) {
            flush(pg);
            pg.setDirty(false);
        }
    }

    /**
     * 释放一个页面
     * @param page
     */
    @Override
    public void release(Page page) {
        release((long)page.getPageNumber());
    }

    /**
     * 将页面写回文件系统
     * @param pg
     */
    @Override
    public void flushPage(Page pg) {
        flush(pg);
    }

    /**
     * 将页面写回文件系统
     * @param pg 需要写回的页面
     */
    private void flush(Page pg) {
        int pgno = pg.getPageNumber();
        long offset = pageOffset(pgno);

        fileLock.lock();
        try {
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset);
            fc.write(buf);
            fc.force(false);
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * 同一条数据是不允许跨页存储的，这一点会从后面的章节中体现。这意味着，单条数据的大小不能超过数据库页面的大小。
     * 如果数据超过了页面最大长度，截短文件为页面最大长度
     * @param maxPgno
     */
    @Override
    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try {
            file.setLength(size);
        } catch (IOException e) {
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    /**
     * 关闭所有结构
     */
    @Override
    public void close() {
        super.close();
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    /**
     * 获取当前数据库中页面总数
     * @return
     */
    @Override
    public int getPageNumber() {
        return pageNumbers.intValue();
    }

    /**
     * 获取页面的起始地址
     * @param pgno 页号
     * @return 页面起始地址,单位：byte
     */
    private static long pageOffset(int pgno) {
        return (pgno-1) * PAGE_SIZE;
    }

}
