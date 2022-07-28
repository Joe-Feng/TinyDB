package backend.common;

import common.Error;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Joe
 * @ClassName AbstractCache.java
 * @Description 缓存框架
 * @createTime 2022年07月28日 21:37:00
 */
public abstract class AbstractCache<T> {

    /**
     * 实际缓存的数据
     */
    private HashMap<Long, T> cache;
    /**
     * 元素的引用个数
     */
    private HashMap<Long, Integer> references;
    /**
     * 为了应对多线程场景，还需要记录哪些资源正在从数据源获取中（从数据源获取资源是一个相对费时的操作）
     */
    private HashMap<Long, Boolean> getting;
    /**
     * 缓存的最大缓存资源数
     */
    private int maxResource;
    /**
     * 缓存中元素的个数
     */
    private int count = 0;
    /**
     * 对数据源操作都要加锁
     */
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();
        lock = new ReentrantLock();
    }

    /**
     * 通过 get() 方法获取资源
     * @param key 资源编号
     * @return
     * @throws Exception
     */
    @SuppressWarnings("AlibabaLockShouldWithTryFinally")
    protected T get(long key) throws Exception {
        // 1. 尝试从缓存里获取
        while(true) {
            lock.lock();

            // 请求的资源正在被其他线程获取：隔一段时间查看一次，看看是否还被占用
            if(getting.containsKey(key)) {
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }

            // 资源在缓存中，直接返回
            if(cache.containsKey(key)) {
                T obj = cache.get(key);
                // 被引用数+1
                references.put(key, references.get(key) + 1);
                lock.unlock();
                return obj;
            }

            // 尝试获取该资源
            if(maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }
            //如果缓存没满的话，就在 getting 中注册一下，该线程准备从数据源获取资源了
            count ++;
            getting.put(key, true);
            lock.unlock();
            break;
        }

        // 2. 从数据源获取资源了
        T obj = null;
        try {
            obj = getForCache(key);
        } catch(Exception e) {
            lock.lock();
            count --;
            getting.remove(key);
            lock.unlock();
            throw e;
        }
        // 成功从数据源获取数据
        lock.lock();
        getting.remove(key);
        cache.put(key, obj);
        references.put(key, 1);
        lock.unlock();

        return obj;
    }

    /**
     * 释放一个引用
     */
    protected void release(long key) {
        lock.lock();
        try {
            int ref = references.get(key)-1;
            // 当没有引用指向该缓存，删除掉缓存相关的结构
            if(ref == 0) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
                count --;
            } else {
                references.put(key, ref);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 安全关闭：关闭缓存，写回所有资源
     */
    protected void close() {
        lock.lock();
        try {
            Set<Long> keys = cache.keySet();
            for (long key : keys) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * 当资源不在缓存时的获取行为
     */
    protected abstract T getForCache(long key) throws Exception;
    /**
     * 当资源被驱逐时的写回行为
     */
    protected abstract void releaseForCache(T obj);
}
