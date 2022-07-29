package backend.data.pageIndex;

import backend.data.pageCache.PageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Joe
 * @ClassName PageIndex.java
 * @Description
 * @createTime 2022年07月29日 16:38:00
 */
/**
 * 页面索引
 * 缓存了每一页的空闲空间。用于在上层模块进行插入操作时，能够快速找到一个合适空间的页面，而无需从磁盘或者缓存中检查每一个页面的信息。
 *
 * MYDB 用一个比较粗略的算法实现了页面索引，将一页的空间划分成了 40 个区间。
 * 在启动时，就会遍历所有的页面信息，获取页面的空闲空间，安排到这 40 个区间中。
 * insert 在请求一个页时，会首先将所需的空间向上取整，映射到某一个区间，随后取出这个区间的任何一页，都可以满足需求。
 */
public class PageIndex {
    // 将一页划成40个区间
    private static final int INTERVALS_NO = 40;
    // 每个区间0.2kb
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private Lock lock;
    private List<PageInfo>[] lists;

    @SuppressWarnings("unchecked")
    public PageIndex() {
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO+1];
        for (int i = 0; i < INTERVALS_NO+1; i ++) {
            lists[i] = new ArrayList<>();
        }
    }

    /**
     * 将页面信息插入对应区间list
     * @param pgno
     * @param freeSpace
     */
    public void add(int pgno, int freeSpace) {
        lock.lock();
        try {
            int number = freeSpace / THRESHOLD;
            lists[number].add(new PageInfo(pgno, freeSpace));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从 PageIndex 中获取页面：计算区间，寻找符合该空间的页面
     * @param spaceSize
     * @return
     */
    public PageInfo select(int spaceSize) {
        lock.lock();
        try {
            int number = spaceSize / THRESHOLD;
            if(number < INTERVALS_NO) {
                // 向上取整
                number ++;
            }
            while(number <= INTERVALS_NO) {
                if(lists[number].size() == 0) {
                    number ++;
                    continue;
                }
                // 找到了可以放下该数据的页面
                //被选择的页，会直接从 PageIndex 中移除，这意味着，同一个页面是不允许并发写的。
                //在上层模块使用完这个页面后，需要将其重新插入 PageIndex：
                return lists[number].remove(0);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }
}
